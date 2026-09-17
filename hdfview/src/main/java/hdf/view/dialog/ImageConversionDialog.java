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
import java.util.Iterator;
import java.util.List;

import hdf.object.FileFormat;
import hdf.view.DefaultFileFilter;
import hdf.view.Tools;
import hdf.view.ViewProperties;
import hdf.view.i18n.I18n;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * ImageConversionDialog shows a message dialog requesting user input for
 * converting files.
 *
 * @author Jordan T. Henderson
 * @version 2.4 1/28/2016
 */
public class ImageConversionDialog extends Dialog {
    private Shell shell;

    private Font curFont;

    private String fileTypeFrom;

    private String fileTypeTo;

    private Text srcFileField;

    private Text dstFileField;

    private boolean isConverted;

    private boolean isConvertedFromImage;

    private String convertedFile;

    private String toFileExtension;

    private List<FileFormat> fileList;

    private String currentDir;

    /**
     * Constructs a FileConversionDialog.
     *
     * @param parent    The parent shell of the dialog.
     * @param typeFrom  source file type
     * @param typeTo    destination file type
     * @param dir       current file directory
     * @param openFiles The list of currently open files
     */
    public ImageConversionDialog(Shell parent, String typeFrom, String typeTo, String dir,
                                 List<FileFormat> openFiles)
    {
        super(parent, SWT.APPLICATION_MODAL);

        try {
            curFont = new Font(Display.getCurrent(), ViewProperties.getFontType(),
                               ViewProperties.getFontSize(), SWT.NORMAL);
        }
        catch (Exception ex) {
            curFont = null;
        }

        fileTypeFrom         = typeFrom;
        fileTypeTo           = typeTo;
        isConverted          = false;
        isConvertedFromImage = false;
        fileList             = openFiles;
        toFileExtension      = "";
        currentDir           = dir;
    }

