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

import java.lang.reflect.Array;

import hdf.view.ThemeManager;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * MathConversionDialog shows a message dialog requesting user input for math
 * conversion.
 *
 * @author Jordan T. Henderson
 * @version 2.4 1/28/2016
 */
public class MathConversionDialog extends Dialog {
    private Shell shell;

    private Font curFont;

    private Text aField;

    private Text bField;

    private Text infoArea;

    private List functionList;

    private Object dataValue;

    private char NT;

    private String[] functionDescriptionKeys;

    private boolean isConverted;

    /**
     * Constructs MathConversionDialog.
     *
     * @param parent
     *            the owner of the input
     * @param data
     *            the data array to convert.
     */
    public MathConversionDialog(Shell parent, Object data)
    {
        super(parent, SWT.APPLICATION_MODAL);

        try {
            curFont = new Font(Display.getCurrent(), ViewProperties.getFontType(),
                               ViewProperties.getFontSize(), SWT.NORMAL);
        }
        catch (Exception ex) {
            curFont = null;
        }

        isConverted = false;
        dataValue   = data;
        NT          = ' ';

        String cName = data.getClass().getName();
        int cIndex   = cName.lastIndexOf('[');
        if (cIndex >= 0) {
            NT = cName.charAt(cIndex + 1);
        }

        functionDescriptionKeys = new String[] {"math.description.0", "math.description.1",
                                                "math.description.2", "math.description.3",
                                                "math.description.4", "math.description.5",
                                                "math.description.6", "math.description.7",
                                                "math.description.8", "math.description.9"};
    }

