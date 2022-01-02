package com.biorecorder.bdfrecorder;

import com.biorecorder.edflib.EdfFileWriter;
import com.biorecorder.filters.digitalfilter.IntMovingAverage;
import com.biorecorder.edflib.DataRecordStream;
import com.biorecorder.edflib.DataHeader;
import com.biorecorder.bdfrecorder.recorder.*;
import com.sun.istack.internal.Nullable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by galafit on 2/6/17.
 */
public class EdfBioRecorderApp {
    private static final Log log = LogFactory.getLog(EdfBioRecorderApp.class);

    private static final int NOTIFICATION_PERIOD_MS = 1000;
    private static final int NOTIFICATION_QUARTER_PERIOD_MS = NOTIFICATION_PERIOD_MS / 4;
    private static final int COMPORT_CONNECTION_PERIOD_MS = 2000;
    private static final int AVAILABLE_COMPORTS_CHECKING_PERIOD_MS = 3000;
    private static final int FUTURE_CHECKING_PERIOD_MS = 1000;

    private final Timer timer = new Timer("EdfBioRecorderApp Timer");

    private volatile ProgressListener progressListener = new NullProgressListener();
    private volatile StateChangeListener stateChangeListener = new NullStateChangeListener();
    private volatile AvailableComportsListener availableComportsListener = new NullAvailableComportsListener();

    private volatile BioRecorder bioRecorder;
    private volatile Boolean[] leadOffBitMask;
    private volatile Integer batteryLevel;
    private volatile String comportName;
    private volatile TimerTask connectionTask = new ConnectionTask();
    private volatile TimerTask availableComportsTask = new AvailableComportsTask();
    private volatile TimerTask notificationTask = new NotificationTask();

    private volatile TimerTask startFutureHandlingTask;
    private volatile boolean isLoffDetecting;
    private volatile long lastNotificationTime;
    private volatile int numberOfWrittenDataRecords;

    private List<DataRecordStream> dataListeners = new ArrayList<>(1);


    public EdfBioRecorderApp() {
        restartAvailableComportsTask();
        restartNotificationTask();
    }

    public void addDataListener(DataRecordStream dataRecordStream) {
        dataListeners.add(dataRecordStream);
    }

    public void removeDataListeners() {
        dataListeners.clear();
    }

    public boolean connectToComport(String comportName) {
        if (comportName == null || comportName.isEmpty() || isRecording()) {
            return false;
        }
        this.comportName = comportName;
        restartConnectionTask();
        return true;
    }

    public String getComportName() {
        return comportName;
    }


    public synchronized boolean isRecording() {
        if (bioRecorder != null && bioRecorder.isRecording()) {
            return true;
        }
        return false;
    }

    public boolean isLoffDetecting() {
        if (isLoffDetecting && isRecording()) {
            return true;
        }
        return false;
    }

    public synchronized boolean isActive() {
        if (bioRecorder != null && (bioRecorder.isActive() || bioRecorder.isRecording())) {
            return true;
        }
        return false;
    }

    private synchronized void createRecorder(String comportName) throws ConnectionRuntimeException {
        if (bioRecorder != null) {
            if (bioRecorder.getComportName().equals(comportName)) {
                return;
            } else {
                disconnectRecorder();
            }
        }

        bioRecorder = new BioRecorder(comportName);
        bioRecorder.addEventsListener(new EventsListener() {
            public void handleLowBattery() {
                notifyStateChange(new Message(Message.TYPE_LOW_BUTTERY));
                stop1();
            }
        });

        bioRecorder.addLeadOffListener(new LeadOffListener() {
            public void onLeadOffMaskReceived(Boolean[] leadOffMask) {
                leadOffBitMask = leadOffMask;
                notifyProgressOnDataReceived();
            }
        });

        bioRecorder.addButteryLevelListener(new BatteryLevelListener() {
            public void onBatteryLevelReceived(int batteryLevel1) {
                batteryLevel = batteryLevel1;
            }
        });
    }

    private synchronized void startMonitoring() throws IllegalStateException {
        if (bioRecorder != null) {
            bioRecorder.startMonitoring();
        }
    }

    public synchronized OperationResult detectLoffStatus(AppConfig appConfig) {
        return start(appConfig, true);
    }

