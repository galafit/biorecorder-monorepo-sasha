package com.biorecorder.bdfrecorder.gui.file_gui;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Created by gala on 01/11/16.
 */
public class FileToSaveUI extends JPanel {
    private static final int DEFAULT_FILENAME_LENGTH = 16;
    private static final int DEFAULT_DIRNAME_LENGTH = 45;

    private JTextField filename;
    private DirectoryField directory;

    private String filenameLabel = "Filename";

    public FileToSaveUI() {
        this(DEFAULT_FILENAME_LENGTH, DEFAULT_DIRNAME_LENGTH);
    }

    public FileToSaveUI(int filenameLength, int dirnameLength) {
        filename = new JTextField(filenameLength);
        directory = new DirectoryField();
        directory.setLength(dirnameLength);

        int hgap = 5;
        int vgap = 0;
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, hgap, vgap));
        filePanel.add(new JLabel(filenameLabel));
        filePanel.add(filename);
        setLayout(new MigLayout("fill, insets 5", "left", "baseline"));
        add(filePanel, "gapleft 20");
        add(directory, "growx");

        filename.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent focusEvent) {
                filename.selectAll();
            }
        });
    }

    public String getFilename() {
       return filename.getText();
    }

    public void setFilename(String name) {
        filename.setText(name);
    }

    public void setDirectory(String dirName) {
        directory.setDirectory(dirName);
    }


    public String getDirectory() {
        return directory.getDirectory();
    }


    @Override
    public void setEnabled(boolean isEnabled) {
        filename.setEnabled(isEnabled);
        directory.setEnabled(isEnabled);
    }
}

