package com.biorecorder.bdfrecorder;


import com.biorecorder.bdfrecorder.gui.RecorderSettings;
import com.biorecorder.bdfrecorder.gui.RecorderViewModel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;


public class RecorderViewModelImpl implements RecorderViewModel {
    private static final Log log = LogFactory.getLog(RecorderViewModel.class);

    private static final String FAILED_CREATE_DIR_MSG = "Directory: {0}\ncan not be created.";
    private static final String FAILED_SAVE_PREFERENCES = "Failed to save preferences";

    private final EdfBioRecorderApp recorder;
    private final Preferences preferences;

    public RecorderViewModelImpl(EdfBioRecorderApp recorder, Preferences preferences) {
        this.recorder = recorder;
        this.preferences = preferences;
        AppConfig config = preferences.getConfig();
        String comportName = config.getComportName();
        if(comportName == null || comportName.isEmpty()) {
            String[] availableComports = recorder.getAvailableComports();
            if(availableComports.length > 0) {
                comportName = availableComports[0];
                config.setComportName(comportName);
                preferences.saveConfig(config);
            }
        }
        recorder.connectToComport(comportName);
    }


    @Override
    public void addProgressListener(ProgressListener l) {
        recorder.addProgressListener(l);
    }

    @Override
    public void addStateChangeListener(StateChangeListener l) {
        recorder.addStateChangeListener(l);
    }

    @Override
    public void addAvailableComportsListener(AvailableComportsListener l) {
        recorder.addAvailableComportsListener(l);
    }

    @Override
    public RecorderSettingsImpl getInitialSettings() {
        AppConfig appConfig = preferences.getConfig();
        String[] comports = recorder.getAvailableComports();
        return new RecorderSettingsImpl(appConfig, comports);
    }

    @Override
    public Boolean[] getDisconnectionMask() {
        if(recorder.isLoffDetecting()) {
            return recorder.getLeadOffMask();
        }
        return null;
    }

    @Override
    public Integer getBatteryLevel() {
        return recorder.getBatteryLevel();
    }

    @Override
    public String getProgressInfo() {
        String stateString = "Disconnected";
        if(recorder.isActive()) {
            stateString = "Connected";
        }
        if(recorder.isLoffDetecting()) {
            if(recorder.getLeadOffMask() == null) {
                stateString = "Starting...";
            } else {
                stateString = "Checking contacts";
            }
        } else if(recorder.isRecording()) {
            long numberOfWrittenDataRecords = recorder.getNumberOfWrittenDataRecords();
            if(numberOfWrittenDataRecords == 0) {
                stateString = "Starting...";
            } else {
                stateString = "Recording:  " + numberOfWrittenDataRecords + " data records";
            }
        }
        return stateString;
    }

    @Override
    public boolean isActive() {
        return recorder.isActive();
    }

    @Override
    public boolean isRecording() {
        return recorder.isRecording();
    }

    @Override
    public boolean isCheckingContacts() {
        return recorder.isLoffDetecting();
    }

    @Override
    public void changeComport(String comportName) {
        recorder.connectToComport(comportName);
    }

    @Override
    public RecorderSettings changeDeviceType(RecorderSettings settings) {
        return settings;
    }

    @Override
    public RecorderSettings changeMaxFrequency(RecorderSettings settings) {
        return settings;
    }

    @Override
    public boolean isDirectoryExist(String directory) {
        File dir = new File(directory);
        if (dir.exists() && dir.isDirectory()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isFileExist(String directory, String filename) {
        File file = new File(directory, recorder.normalizeFilename(filename));
        if(file.exists() && file.isFile()) {
            return true;
        }
        return false;
    }

    @Override
    public String getNormalizedFilename(String directory, String filename) {
        File file = new File(directory, recorder.normalizeFilename(filename));
        return file.toString();
    }

    /**
     * Create directory if it does not exist.
     *
     * @param directory name of the directory to create
     * @return OperationResult: successful if directory was created
     */
    @Override
    public OperationResult createDirectory(String directory) {
        File dir = new File(directory);
        try {
            if (dir.mkdir()) {
                return new OperationResult(true);
            } else {
                return new OperationResult(false, new Message(Message.TYPE_FAILED_CREATE_DIR, dir.toString()));
            }
        } catch (Exception ex) {
            log.error(ex);
            return new OperationResult(false, new Message(Message.TYPE_FAILED_CREATE_DIR,  ex.getMessage()));
        }
    }

    @Override
    public OperationResult startRecording(RecorderSettings settings) {
        RecorderSettingsImpl settingsImpl = (RecorderSettingsImpl) settings;
        return recorder.startRecording(settingsImpl.getAppConfig());
    }

    @Override
    public OperationResult checkContacts(RecorderSettings settings) {
        RecorderSettingsImpl settingsImpl = (RecorderSettingsImpl) settings;
        return recorder.detectLoffStatus(settingsImpl.getAppConfig());
    }

    @Override
    public void stop() {
        recorder.stop();
    }

    @Override
    public void closeApplication(RecorderSettings settings) {
        recorder.finalize();
        try{
            RecorderSettingsImpl settingsImpl = (RecorderSettingsImpl) settings;
            preferences.saveConfig(settingsImpl.getAppConfig());
        } catch (Exception ex) {
            log.error(FAILED_SAVE_PREFERENCES, ex);
        }
    }
}
