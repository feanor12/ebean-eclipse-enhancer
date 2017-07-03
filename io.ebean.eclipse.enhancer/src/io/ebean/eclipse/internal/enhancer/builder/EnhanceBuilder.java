package io.ebean.eclipse.internal.enhancer.builder;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;

import io.ebean.eclipse.internal.enhancer.EnhancerPlugin;
import io.ebean.enhance.agent.MessageOutput;
import io.ebean.enhance.agent.Transformer;
import io.ebean.enhance.agent.UrlPathHelper;
import io.ebean.enhance.asm.ClassReader;
import io.ebean.enhance.asm.ClassVisitor;
import io.ebean.enhance.asm.Opcodes;
import io.ebean.typequery.agent.CombinedTransform;
import io.ebean.typequery.agent.CombinedTransform.Response;
import io.ebean.typequery.agent.QueryBeanTransformer;

public final class EnhanceBuilder extends IncrementalProjectBuilder
{

    @Override
    protected IProject[] build(final int kind, final Map<String, String> args, final IProgressMonitor monitor) throws CoreException
    {

        final IProject project = getProject();
        if (kind == FULL_BUILD) {
            fullBuild(monitor);
        } else {
            final IResourceDelta delta = getDelta(project);
            if (delta == null) {
                fullBuild(monitor);
            } else {
                delta.accept(new DeltaVisitor(monitor));
            }
        }

        return new IProject[0];
    }

    /**
     * Find the corresponding source for this class in the project.
     *
     * @throws CoreException
     */
    private IFile findSourcePath(final IProject project, final String className) throws CoreException
    {
        if (project.hasNature(JavaCore.NATURE_ID)) {
            final IJavaProject javaProject = JavaCore.create(project);
            try {
                final IType type = javaProject.findType(className);
                if (type != null) {
                    final IFile sourceFile = project.getWorkspace().getRoot().getFile(type.getPath());
                    if (sourceFile.exists()) {
                        return sourceFile;
                    }
                }
            }
            catch (final JavaModelException e) {
                EnhancerPlugin.logError("Error in findSourcePath", e);
            }
        }
        return null;

    }

    private void checkResource(final IResource resource, final IProgressMonitor monitor)
    {
        if (!((resource instanceof IFile) && resource.getName().endsWith(".class"))) {
            return;
        }

        final IFile file = (IFile) resource;
        final int pluginDebug = EnhancerPlugin.getDebugLevel();

        // try to place error markers on sourceFile, if it does not exist, place marker on project
        final IProject project = resource.getProject();
        IFile sourceFile = null;

        try (InputStream is = file.getContents(); PrintStream transformLog = EnhancerPlugin.createTransformLog()) {

            final byte[] classBytes;
            try {
                classBytes = readBytes(is);
            }
            catch (final IOException ioe) {
                EnhancerPlugin.logError("Error during enhancement", ioe);
                return;
            }

            final String className = DetermineClass.getClassName(classBytes);
            sourceFile = findSourcePath(project, className);

            final URL[] paths = getClasspath();

            if (pluginDebug >= 2) {
                EnhancerPlugin.logInfo("... processing class: " + className);
                EnhancerPlugin.logInfo("... classpath: " + Arrays.toString(paths));
            }

            final int enhanceDebugLevel = EnhancerPlugin.getEnhanceDebugLevel();

            try (final URLClassLoader cl = new URLClassLoader(paths)) {
                final QueryBeanTransformer queryBeanTransformer = new QueryBeanTransformer("debug=" + enhanceDebugLevel, cl, null);
                final Transformer entityBeanTransformer = new Transformer(paths, "debug=" + enhanceDebugLevel);
                entityBeanTransformer.setLogout(new MessageOutput()
                {
                    @Override
                    public void println(final String msg)
                    {
                        EnhancerPlugin.logInfo(msg);
                    }
                });

                final CombinedTransform combined = new CombinedTransform(entityBeanTransformer, queryBeanTransformer);
                final Response response = combined.transform(cl, className, null, null, classBytes);
                if (response.isEnhanced()) {
                    final byte[] outBytes = response.getClassBytes();
                    final ByteArrayInputStream bais = new ByteArrayInputStream(outBytes);
                    file.setContents(bais, true, false, monitor);
                    if (pluginDebug >= 1) {
                        EnhancerPlugin.logInfo("enhanced: " + className);
                    }
                }
                // create Markers for all errors in SourceFile
                for (final List<Throwable> list : entityBeanTransformer.getUnexpectedExceptions().values()) {
                    for (final Throwable t : list) {
                        createErrorMarker(sourceFile == null ? project : sourceFile, t);
                    }
                }
            }

        }
        catch (final Exception e) {
            EnhancerPlugin.logError("Error during enhancement", e);
            createErrorMarker(sourceFile == null ? project : sourceFile, e);
        }
    }