    /**
     * Open the ImageConversionDialog for converting images.
     */
    public void open()
    {
        Shell parent = getParent();
        shell        = new Shell(parent, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL);
        shell.setFont(curFont);
        I18n.bind(shell, "dialog.convertImage.title");
        shell.setImages(ViewProperties.getHdfIcons());
        shell.setLayout(new GridLayout(1, true));

        if (fileTypeTo.equals(FileFormat.FILE_TYPE_HDF5)) {
            toFileExtension = ".h5";
            I18n.bind(shell, "dialog.convertImage.hdf5.title");
            isConvertedFromImage = true;
        }
        else if (fileTypeTo.equals(FileFormat.FILE_TYPE_HDF4)) {
            toFileExtension = ".hdf";
            I18n.bind(shell, "dialog.convertImage.hdf4.title");
            isConvertedFromImage = true;
        }

        // Create content region
        Composite contentComposite = new Composite(shell, SWT.NONE);
        contentComposite.setLayout(new GridLayout(3, false));
        contentComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label label = new Label(contentComposite, SWT.RIGHT);
        label.setFont(curFont);
        I18n.bind(label, "label.imageFile");

        srcFileField = new Text(contentComposite, SWT.SINGLE | SWT.BORDER);
        srcFileField.setFont(curFont);
        GridData fieldData     = new GridData(SWT.FILL, SWT.FILL, true, false);
        fieldData.minimumWidth = 350;
        srcFileField.setLayoutData(fieldData);

        Button browseButton = new Button(contentComposite, SWT.PUSH);
        browseButton.setFont(curFont);
        I18n.bind(browseButton, "button.browse");
        browseButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e)
            {
                FileDialog fChooser = new FileDialog(shell, SWT.OPEN);
                fChooser.setFilterPath(currentDir);

                if (isConvertedFromImage) {
                    DefaultFileFilter filter = DefaultFileFilter.getImageFileFilter();
                    fChooser.setFilterExtensions(new String[] {"*", filter.getExtensions()});
                    fChooser.setFilterNames(new String[] {I18n.text("fileChooser.allFiles"),
                                                          filter.getDescription()});
                    fChooser.setFilterIndex(1);
                }
                else {
                    fChooser.setFilterExtensions(new String[] {"*"});
                    fChooser.setFilterNames(new String[] {I18n.text("fileChooser.allFiles")});
                    fChooser.setFilterIndex(0);
                }

                String filename = fChooser.open();

                if (filename == null) {
                    return;
                }

                File chosenFile = new File(filename);

                currentDir = chosenFile.getParent();
                srcFileField.setText(chosenFile.getAbsolutePath());
                dstFileField.setText(chosenFile.getAbsolutePath() + toFileExtension);
            }
        });

        label = new Label(contentComposite, SWT.RIGHT);
        label.setFont(curFont);
        I18n.bind(label, "label.hdfFile");

        dstFileField = new Text(contentComposite, SWT.SINGLE | SWT.BORDER);
        dstFileField.setFont(curFont);
        dstFileField.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        browseButton = new Button(contentComposite, SWT.PUSH);
        browseButton.setFont(curFont);
        I18n.bind(browseButton, "button.browse");
        browseButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e)
            {
                FileDialog fChooser = new FileDialog(shell, SWT.OPEN);

                fChooser.setFilterExtensions(new String[] {"*"});
                fChooser.setFilterNames(new String[] {I18n.text("fileChooser.allFiles")});
                fChooser.setFilterIndex(0);

                String filename = fChooser.open();

                if (filename == null) {
                    return;
                }

                dstFileField.setText(filename);
            }
        });

        // Dummy label to fill space as dialog is resized
        new Label(shell, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Create Ok/Cancel button
        Composite buttonComposite = new Composite(shell, SWT.NONE);
        buttonComposite.setLayout(new GridLayout(2, true));
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        Button okButton = new Button(buttonComposite, SWT.PUSH);
        okButton.setFont(curFont);
        I18n.bind(okButton, "button.ok");
        okButton.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
        okButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e)
            {
                isConverted = convert();

                if (isConverted) {
                    shell.dispose();
                }
            }
        });

        Button cancelButton = new Button(buttonComposite, SWT.PUSH);
        cancelButton.setFont(curFont);
        I18n.bind(cancelButton, "button.cancel");
        cancelButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.FILL, true, false));
        cancelButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e)
            {
                isConverted   = false;
                convertedFile = null;
                shell.dispose();
            }
        });

        shell.pack();

        shell.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e)
            {
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

        Display display = parent.getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }
    }

    /**
     * Convert file.
     *
     * @return status of convert
     */
    private boolean convert()
    {
        boolean converted = false;
        String srcFile    = srcFileField.getText();
        String dstFile    = dstFileField.getText();

        if ((srcFile == null) || (dstFile == null)) {
            return false;
        }

        srcFile = srcFile.trim();
        dstFile = dstFile.trim();
        if ((srcFile == null) || (srcFile.length() <= 0) || (dstFile == null) || (dstFile.length() <= 0)) {
            return false;
        }

        // verify the source file
        File f = new File(srcFile);
        if (!f.exists()) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.convert"), I18n.text("message.sourceFileMissing"));
            return false;
        }
        else if (f.isDirectory()) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.convert"), I18n.text("message.sourceFileDirectory"));
            return false;
        }

        // verify target file
        String srcPath = f.getParent();
        f              = new File(dstFile);
        File pfile     = f.getParentFile();
        if (pfile == null) {
            dstFile = srcPath + File.separator + dstFile;
            f       = new File(dstFile);
        }
        else if (!pfile.exists()) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.convert"),
                            I18n.text("message.destinationPathMissing", pfile.getPath()));
            return false;
        }

        // check if the file is in use
        if (fileList != null) {
            FileFormat theFile            = null;
            Iterator<FileFormat> iterator = fileList.iterator();
            while (iterator.hasNext()) {
                theFile = (FileFormat)iterator.next();
                if (theFile.getFilePath().equals(dstFile)) {
                    shell.getDisplay().beep();
                    Tools.showError(shell, I18n.text("action.convert"),
                                    I18n.text("message.destinationInUse"));
                    return false;
                }
            }
        }

        if (f.exists()) {
            if (!Tools.showConfirm(shell, I18n.text("action.convert"),
                                   I18n.text("message.destinationExists")))
                return false;
        }

        try {
            Tools.convertImageToHDF(srcFile, dstFile, fileTypeFrom, fileTypeTo);
            convertedFile = dstFile;
            converted     = true;
        }
        catch (Exception ex) {
            convertedFile = null;
            converted     = false;
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.convert"), ex.getMessage());
            return false;
        }

        return converted;
    }

    /**
     * if an image file has been converted.
     *
     * @return the state of conversion
     */
    public boolean isFileConverted() { return isConverted; }

    /**
     * get the file of an image file that has been converted.
     *
     * @return the name of the converted file
     */
    public String getConvertedFile() { return convertedFile; }
}