    public synchronized OperationResult startRecording(AppConfig appConfig) {
        return start(appConfig, false);
    }

    private synchronized OperationResult start(AppConfig appConfig, boolean isLoffDetection) {
        if (bioRecorder != null && bioRecorder.isRecording()) {
            return new OperationResult(false, new Message(Message.TYPE_ALREADY_RECORDING));
        }
        numberOfWrittenDataRecords = 0;
        String comportName = appConfig.getComportName();
        if (comportName == null || comportName.isEmpty()) {
            return new OperationResult(false, new Message(Message.TYPE_COMPORT_NULL));
        }
        // make a copy to safely change config
        RecorderConfig recorderConfig = new RecorderConfig(appConfig.getRecorderConfig());
        boolean isAllChannelsDisabled = true;
        for (int i = 0; i < recorderConfig.getChannelsCount(); i++) {
            if (recorderConfig.isChannelEnabled(i)) {
                isAllChannelsDisabled = false;
                break;
            }
        }
        if (isAllChannelsDisabled) {
            if (isLoffDetection) {
                return new OperationResult(false, new Message(Message.TYPE_CHANNELS_DISABLED));
            }
            if (!recorderConfig.isAccelerometerEnabled()) {
                return new OperationResult(false, new Message(Message.TYPE_CHANNELS_AND_ACCELEROMETER_DISABLED));
            }
        }

        this.comportName = comportName;
        connectionTask.cancel();

        this.isLoffDetecting = isLoffDetection;
        try {
            createRecorder(comportName);
        } catch (ConnectionRuntimeException ex) {
            Message errMSg = new Message(Message.TYPE_CONNECTION_ERROR, ex.getMessage());
            if (ex.getExceptionType() == ConnectionRuntimeException.TYPE_PORT_BUSY) {
                errMSg = new Message(Message.TYPE_COMPORT_BUSY, comportName);
            }
            if (ex.getExceptionType() == ConnectionRuntimeException.TYPE_PORT_NOT_FOUND) {
                errMSg = new Message(Message.TYPE_COMPORT_NOT_FOUND, comportName);
            }
            return new OperationResult(false, errMSg);
        }

        bioRecorder.removeDataListeners();
        // remove all previously added filters
        bioRecorder.removeChannelsFilters();
        List<DataRecordStream> streams = new ArrayList<>(2 + dataListeners.size());
        if (isLoffDetection) { // lead off detection
            leadOffBitMask = null;
            recorderConfig.setSampleRate(RecorderSampleRate.S500);
            recorderConfig.setAccelerometerEnabled(false);
            recorderConfig.setBatteryVoltageChannelDeletingEnable(false);
            for (int i = 0; i < recorderConfig.getChannelsCount(); i++) {
                recorderConfig.setChannelLeadOffEnable(i, true);
                RecorderDivider[] dividers = RecorderDivider.values();
                RecorderDivider maxDivider = dividers[dividers.length - 1];
                recorderConfig.setChannelDivider(i, maxDivider);
            }
        } else { // normal recording and writing to the file
            for (int i = 0; i < recorderConfig.getChannelsCount(); i++) {
                // disable lead off detection
                recorderConfig.setChannelLeadOffEnable(i, false);
                if (appConfig.is50HzFilterEnabled(i)) {
                    // Apply MovingAverage filter to the channel to reduce 50Hz noise
                    int numberOfAveragingPoints = recorderConfig.getChannelSampleRate(i) / 50;
                    if (numberOfAveragingPoints > 1) {
                        bioRecorder.addChannelFilter(i, new IntMovingAverage(numberOfAveragingPoints), "MovAvg:" + numberOfAveragingPoints);
                    }
                }
            }
            // create lab stream
            if (appConfig.isLabStreamingEnabled()) {
                int numberOfAccChannels = 0;
                int numberOfAdsChannels = 0;

                for (int i = 0; i < appConfig.getRecorderConfig().getChannelsCount(); i++) {
                    if (appConfig.getRecorderConfig().isChannelEnabled(i)) {
                        numberOfAdsChannels++;
                    }
                }
                if (appConfig.getRecorderConfig().isAccelerometerEnabled()) {
                    if (appConfig.getRecorderConfig().isAccelerometerOneChannelMode()) {
                        numberOfAccChannels = 1;
                    } else {
                        numberOfAccChannels = 3;
                    }
                }
                try {
                    DataRecordStream lslStream = new LslStream(numberOfAdsChannels, numberOfAccChannels);
                    streams.add(lslStream);
                } catch (IllegalArgumentException ex) {
                    log.info("LabStreaming failed to start", ex);
                    return new OperationResult(false, new Message(Message.TYPE_LAB_STREAMING_FAILED));
                }
            }

            // check if dir name is ok and directory exist
            String dirname = appConfig.getDirToSave();
            File dir = new File(dirname);
            if (!dir.exists() && !dir.isDirectory()) {
                return new OperationResult(false, new Message(Message.TYPE_DIRECTORY_NOT_EXIST, dir.toString()));
            }
            // create edf file stream
            File edfFile = new File(dirname, normalizeFilename(appConfig.getFileName()));
            try {
                FileStream edfStream = new FileStream(edfFile);
                streams.add(edfStream);
            } catch (FileNotFoundException ex) {
                log.error(ex);
                return new OperationResult(false, new Message(Message.TYPE_FILE_NOT_ACCESSIBLE, edfFile.toString()));
            }
            // add data listeners
            for (DataRecordStream listener : dataListeners) {
                streams.add(listener);
            }
            bioRecorder.addDataListener(new BioRecorderDataHandler(streams, appConfig));
        }

        Future startFuture = bioRecorder.startRecording(recorderConfig);
        startFutureHandlingTask = new StartFutureHandlingTask(startFuture, recorderConfig.getDeviceType(), streams);
        timer.schedule(startFutureHandlingTask, FUTURE_CHECKING_PERIOD_MS, FUTURE_CHECKING_PERIOD_MS);
        notifyStateChange(null);
        return new OperationResult(true);
    }

