/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the COPYING file, which can be found  *
 * at the root of the source code distribution tree,                         *
 * or in https://www.hdfgroup.org/licenses.                                  *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package hdf.view.dialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;

import hdf.object.FileFormat;
import hdf.object.Group;
import hdf.object.HObject;
import hdf.view.DefaultFileFilter;
import hdf.view.ThemeManager;
import hdf.view.Tools;
import hdf.view.ViewProperties;
import hdf.view.i18n.I18n;

import hdf.hdf5lib.H5;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * NewLinkDialog shows a message dialog requesting user input for creating
 * new links.
 *
 * @author Jordan T. Henderson
 * @version 2.4 1/1/2016
 */
public class NewLinkDialog extends Dialog {

    private static final Logger log = LoggerFactory.getLogger(NewLinkDialog.class);

    private Shell shell;
    private ThemeManager themeManager;
    private ThemeManager.Registration targetObjectThemeRegistration;

    private Font curFont;

    private Text nameField;

    private Combo parentChoice;

    private CCombo targetObject;

    private String currentDir;

    private Text targetFile;

    private Button targetFileButton;

    private Button hardLink;
    private Button softLink;
    private Button externalLink;

    /** a list of current groups. */
    private List<Group> groupList;

    /** a list of current objects. */
    private List<?> objList;

    private HObject newObject;
    private Group parentGroup;

    private FileFormat fileFormat;

    private final List<?> fileList;

    /**
     * Constructs a NewLinkDialog with specified list of possible parent groups.
     *
     * @param parent
     *            the parent shell of the dialog
     * @param pGroup
     *            the parent group which the new group is added to.
     * @param objs
     *            the list of all objects.
     * @param files
     *            the list of all files open in the TreeView
     */
    public NewLinkDialog(Shell parent, Group pGroup, List<?> objs, List<FileFormat> files)
    {
        super(parent, SWT.APPLICATION_MODAL);

        try {
            curFont = new Font(Display.getCurrent(), ViewProperties.getFontType(),
                               ViewProperties.getFontSize(), SWT.NORMAL);
        }
        catch (Exception ex) {
            curFont = null;
        }

        newObject   = null;
        parentGroup = pGroup;
        objList     = objs;

        fileFormat = pGroup.getFileFormat();
        currentDir = ViewProperties.getWorkDir();
        fileList   = files;
    }

