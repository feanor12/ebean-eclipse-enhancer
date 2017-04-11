package io.ebean.eclipse.internal.enhancer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import io.ebean.eclipse.internal.enhancer.ui.preferences.PreferenceConstants;

/**
 * The activator controlling the plug-in life cycle
 */
public class EnhancerPlugin extends AbstractUIPlugin
{
    // The plug-in ID
    public static final String PLUGIN_ID = "io.ebean.eclipse.enhancer";

    // The shared instance
    private static EnhancerPlugin plugin;

    public static PrintStream createTransformLog()
    {
        final IPath path = plugin.getStateLocation().addTrailingSeparator().append("enhance.log");

        try {
            return (getEnhanceDebugLevel() > 0) ? createTransformLogStream(path) : createNullTransformLog();
        }
        catch (final IOException e) {
            logError("Error creating log file [" + path.toString() + "]", e);
            return System.out;
        }
    }

    private static PrintStream createTransformLogStream(final IPath path) throws IOException
    {

        final File logFile = path.toFile();
        if (!logFile.exists()) {
            logFile.createNewFile();
        }

        return new PrintStream(new FileOutputStream(logFile, true));

    }

    private static PrintStream createNullTransformLog()
    {
        final OutputStream outputStream = new OutputStream()
        {
            @Override
            public void write(final int b)
            {
                // null writer
            }
        };

        return new PrintStream(outputStream);
    }

    public static int getDebugLevel()
    {
        if (plugin == null) {
            return 0;
        }

        final IPreferenceStore store = plugin.getPreferenceStore();
        return store.getInt(PreferenceConstants.P_PLUGIN_DEBUG_LEVEL);
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static EnhancerPlugin getDefault()
    {
        return plugin;
    }

    public static int getEnhanceDebugLevel()
    {
        if (plugin == null) {
            return 0;
        }

        final IPreferenceStore store = plugin.getPreferenceStore();
        return store.getInt(PreferenceConstants.P_ENHANCE_DEBUG_LEVEL);
    }

    /**
     * Returns an image descriptor for the image file at the given plug-in
     * relative path
     *
     * @param path
     *            the path
     * @return the image descriptor
     */
    public static ImageDescriptor getImageDescriptor(final String path)
    {
        return imageDescriptorFromPlugin(PLUGIN_ID, path);
    }

    public static void logError(final String msg, final Exception e)
    {
        final ILog log = plugin.getLog();
        log.log(new Status(IStatus.ERROR, PLUGIN_ID, IStatus.OK, msg, e));
    }

    public static void logInfo(final String msg)
    {
        logInfo(msg, null);
    }

    public static void logInfo(final String msg, final Exception e)
    {
        final ILog log = plugin.getLog();
        log.log(new Status(IStatus.INFO, PLUGIN_ID, IStatus.OK, msg, e));
    }

    @Override
    public void start(final BundleContext context) throws Exception
    {
        super.start(context);
        plugin = this;

    }

    @Override
    public void stop(final BundleContext context) throws Exception
    {
        plugin = null;
        super.stop(context);
    }
}