    class BioRecorderDataHandler implements DataRecordStream {
        private final List<DataRecordStream> streams;
        private final AppConfig appConfig;

        public BioRecorderDataHandler(List<DataRecordStream> streams, AppConfig appConfig) {
            this.streams = streams;
            this.appConfig = appConfig;
        }

        @Override
        public void setHeader(DataHeader header) {
            header.setPatientIdentification(appConfig.getPatientIdentification());
            header.setRecordingIdentification(appConfig.getRecordingIdentification());
            for (DataRecordStream stream : streams) {
                stream.setHeader(header);
            }
        }

        @Override
        public void writeDataRecord(int[] dataRecord) {
            try {
                for (DataRecordStream stream : streams) {
                    stream.writeDataRecord(dataRecord);
                }
                numberOfWrittenDataRecords++;
                notifyProgressOnDataReceived();
            } catch (IORuntimeException ex) {
                stop();
                notifyStateChange(new Message(Message.TYPE_FAILED_WRITE_DATA));
                log.error(ex);
            } catch (Exception ex) {
                // after stopping BioRecorder and closing streams
                // some records still can be received and IllegalStateException
                // can be thrown.
                log.info(ex);
            }
        }

        @Override
        public void close() {
            try {
                for (DataRecordStream stream : streams) {
                    stream.close();
                }
            } catch (Exception ex) {
                log.error(ex);
                Message msg = new Message(Message.TYPE_FAILED_CLOSE_FILE, ex.getMessage());
                notifyStateChange(msg);
            }
        }
    }

    class StartFutureHandlingTask extends TimerTask {
        private Future future;
        private final List<DataRecordStream> streams;
        private RecorderType recorderType;

        public StartFutureHandlingTask(Future future, RecorderType recorderType, List<DataRecordStream> streams) {
            this.future = future;
            this.streams = streams;
            this.recorderType = recorderType;
        }

        public void run() {
            if (future.isDone()) {
                try {
                    // if start successful
                    future.get();
                    availableComportsTask.cancel();
                    notificationTask.cancel();
                } catch (ExecutionException e) {
                    closeStreamsAndStartMonitoring();
                    RecorderType connectedRecorder = getConnectedRecorder();
                    if (connectedRecorder != null && recorderType != connectedRecorder) {
                        notifyStateChange(new Message(Message.TYPE_WRONG_DEVICE));

                    } else {
                        notifyStateChange(new Message(Message.TYPE_START_FAILED));
                    }
                } catch (CancellationException | InterruptedException e) {
                    closeStreamsAndStartMonitoring();
                    notifyStateChange(null);
                } finally {
                    cancel(); // cancel this task
                }
            }
        }