    /**
     * Open the NewLinkDialog for adding a new link.
     */
    public void open()
    {
        Shell parent = getParent();
        shell        = new Shell(parent, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL);
        themeManager = ThemeManager.forDisplay(shell.getDisplay());
        themeManager.applyTo(shell);
        shell.setFont(curFont);
        I18n.bind(shell, "dialog.newLink.title");
        shell.setImages(ViewProperties.getHdfIcons());
        shell.setLayout(new GridLayout(1, true));

        // Create the main content region
        Composite content = new Composite(shell, SWT.NONE);
        content.setLayout(new GridLayout(2, false));
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label label = new Label(content, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.linkName");

        nameField = new Text(content, SWT.SINGLE | SWT.BORDER);
        nameField.setFont(curFont);
        GridData fieldData     = new GridData(SWT.FILL, SWT.FILL, true, false);
        fieldData.minimumWidth = 300;
        nameField.setLayoutData(fieldData);

        label = new Label(content, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.parentGroup");

        parentChoice = new Combo(content, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
        parentChoice.setFont(curFont);
        parentChoice.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        parentChoice.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                parentGroup = groupList.get(parentChoice.getSelectionIndex());
            }
        });

        Composite helpComposite = new Composite(content, SWT.NONE);
        helpComposite.setLayout(new GridLayout(1, true));
        helpComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));

        label = new Label(helpComposite, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.linkType");

        Button helpButton = new Button(helpComposite, SWT.PUSH);
        helpButton.setImage(ViewProperties.getHelpIcon());
        I18n.bindToolTip(helpButton, "dialog.help.links.title");
        helpButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
        helpButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                Tools.showInformation(shell, I18n.text("dialog.help.links.title"),
                                      I18n.text("dialog.help.links.text"));
            }
        });

        Composite typeComposite = new Composite(content, SWT.BORDER);
        typeComposite.setLayout(new GridLayout(3, true));
        typeComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        hardLink = new Button(typeComposite, SWT.RADIO);
        hardLink.setFont(curFont);
        I18n.bind(hardLink, "common.hardLink");
        hardLink.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        hardLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                targetFile.setEnabled(false);
                targetFileButton.setEnabled(false);
                targetObject.setEnabled(true);
                targetObject.setEditable(false);
                targetObject.setBackground(
                    themeManager.color(ThemeManager.ColorRole.SECONDARY_SURFACE));

                targetObject.removeAll();
                retrieveObjects(fileFormat);

                targetObject.select(0);
            }
        });

        softLink = new Button(typeComposite, SWT.RADIO);
        softLink.setFont(curFont);
        I18n.bind(softLink, "common.softLink");
        softLink.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        softLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                targetFile.setEnabled(false);
                targetFileButton.setEnabled(false);
                targetObject.setEnabled(true);
                targetObject.setEditable(true);
                targetObject.setBackground(null);

                targetObject.removeAll();
                retrieveObjects(fileFormat);

                targetObject.select(0);
            }
        });

        externalLink = new Button(typeComposite, SWT.RADIO);
        externalLink.setFont(curFont);
        I18n.bind(externalLink, "common.externalLink");
        externalLink.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        externalLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                targetFile.setEnabled(true);
                targetFileButton.setEnabled(true);
                targetObject.setEnabled(true);
                targetObject.setEditable(true);
                targetObject.setBackground(null);
                targetObject.removeAll();
            }
        });

        label = new Label(content, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.targetFile");

        Composite fileComposite  = new Composite(content, SWT.NONE);
        GridLayout layout        = new GridLayout(2, false);
        layout.horizontalSpacing = 0;
        layout.marginHeight      = 0;
        layout.marginWidth       = 0;
        fileComposite.setLayout(layout);
        fileComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        targetFile = new Text(fileComposite, SWT.SINGLE | SWT.BORDER);
        targetFile.setFont(curFont);
        targetFile.setEnabled(false);
        targetFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        targetFile.addTraverseListener(new TraverseListener() {
            @Override
            public void keyTraversed(TraverseEvent e)
            {
                if (e.detail == SWT.TRAVERSE_RETURN) {
                    String filename = targetFile.getText();

                    if (filename == null || filename.length() <= 0)
                        return;

                    File chosenFile = new File(filename);

                    if (!chosenFile.exists()) {
                        return;
                    }

                    if (chosenFile.isDirectory()) {
                        currentDir = chosenFile.getPath();
                    }
                    else {
                        currentDir = chosenFile.getParent();
                    }

                    // Check if the target File is not the current file.
                    String currentFileName = fileFormat.getAbsolutePath();
                    if (currentFileName.equals(chosenFile.getAbsolutePath())) {
                        Tools.showError(
                            shell, I18n.text("action.traverse"),
                            I18n.text("message.externalLinkOtherFile"));
                        targetFile.setText("");
                        return;
                    }

                    getTargetFileObjs();

                    if (targetObject.getItemCount() > 0)
                        targetObject.select(0);
                }
            }
        });

        targetFileButton = new Button(fileComposite, SWT.PUSH);
        targetFileButton.setFont(curFont);
        I18n.bind(targetFileButton, "button.browse");
        targetFileButton.setEnabled(false);
        targetFileButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        targetFileButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                String filename = null;
                filename        = openTargetFile();

                if (filename == null) {
                    return;
                }

                targetFile.setText(filename);
                getTargetFileObjs();

                if (targetObject.getItemCount() > 0)
                    targetObject.select(0);
            }
        });

        label = new Label(content, SWT.LEFT);
        label.setFont(curFont);
        I18n.bind(label, "label.targetObject");

        targetObject = new CCombo(content, SWT.DROP_DOWN | SWT.BORDER);
        targetObject.setFont(curFont);
        targetObject.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        targetObject.setEditable(false);
        targetObject.setBackground(themeManager.color(ThemeManager.ColorRole.SECONDARY_SURFACE));
        targetObjectThemeRegistration = themeManager.addListener(manager -> {
            if (targetObject != null && !targetObject.isDisposed() && !targetObject.getEditable())
                targetObject.setBackground(manager.color(ThemeManager.ColorRole.SECONDARY_SURFACE));
        });

        groupList            = new ArrayList<>(objList.size());
        Object obj           = null;
        Iterator<?> iterator = objList.iterator();
        String fullName      = null;
        int idxRoot          = -1;
        int idx              = -1;
        while (iterator.hasNext()) {
            obj = iterator.next();
            idx++;

            if (obj instanceof Group) {
                Group g = (Group)obj;
                groupList.add(g);
                if (g.isRoot()) {
                    fullName = HObject.SEPARATOR;
                    idxRoot  = idx;
                }
                else {
                    fullName = g.getPath() + g.getName() + HObject.SEPARATOR;
                }
                parentChoice.add(fullName);
            }
            else {
                fullName = ((HObject)obj).getPath() + ((HObject)obj).getName();
            }

            targetObject.add(fullName);
        }

        targetObject.remove(idxRoot);
        objList.remove(idxRoot);

        if (parentGroup.isRoot()) {
            parentChoice.select(parentChoice.indexOf(HObject.SEPARATOR));
        }
        else {
            parentChoice.select(
                parentChoice.indexOf(parentGroup.getPath() + parentGroup.getName() + HObject.SEPARATOR));
        }

        // Dummy label to take up space as dialog is resized
        label = new Label(content, SWT.LEFT);
        label.setFont(curFont);
        label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

        // Create the Ok/Cancel button region
        Composite buttonComposite = new Composite(shell, SWT.NONE);
        buttonComposite.setLayout(new GridLayout(2, true));
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        Button okButton = new Button(buttonComposite, SWT.PUSH);
        okButton.setFont(curFont);
        I18n.bind(okButton, "button.ok");
        okButton.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
        okButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                newObject = createLink();

                if (newObject != null) {
                    shell.dispose();
                }
            }
        });

        Button cancelButton = new Button(buttonComposite, SWT.PUSH);
        cancelButton.setFont(curFont);
        I18n.bind(cancelButton, "button.cancel");
        cancelButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.FILL, true, false));
        cancelButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                newObject = null;
                shell.dispose();
                ((Vector<Group>)groupList).setSize(0);
            }
        });

        hardLink.setSelection(true);
        targetObject.select(0);

        shell.pack();

        shell.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e)
            {
                if (targetObjectThemeRegistration != null) {
                    targetObjectThemeRegistration.dispose();
                    targetObjectThemeRegistration = null;
                }
                if (curFont != null)
                    curFont.dispose();
            }
        });

        shell.setMinimumSize(shell.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        Rectangle parentBounds = parent.getBounds();
        Point shellSize        = shell.getSize();
        shell.setLocation((parentBounds.x + (parentBounds.width / 2)) - (shellSize.x / 2),
                          (parentBounds.y + (parentBounds.height / 2)) - (shellSize.y / 2));

        shell.open();

        Display display = shell.getDisplay();
        while (!shell.isDisposed())
            if (!display.readAndDispatch())
                display.sleep();
    }

    private HObject createLink()
    {
        String name  = null;
        Group pgroup = null;
        HObject obj  = null;

        name = nameField.getText().trim();
        if ((name == null) || (name.length() < 1)) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.create"),
                            I18n.text("message.nameMissing", I18n.text("common.link")));
            return null;
        }

        if (name.indexOf(HObject.SEPARATOR) >= 0) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.create"),
                            I18n.text("message.nameNoPath", I18n.text("common.link")));
            return null;
        }

        pgroup = groupList.get(parentChoice.getSelectionIndex());

        if (pgroup == null) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.create"), I18n.text("message.parentNull"));
            return null;
        }

        if (hardLink.getSelection()) {
            HObject targetObj = (HObject)objList.get(targetObject.getSelectionIndex());

            if (targetObj == null) {
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.create"), I18n.text("message.targetObjectNull"));
                return null;
            }

            if ((targetObj instanceof Group) && ((Group)targetObj).isRoot()) {
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.create"), I18n.text("message.rootLinkNotAllowed"));
                return null;
            }

            try {
                obj = fileFormat.createLink(pgroup, name, targetObj);
            }
            catch (Exception ex) {
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.create"), ex.getMessage());
                return null;
            }
        }
        else if (softLink.getSelection()) {
            String targetName = targetObject.getText();
            if (targetName.length() < 1) {
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.create"), I18n.text("message.targetNameMissing"));
                return null;
            }

            /*
             * While checking for the existence of the object that the soft link points to,
             * the following function currently just calls H5Oopen, which will fail and
             * throw an HDF5 error stack for dangling soft links. Due to this, we
             * temporarily suppress the HDF5 error stack.
             */
            HObject targetObj = null;
            try {
                H5.H5error_off();
                targetObj = fileFormat.get(targetName);
            }
            catch (Exception ex) {
                /* It is possible that this is a soft link to a non-existent
                 * object, in which case this exception would be normal.
                 * For this reason, no logging is done here even though there
                 * is the possibility of a real HDF5 exception being thrown
                 * if something went terribly wrong.
                 */
            }
            finally {
                H5.H5error_on();
            }

            String tObj = null;
            if (targetObj == null) {
                tObj = targetName;

                if (!tObj.startsWith(HObject.SEPARATOR)) {
                    tObj = HObject.SEPARATOR + tObj;
                }
            }

            if ((targetObj instanceof Group) && ((Group)targetObj).isRoot()) {
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.create"), I18n.text("message.rootLinkNotAllowed"));
                return null;
            }

            try {
                if (targetObj != null)
                    obj = fileFormat.createLink(pgroup, name, targetObj, Group.LINK_TYPE_SOFT);
                else if (tObj != null)
                    obj = fileFormat.createLink(pgroup, name, tObj, Group.LINK_TYPE_SOFT);
            }
            catch (Exception ex) {
                ex.printStackTrace();
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.create"), ex.getMessage());
                return null;
            }
        }
        else if (externalLink.getSelection()) {
            String targetFileName       = targetFile.getText();
            FileFormat targetFileFormat = null;
            int fileAccessID            = FileFormat.FILE_CREATE_OPEN;

            File targetNewFile = new File(targetFileName);

            if (!targetNewFile.exists()) {
                return null;
            }
            FileFormat h5format = FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);
            try {
                targetFileFormat = h5format.createInstance(targetFileName, fileAccessID);
                targetFileFormat.open(); // open the file
            }
            catch (Exception ex) {
                log.debug("external link:", ex);
                return null;
            }

            HObject targetObj = null;
            try {
                targetObj = targetFileFormat.get(targetObject.getText());
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            try {
                targetFileFormat.close();
            }
            catch (Exception ex) {
                log.debug("external link:", ex);
            }

            String tFileObj = null;
            if (targetObj == null) {
                String tObj = null;
                tObj        = targetObject.getText();
                if (tObj.length() < 1) {
                    shell.getDisplay().beep();
                    Tools.showError(shell, I18n.text("action.create"),
                                    I18n.text("message.targetNameMissingShort"));
                    return null;
                }
                tFileObj = targetFileName + FileFormat.FILE_OBJ_SEP + tObj;
            }

            try {
                if (targetObj != null)
                    obj = fileFormat.createLink(pgroup, name, targetObj, Group.LINK_TYPE_EXTERNAL);
                else if (tFileObj != null)
                    obj = fileFormat.createLink(pgroup, name, tFileObj, Group.LINK_TYPE_EXTERNAL);
            }
            catch (Exception ex) {
                ex.printStackTrace();
                shell.getDisplay().beep();
                Tools.showError(shell, I18n.text("action.create"), ex.getMessage());
                return null;
            }
        }

        return obj;
    }

    private String openTargetFile()
    {
        FileDialog fchooser = new FileDialog(shell, SWT.OPEN);
        fchooser.setFilterPath(currentDir);

        DefaultFileFilter filter = DefaultFileFilter.getFileFilter();
        fchooser.setFilterExtensions(new String[] {"*", filter.getExtensions()});
        fchooser.setFilterNames(new String[] {I18n.text("fileChooser.allFiles"), filter.getDescription()});
        fchooser.setFilterIndex(1);

        if (fchooser.open() == null)
            return null;

        File chosenFile = new File(fchooser.getFilterPath() + File.separator + fchooser.getFileName());

        if (!chosenFile.exists()) {
            return null;
        }

        if (chosenFile.isDirectory()) {
            currentDir = chosenFile.getPath();
        }
        else {
            currentDir = chosenFile.getParent();
        }

        // Check if the target File is not the current file.
        String currentFileName = fileFormat.getAbsolutePath();
        if (currentFileName.equals(chosenFile.getAbsolutePath())) {
            Tools.showError(shell, I18n.text("action.open"), I18n.text("message.externalLinkOtherFile"));
            targetFile.setText("");
            return null;
        }

        return chosenFile.getAbsolutePath();
    }

    // Function to check if the target File is open in TreeView
    private boolean isFileOpen(String filename)
    {
        boolean isOpen     = false;
        FileFormat theFile = null;

        Iterator<?> iterator = fileList.iterator();
        while (iterator.hasNext()) {
            theFile = (FileFormat)iterator.next();
            if (theFile.getFilePath().equals(filename)) {
                isOpen = true;
                if (!theFile.isThisType(FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5))) {
                    targetObject.setEnabled(false);
                }
                retrieveObjects(theFile);
                break;
            }
        }

        return isOpen;
    }

    private List<HObject> getAllUserObjectsBreadthFirst(FileFormat file)
    {
        if (file == null)
            return null;

        ArrayList<HObject> breadthFirstObjects = new ArrayList<>();
        Queue<HObject> currentChildren         = new LinkedList<>();
        HObject currentObject                  = file.getRootObject();

        if (currentObject == null) {
            log.debug("getAllUserObjectsBreadthFirst(): file root object is null");
            return null;
        }

        breadthFirstObjects.add(file.getRootObject()); // Add the root object to the list first

        // Add all root object children to a Queue
        currentChildren.addAll(((Group)currentObject).getMemberList());

        while (!currentChildren.isEmpty()) {
            currentObject = currentChildren.remove();
            breadthFirstObjects.add(currentObject);

            if (currentObject instanceof Group) {
                if (((Group)currentObject).getNumberOfMembersInFile() <= 0)
                    continue;

                currentChildren.addAll(((Group)currentObject).getMemberList());
            }
        }

        return breadthFirstObjects;
    }

    // Retrieves the list of objects from the file
    private void retrieveObjects(FileFormat file)
    {
        HObject obj                  = null;
        List<HObject> userObjectList = getAllUserObjectsBreadthFirst(file);
        Iterator<HObject> iterator;
        String fullName = null;

        if (userObjectList == null) {
            log.debug("retrieveObjects(): user object list is null");
            return;
        }

        iterator = userObjectList.iterator();
        while (iterator.hasNext()) {
            obj = iterator.next();

            if (obj instanceof Group) {
                Group g = (Group)obj;
                if (g.isRoot()) {
                    fullName = HObject.SEPARATOR;
                }
                else {
                    fullName = g.getPath() + g.getName() + HObject.SEPARATOR;
                }
            }
            else {
                fullName = obj.getPath() + obj.getName();
            }

            targetObject.add(fullName);
        }

        // Remove the root group "/" from the target objects
        targetObject.remove(0);
    }

    // Retrieves objects from Target File.
    private void getTargetFileObjs()
    {
        FileFormat fileFormatC = null;
        int fileAccessID       = FileFormat.FILE_CREATE_OPEN;
        String filename        = null;
        filename               = targetFile.getText();

        if (filename == null || filename.length() < 1) {
            return;
        }

        // Check if the target File is open in treeView
        if (isFileOpen(filename)) {
            return;
        }

        File chosenFile = new File(filename);

        if (!chosenFile.exists()) {
            targetObject.setEnabled(false);
            return;
        }

        FileFormat h5format = FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);
        try {
            fileFormatC = h5format.createInstance(filename, fileAccessID);
            fileFormatC.open(); // open the file
        }
        catch (Exception ex) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.target"), I18n.text("message.invalidFileFormat"));
            targetFile.setText("");
            return;
        }

        // get the list of objects from the file
        retrieveObjects(fileFormatC);

        try {
            fileFormatC.close();
        }
        catch (Exception ex) {
            log.debug("FileFormat close:", ex);
        }
    }

    /**
     * Get the new dataset created.
     *
     * @return the new dataset created.
     */
    public HObject getObject() { return newObject; }

    /**
     * Get the parent group of the new dataset.
     *
     * @return the parent group of the new dataset.
     */
    public Group getParentGroup() { return parentGroup; }
}
