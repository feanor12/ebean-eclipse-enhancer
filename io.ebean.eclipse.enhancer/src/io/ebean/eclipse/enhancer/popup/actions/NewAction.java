package io.ebean.eclipse.enhancer.popup.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

public class NewAction implements IObjectActionDelegate
{

    private Shell shell;

    /**
     * Constructor for Action1.
     */
    public NewAction()
    {
        super();
    }

    /**
     * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
     */
    @Override
    public void setActivePart(final IAction action, final IWorkbenchPart targetPart)
    {
        shell = targetPart.getSite().getShell();
    }

    /**
     * @see IActionDelegate#run(IAction)
     */
    @Override
    public void run(final IAction action)
    {
        MessageDialog.openInformation(shell, "Ebean Enhancer", "New Action was executed.");
    }

    /**
     * @see IActionDelegate#selectionChanged(IAction, ISelection)
     */
    @Override
    public void selectionChanged(final IAction action, final ISelection selection)
    {
        // nothing to do here
    }

}