        private void closeStreamsAndStartMonitoring() {
            startMonitoring();
            for (DataRecordStream stream : streams) {
                try {
                    stream.close();
                } catch (Exception ex) {
                    log.error(ex);
                }
            }
        }
    }

    private synchronized RecorderType getConnectedRecorder() {
        if (bioRecorder != null) {
            return bioRecorder.getDeviceType();
        }
        return null;
    }


    private void restartAvailableComportsTask() {
        availableComportsTask.cancel();
        availableComportsTask = new AvailableComportsTask();
        timer.schedule(availableComportsTask, AVAILABLE_COMPORTS_CHECKING_PERIOD_MS, AVAILABLE_COMPORTS_CHECKING_PERIOD_MS);
    }

    private void restartNotificationTask() {
        notificationTask.cancel();
        notificationTask = new NotificationTask();
        timer.schedule(notificationTask, NOTIFICATION_PERIOD_MS, NOTIFICATION_PERIOD_MS);
    }

    private void restartConnectionTask() {
        connectionTask.cancel();
        connectionTask = new ConnectionTask();
        timer.schedule(connectionTask, COMPORT_CONNECTION_PERIOD_MS, COMPORT_CONNECTION_PERIOD_MS);
    }

    /**
     * When ads stopped we do notification by timer.
     * When ads recording we do notification on data receiving.
     * After "bluetooth disconnection" all "lost" records come
     * at once and to avoid "notifications overload" we check
     * the time between current data and previous one
     * and notify only if that time is bigger then some given time
     */
    private void notifyProgressOnDataReceived() {
        long time = System.currentTimeMillis();
        if (time - lastNotificationTime > NOTIFICATION_QUARTER_PERIOD_MS) {
            notifyProgress();
            lastNotificationTime = time;
        }
    }

    private synchronized void stop1() {
        if (bioRecorder != null) {
            bioRecorder.stop();
        }
        if (startFutureHandlingTask != null) {
            startFutureHandlingTask.cancel();
        }
        restartAvailableComportsTask();
        restartNotificationTask();
        notifyStateChange(null);
    }

    public void stop() {
        stop1();
        startMonitoring();
    }

    private synchronized void disconnectRecorder() {
        if (bioRecorder != null) {
            if (bioRecorder.isRecording()) {
                stop1();
            }
            if (!bioRecorder.disconnect()) {
                log.error("Failed to disconnect from comport: " + bioRecorder.getComportName());
            }
            bioRecorder = null;
        }
    }

    public void finalize() {
        disconnectRecorder();
        timer.cancel();
    }

    /**
     * Mask gives TRUE if electrode is DISCONNECTED,
     * FALSE if electrode is CONNECTED and
     * NULL if channel is disabled (or work in mode different from "input")
     *
     * @return disconnection bit mask for positive and negative electrode of every channel
     */
    public Boolean[] getLeadOffMask() {
        return leadOffBitMask;
    }

    public Integer getBatteryLevel() {
        return batteryLevel;
    }


    /**
     * If the comportName is not equal to any available port we add it to the list.
     * <p>
     * String full comparison is very "heavy" operation.
     * So instead we will compare only lengths and 2 last symbols...
     * That will be quick and good enough for our purpose
     *
     * @return available comports list with selected port included
     */
    public String[] getAvailableComports() {
        String[] availablePorts = BioRecorder.getAvailableComportNames();
        if (comportName == null || comportName.isEmpty()) {
            return availablePorts;
        }
        if (availablePorts.length == 0) {
            String[] resultantPorts = new String[1];
            resultantPorts[0] = comportName;
            return resultantPorts;
        }

        boolean isSelectedPortAvailable = false;
        for (String port : availablePorts) {
            if (port.length() == comportName.length()
                    && port.charAt(port.length() - 1) == comportName.charAt(comportName.length() - 1)
                    && port.charAt(port.length() - 2) == comportName.charAt(comportName.length() - 2)) {
                isSelectedPortAvailable = true;
                break;
            }
        }
        if (isSelectedPortAvailable) {
            return availablePorts;
        } else {
            String[] resultantPorts = new String[availablePorts.length + 1];
            resultantPorts[0] = comportName;
            System.arraycopy(availablePorts, 0, resultantPorts, 1, availablePorts.length);
            return resultantPorts;
        }
    }

