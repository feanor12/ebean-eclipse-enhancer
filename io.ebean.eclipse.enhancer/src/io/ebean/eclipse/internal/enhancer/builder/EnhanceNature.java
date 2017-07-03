package io.ebean.eclipse.internal.enhancer.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import io.ebean.eclipse.internal.enhancer.EnhancerConstants;
import io.ebean.eclipse.internal.enhancer.EnhancerPlugin;

public final class EnhanceNature implements IProjectNature
{
    private IProject project;

    public EnhanceNature()
    {
        // no-op for eclipse
    }

    public EnhanceNature(final IProject project)
    {
        this.project = project;
    }

    public static void toggleNature(final IProject project, final boolean enable) throws CoreException
    {
        final IProjectDescription desc = project.getDescription();

        // this will cause an instance of this class to run and toggle the
        // builders
        desc.setNatureIds(toggleNature(desc, true));
        project.setDescription(desc, null);
    }

    @Override
    public void configure() throws CoreException
    {
        toggleBuilder(true, "... Enhancement enabled!!!");
    }

    @Override
    public void deconfigure() throws CoreException
    {
        toggleBuilder(false, "... Enhancement disabled!!!");
    }

    @Override
    public IProject getProject()
    {
        return project;
    }

    @Override
    public void setProject(final IProject project)
    {
        this.project = project;
    }

    private static <T> List<T> toggle(final T[] array, final String id, final boolean enable, final ICallback<T> callback)
    {
        // not going to be any bigger...
        final List<T> list = new ArrayList<>(array.length + 1);
        list.addAll(Arrays.asList(array));

        if (enable && !list.contains(id)) {
            list.add(callback.create());
        } else {
            list.remove(id);
        }

        return list;
    }

    private static String[] toggleNature(final IProjectDescription desc, final boolean enable)
    {
        final List<String> list = toggle(desc.getNatureIds(), EnhancerConstants.NATURE_ID, enable, new ICallback<String>()
        {
            @Override
            public String create()
            {
                return EnhancerConstants.NATURE_ID;
            }
        });

        final String[] array = new String[list.size()];

        return list.toArray(array);
    }

    private void toggleBuilder(final boolean enable, final String message) throws CoreException
    {
        final IProjectDescription desc = project.getDescription();

        desc.setBuildSpec(toggleBuilder(desc, enable));
        project.setDescription(desc, null);

        // TOOD: add warning on full build and/or allow specifing classpath /
        // better monitor
        project.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());

        if (EnhancerPlugin.getDebugLevel() >= 1) {
            EnhancerPlugin.logInfo(message);
        }
    }

    private ICommand[] toggleBuilder(final IProjectDescription desc, final boolean enable)
    {
        final List<ICommand> list = toggle(desc.getBuildSpec(), EnhancerConstants.BUILDER_ID, enable, new ICallback<ICommand>()
        {
            @Override
            public ICommand create()
            {
                final ICommand command = desc.newCommand();
                command.setBuilderName(EnhancerConstants.BUILDER_ID);

                return command;
            }
        });

        final ICommand[] array = new ICommand[list.size()];

        return list.toArray(array);
    }

    private interface ICallback<T>
    {
        T create();
    }
}