    private void createErrorMarker(final IResource target, final Throwable t)
    {
        try {
            final IMarker marker = target.createMarker(IMarker.PROBLEM);
            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
            marker.setAttribute(IMarker.MESSAGE, "Error during enhancement: " + t.getMessage());
            marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
            marker.setAttribute(IMarker.LINE_NUMBER, 1);
        }
        catch (final CoreException e) {
            EnhancerPlugin.logError("Error during creating marker", e);
        }
    }

    private void fullBuild(final IProgressMonitor monitor)
    {
        try {
            getProject().accept(new ResourceVisitor(monitor));
        }
        catch (final CoreException e) {
            EnhancerPlugin.logError("Error with fullBuild", e);
        }
    }

    private URL[] getClasspath() throws CoreException
    {

        final IProject project = getProject();
        final IJavaProject javaProject = JavaCore.create(project);

        final String[] ideClassPath = JavaRuntime.computeDefaultRuntimeClassPath(javaProject);

        return UrlPathHelper.convertToUrl(ideClassPath);
    }

    private byte[] readBytes(final InputStream in) throws IOException
    {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final BufferedInputStream bi = new BufferedInputStream(in);

        int len = -1;
        final byte[] buf = new byte[1024];

        while ((len = bi.read(buf)) > -1) {
            baos.write(buf, 0, len);
        }

        return baos.toByteArray();
    }

    private static class DetermineClass
    {
        private DetermineClass()
        {

        }

        static String getClassName(final byte[] classBytes)
        {
            final ClassReader cr = new ClassReader(classBytes);
            final DetermineClassVisitor cv = new DetermineClassVisitor();
            try {
                cr.accept(cv, ClassReader.SKIP_CODE + ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES);

                // should not get to here...
                throw new RuntimeException("Expected DetermineClassVisitor to throw GotClassName?");

            }
            catch (final GotClassName e) {
                EnhancerPlugin.logError("Error in getClassName", e);
                // used to skip reading the rest of the class bytes...
                return e.getClassName();
            }
        }

        private static class DetermineClassVisitor extends ClassVisitor
        {
            public DetermineClassVisitor()
            {
                super(Opcodes.ASM5);
            }

            @Override
            public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces)
            {
                throw new GotClassName(name);
            }
        }

        private static class GotClassName extends RuntimeException
        {
            private static final long serialVersionUID = 2869058158272107957L;

            final String className;

            public GotClassName(final String className)
            {
                super(className);
                this.className = className.replace('/', '.');
            }

            public String getClassName()
            {
                return className;
            }
        }

    }

    private class DeltaVisitor implements IResourceDeltaVisitor
    {
        private final IProgressMonitor monitor;

        private DeltaVisitor(final IProgressMonitor monitor)
        {
            this.monitor = monitor;
        }

        @Override
        public boolean visit(final IResourceDelta delta) throws CoreException
        {
            final IResource resource = delta.getResource();
            switch (delta.getKind())
            {
                case IResourceDelta.ADDED:
                case IResourceDelta.CHANGED:
                {
                    checkResource(resource, monitor);
                    break;
                }
                case IResourceDelta.REMOVED:
                default:
                {
                    break;
                }
            }

            // return true to continue visiting children.
            return true;
        }
    }

    private class ResourceVisitor implements IResourceVisitor
    {
        private final IProgressMonitor monitor;

        private ResourceVisitor(final IProgressMonitor monitor)
        {
            this.monitor = monitor;
        }

        @Override
        public boolean visit(final IResource resource) throws CoreException
        {
            checkResource(resource, monitor);
            return true;
        }
    }
}