    /**
     * EdfBioRecorderApp permits to add only ONE ProgressListener!
     * So if a new listener added the old one are automatically removed
     */
    public void addProgressListener(ProgressListener l) {
        if (l != null) {
            progressListener = l;
        }
    }

    /**
     * EdfBioRecorderApp permits to add only ONE StateChangeListener!
     * So if a new listener added the old one are automatically removed
     */
    public void addStateChangeListener(StateChangeListener l) {
        if (l != null) {
            stateChangeListener = l;
        }
    }

    /**
     * EdfBioRecorderApp permits to add only ONE AvailableComportsListener!
     * So if a new listener added the old one are automatically removed
     */
    public void addAvailableComportsListener(AvailableComportsListener l) {
        if (l != null) {
            availableComportsListener = l;
        }
    }

    public long getNumberOfWrittenDataRecords() {
        return numberOfWrittenDataRecords;
    }


    public String normalizeFilename(@Nullable String filename) {
        String FILE_EXTENSION = "bdf";
        String defaultFilename = new SimpleDateFormat("dd-MM-yyyy_HH-mm").format(new Date(System.currentTimeMillis()));

        if (filename == null || filename.isEmpty()) {
            return defaultFilename.concat(".").concat(FILE_EXTENSION);
        }
        filename = filename.trim();

        // if filename has no extension
        if (filename.lastIndexOf('.') == -1) {
            filename = filename.concat(".").concat(FILE_EXTENSION);
            return defaultFilename + "_" + filename;
        }
        // if  extension  match with given FILE_EXTENSIONS
        // (?i) makes it case insensitive (catch BDF as well as bdf)
        if (filename.matches("(?i).*\\." + FILE_EXTENSION)) {
            return defaultFilename + "_" + filename;
        }
        // If the extension do not match with  FILE_EXTENSION We need to replace it
        filename = filename.substring(0, filename.lastIndexOf(".") + 1).concat(FILE_EXTENSION);
        return defaultFilename + "_" + filename;
    }

    private void notifyProgress() {
        progressListener.onProgress();
    }

    private void notifyStateChange(Message stateChangeReason) {
        stateChangeListener.onStateChanged(stateChangeReason);
    }

    private void notifyAvailableComports(String[] availableComports) {
        availableComportsListener.onAvailableComportsChanged(availableComports);
    }

    class NullDataRecordStream implements DataRecordStream {
        @Override
        public void setHeader(DataHeader header) {
            // do nothing
        }

        @Override
        public void writeDataRecord(int[] dataRecord) {
            // do nothing
        }

        @Override
        public void close() {
            // do nothing
        }
    }

    class NullProgressListener implements ProgressListener {
        @Override
        public void onProgress() {
            // do nothing
        }
    }

    class NullStateChangeListener implements StateChangeListener {
        @Override
        public void onStateChanged(Message changeReason) {
            // do nothing
        }
    }

    class NullAvailableComportsListener implements AvailableComportsListener {
        @Override
        public void onAvailableComportsChanged(String[] availableComports) {
            // do nothing
        }
    }

    class NotificationTask extends TimerTask {
        public void run() {
            notifyProgress();
        }
    }

    class AvailableComportsTask extends TimerTask {
        public void run() {
            notifyAvailableComports(getAvailableComports());
        }
    }

    class ConnectionTask extends TimerTask {
        public void run() {
            try {
                createRecorder(comportName);
                startMonitoring();
                cancel();
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    private static Thread[] getAllThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        return threadSet.toArray(new Thread[threadSet.size()]);
    }

    private static void printAllThreads() {
        Thread[] threads = getAllThreads();
        System.out.println("Running threads:");
        for (Thread thread : threads) {
            System.out.println(thread.getName());
        }
    }
}
