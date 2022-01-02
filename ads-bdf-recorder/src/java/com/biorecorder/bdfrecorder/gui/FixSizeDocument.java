package com.biorecorder.bdfrecorder.gui;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;

/**
 * To limit JTextField possible input to given «size» of characters
 */
public class FixSizeDocument extends PlainDocument
{
    private int size;

    public FixSizeDocument(int size)
    {
        this.size = size;
    }

    @Override
    public void insertString(int offs, String str, AttributeSet a)
            throws BadLocationException
    {
        if (getLength() + str.length() > size) {
            str = str.substring(0, size - getLength());
            Toolkit.getDefaultToolkit().beep();
        }
        super.insertString(offs, str, a);
    }
}