    /**
     * Open the MathConversionDialog for converting data.
     */
    public void open()
    {
        Shell parent = getParent();
        shell        = new Shell(parent, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL);
        ThemeManager themeManager = ThemeManager.forDisplay(shell.getDisplay());
        themeManager.applyTo(shell);
        shell.setFont(curFont);
        I18n.bind(shell, "dialog.convertData.title");
        shell.setImages(ViewProperties.getHdfIcons());
        shell.setLayout(new GridLayout(1, true));

        // Create content region
        org.eclipse.swt.widgets.Group contentGroup = new org.eclipse.swt.widgets.Group(shell, SWT.NONE);
        contentGroup.setFont(curFont);
        I18n.bind(contentGroup, "dialog.convertData.group");
        contentGroup.setLayout(new GridLayout(2, false));
        contentGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        functionList = new List(contentGroup, SWT.SINGLE | SWT.BORDER);
        functionList.setFont(curFont);
        I18n.bindItems(functionList, "math.function.clip", "math.function.absolute", "math.function.linear",
                       "math.function.power", "math.function.exponential", "math.function.naturalLog",
                       "math.function.log", "math.function.sin", "math.function.cos", "math.function.tan");
        GridData functionListData     = new GridData(SWT.FILL, SWT.FILL, true, false);
        functionListData.minimumWidth = 350;
        functionList.setLayoutData(functionListData);
        functionList.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                int index = functionList.getSelectionIndex();
                I18n.bind(infoArea, functionDescriptionKeys[index]);

                if ((index == 0) || (index == 2)) {
                    aField.setEnabled(true);
                    bField.setEnabled(true);
                }
                else if ((index == 3) || (index == 6)) {
                    aField.setEnabled(true);
                    bField.setEnabled(false);
                }
                else {
                    aField.setEnabled(false);
                    bField.setEnabled(false);
                }
            }
        });

        Composite fieldComposite = new Composite(contentGroup, SWT.NONE);
        fieldComposite.setLayout(new GridLayout(2, false));
        fieldComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        Label label = new Label(fieldComposite, SWT.RIGHT);
        label.setFont(curFont);
        I18n.bind(label, "math.parameterA");

        aField                  = new Text(fieldComposite, SWT.SINGLE | SWT.BORDER);
        GridData aFieldData     = new GridData(SWT.FILL, SWT.FILL, true, false);
        aFieldData.minimumWidth = 100;
        aField.setLayoutData(aFieldData);
        aField.setFont(curFont);
        aField.setText("0");
        aField.setEnabled(false);

        label = new Label(fieldComposite, SWT.RIGHT);
        label.setFont(curFont);
        I18n.bind(label, "math.parameterB");

        bField = new Text(fieldComposite, SWT.SINGLE | SWT.BORDER);
        bField.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        bField.setFont(curFont);
        bField.setText("1");
        bField.setEnabled(false);

        infoArea = new Text(contentGroup, SWT.MULTI | SWT.BORDER | SWT.WRAP);
        infoArea.setEditable(false);
        infoArea.setFont(curFont);
        themeManager.bind(infoArea, ThemeManager.ColorRole.READ_ONLY_BACKGROUND,
                          ThemeManager.ColorRole.FOREGROUND);
        GridData infoAreaData      = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        infoAreaData.minimumHeight = 150;
        infoArea.setLayoutData(infoAreaData);

        // Create Ok/Cancel button region
        Composite buttonComposite = new Composite(shell, SWT.NONE);
        buttonComposite.setLayout(new GridLayout(2, true));
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        Button okButton = new Button(buttonComposite, SWT.PUSH);
        okButton.setFont(curFont);
        I18n.bind(okButton, "button.ok");
        okButton.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));
        okButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                isConverted = convertData();

                shell.dispose();
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
                isConverted = false;

                shell.dispose();
            }
        });

        shell.pack();

        shell.addDisposeListener(new DisposeListener() {
            @Override
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

    private boolean convertData()
    {
        double a = 0;
        double b = 1;

        int index = functionList.getSelectionIndex();

        try {
            if ((index == 0) || (index == 2)) {
                a = Double.parseDouble(aField.getText().trim());
                b = Double.parseDouble(bField.getText().trim());
            }
            else if (index == 3) {
                a = Double.parseDouble(aField.getText().trim());
            }
            else if (index == 6) {
                a = Integer.parseInt(aField.getText().trim());
                if (a <= 0) {
                    shell.getDisplay().beep();
                    Tools.showError(shell, I18n.text("action.convert"),
                                    I18n.text("message.parameterPositive"));
                    return false;
                }
            }
        }
        catch (Exception ex) {
            shell.getDisplay().beep();
            Tools.showError(shell, I18n.text("action.convert"), ex.getMessage());
            return false;
        }

        int n        = Array.getLength(dataValue);
        double value = 0;
        double x     = 0;

        switch (NT) {
        case 'B':
            byte[] bdata = (byte[])dataValue;
            for (int i = 0; i < n; i++) {
                x     = bdata[i];
                value = y(index, x, a, b);
                if ((value > Byte.MAX_VALUE) || (value < Byte.MIN_VALUE)) {
                    Tools.showError(shell, I18n.text("action.convert"),
                                    I18n.text("message.invalidValue", "byte", (long)value));
                    return false;
                }

                bdata[i] = (byte)value;
            }
            break;
        case 'S':
            short[] sdata = (short[])dataValue;
            for (int i = 0; i < n; i++) {
                x     = sdata[i];
                value = y(index, x, a, b);
                if ((value > Short.MAX_VALUE) || (value < Short.MIN_VALUE)) {
                    Tools.showError(shell, I18n.text("action.convert"),
                                    I18n.text("message.invalidValue", "short", (long)value));
                    return false;
                }

                sdata[i] = (short)value;
            }
            break;
        case 'I':
            int[] idata = (int[])dataValue;
            for (int i = 0; i < n; i++) {
                x     = idata[i];
                value = y(index, x, a, b);
                if ((value > Integer.MAX_VALUE) || (value < Integer.MIN_VALUE)) {
                    Tools.showError(shell, I18n.text("action.convert"),
                                    I18n.text("message.invalidValue", "int", (long)value));
                    return false;
                }

                idata[i] = (int)value;
            }
            break;
        case 'J':
            long[] ldata = (long[])dataValue;
            for (int i = 0; i < n; i++) {
                x     = ldata[i];
                value = y(index, x, a, b);
                if ((value > Long.MAX_VALUE) || (value < Long.MIN_VALUE)) {
                    Tools.showError(shell, I18n.text("action.convert"),
                                    I18n.text("message.invalidValue", "long", (long)value));
                    return false;
                }

                ldata[i] = (long)value;
            }
            break;
        case 'F':
            float[] fdata = (float[])dataValue;
            for (int i = 0; i < n; i++) {
                x     = fdata[i];
                value = y(index, x, a, b);
                if ((value > Float.MAX_VALUE) || (value < -Float.MAX_VALUE) || (value == Float.NaN)) {
                    Tools.showError(shell, I18n.text("action.convert"),
                                    I18n.text("message.invalidValue", "float", value));
                    return false;
                }

                fdata[i] = (float)value;
            }
            break;
        case 'D':
            double[] ddata = (double[])dataValue;
            for (int i = 0; i < n; i++) {
                x     = ddata[i];
                value = y(index, x, a, b);
                if ((value > Double.MAX_VALUE) || (value < -Double.MAX_VALUE) || (value == Double.NaN)) {
                    Tools.showError(shell, I18n.text("action.convert"),
                                    I18n.text("message.invalidValue", "double", value));
                    return false;
                }

                ddata[i] = value;
            }
            break;
        default:
            break;
        }

        return true;
    }

    private double y(int index, double x, double a, double b)
    {
        double y = x;

        switch (index) {
        case 0:
            if (x < a) {
                y = a;
            }
            else if (x > b) {
                y = b;
            }
            break;
        case 1:
            y = Math.abs(x);
            break;
        case 2:
            y = (a + b * x);
            break;
        case 3:
            y = Math.pow(x, a);
            break;
        case 4:
            y = Math.exp(x);
            break;
        case 5:
            y = Math.log(x);
            break;
        case 6:
            y = (Math.log(x) / Math.log(a));
            break;
        case 7:
            y = Math.sin(x);
            break;
        case 8:
            y = Math.cos(x);
            break;
        case 9:
            y = Math.tan(x);
            break;
        default:
            break;
        }

        return y;
    }

    /**
     * Check if the data is successfully converted.
     *
     * @return true if the data is successfully converted.
     */
    public boolean isConverted() { return isConverted; }
}
