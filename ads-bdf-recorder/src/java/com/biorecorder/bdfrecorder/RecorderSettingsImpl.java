package com.biorecorder.bdfrecorder;

import com.biorecorder.bdfrecorder.gui.RecorderSettings;
import com.biorecorder.bdfrecorder.recorder.*;

import java.util.ArrayList;
import java.util.List;

public class RecorderSettingsImpl implements RecorderSettings {
    private static final String[] ACCELEROMETER_COMMUTATORS = {"1 channel", "3 channels"};
    private final AppConfig appConfig;
    private final String[] availableComports;

    public RecorderSettingsImpl(AppConfig appConfig, String[] availableComports) {
        this.appConfig = appConfig;
        this.availableComports = availableComports;
    }

    AppConfig getAppConfig() {
        return appConfig;
    }

    @Override
    public String getDeviceType() {
        return appConfig.getRecorderConfig().getDeviceType().name();
    }

    @Override
    public void setDeviceType(String recorderType) {
        appConfig.getRecorderConfig().setDeviceType(RecorderType.valueOf(recorderType));
    }
    
    @Override
    public int getChannelsCount() {
        return appConfig.getRecorderConfig().getChannelsCount();
    }

    @Override
    public int getMaxFrequency() {
        return appConfig.getRecorderConfig().getSampleRate();
    }

    @Override
    public void setMaxFrequency(int frequency) {
        appConfig.getRecorderConfig().setSampleRate(RecorderSampleRate.valueOf(frequency));
    }

    @Override
    public String getPatientIdentification() {
        return appConfig.getPatientIdentification();
    }

    @Override
    public void setPatientIdentification(String patientIdentification) {
        appConfig.setPatientIdentification(patientIdentification);
    }

    @Override
    public String getRecordingIdentification() {
        return appConfig.getRecordingIdentification();
    }

    @Override
    public void setRecordingIdentification(String recordingIdentification) {
        appConfig.setRecordingIdentification(recordingIdentification);
    }

    @Override
    public boolean is50HzFilterEnabled(int channelNumber) {
        return appConfig.is50HzFilterEnabled(channelNumber);
    }

    @Override
    public void set50HzFilterEnabled(int channelNumber, boolean is50HzFilterEnabled) {
        appConfig.set50HzFilterEnabled(channelNumber, is50HzFilterEnabled);
    }

    @Override
    public String getComportName() {
        return appConfig.getComportName();
    }

    @Override
    public void setComportName(String comportName) {
        appConfig.setComportName(comportName);
    }

    @Override
    public String getDirToSave() {
        return appConfig.getDirToSave();
    }

    @Override
    public void setDirToSave(String dirToSave) {
        appConfig.setDirToSave(dirToSave);
    }

    @Override
    public String getFileName() {
        return appConfig.getFileName();
    }

    @Override
    public void setFileName(String fileName) {
        appConfig.setFileName(fileName);
    }
    

    @Override
    public boolean isChannelEnabled(int channelNumber) {
        return appConfig.getRecorderConfig().isChannelEnabled(channelNumber);
    }

    @Override
    public void setChannelEnabled(int channelNumber, boolean enabled) {
        appConfig.getRecorderConfig().setChannelEnabled(channelNumber, enabled);
    }

    @Override
    public String getChannelName(int channelNumber) {
        return appConfig.getRecorderConfig().getChannelName(channelNumber);
    }

    @Override
    public void setChannelName(int channelNumber, String name) {
        appConfig.getRecorderConfig().setChannelName(channelNumber, name);
    }
    
    @Override
    public int getChannelGain(int channelNumber) {
        return appConfig.getRecorderConfig().getChannelGain(channelNumber).getValue();
    }

    @Override
    public void setChannelGain(int channelNumber, int gainValue) {
        appConfig.getRecorderConfig().setChannelGain(channelNumber, RecorderGain.valueOf(gainValue));
    }


    @Override
    public String getChannelMode(int channelNumber) {
        return appConfig.getRecorderConfig().getChannelCommutator(channelNumber).name();
    }

    @Override
    public void setChannelMode(int channelNumber, String mode) {
        appConfig.getRecorderConfig().setChannelCommutator(channelNumber, RecorderCommutator.valueOf(mode));
    }


    @Override
    public int getChannelSampleRate(int channelNumber) {
        return appConfig.getRecorderConfig().getChannelSampleRate(channelNumber) / appConfig.getRecorderConfig().getChannelExtraDivider(channelNumber).getValue();
    }


    @Override
    public void setChannelFrequency(int channelNumber, int frequency) {
        int dividerValue = getMaxFrequency() / frequency;
        RecorderDivider[] dividers = RecorderDivider.values();
        ExtraDivider[] extraDividers = ExtraDivider.values();
        int maxDivider = dividers[dividers.length - 1].getValue();
        int maxExtraDivider = extraDividers[extraDividers.length - 1].getValue();

        ExtraDivider extraDivider;
        RecorderDivider recorderDivider;
        if(dividerValue <= maxDivider) {
            extraDivider = ExtraDivider.valueOf(1);
            recorderDivider = RecorderDivider.valueOf(dividerValue);
        } else {
            recorderDivider = RecorderDivider.valueOf(maxDivider);
            int extraDividerValue = dividerValue / maxDivider;
            if(extraDividerValue < 1) {
                extraDividerValue = 1;
            }
            if(extraDividerValue > maxExtraDivider) {
                extraDividerValue = maxExtraDivider;
            }
            extraDivider = ExtraDivider.valueOf(extraDividerValue);
        }
        appConfig.getRecorderConfig().setChannelDivider(channelNumber, recorderDivider);
        appConfig.getRecorderConfig().setChannelExtraDivider(channelNumber, extraDivider);
    }


    @Override
    public void setAccelerometerEnabled(boolean accelerometerEnabled) {
        appConfig.getRecorderConfig().setAccelerometerEnabled(accelerometerEnabled);
    }

    @Override
    public boolean isAccelerometerEnabled() {
        return appConfig.getRecorderConfig().isAccelerometerEnabled();
    }

    @Override
    public String getAccelerometerName() {
        return "Accelerometer";
    }

    @Override
    public int getAccelerometerFrequency() {
        return appConfig.getRecorderConfig().getAccelerometerSampleRate() / appConfig.getRecorderConfig().getAccelerometerExtraDivider().getValue();
    }

    @Override
    public void setAccelerometerFrequency(int frequency) {
        int dividerValue = getMaxFrequency() / frequency;
        int accDivider = appConfig.getRecorderConfig().getAccelerometerDivider().getValue();

        ExtraDivider[] extraDividers = ExtraDivider.values();
         int maxExtraDivider = extraDividers[extraDividers.length - 1].getValue();


        int extraDividerValue = dividerValue / accDivider;
        if(extraDividerValue < 1) {
            extraDividerValue = 1;
        }
        if(extraDividerValue > maxExtraDivider) {
            extraDividerValue = maxExtraDivider;
        }

        appConfig.getRecorderConfig().setAccelerometerExtraDivider(ExtraDivider.valueOf(extraDividerValue));
    }

    @Override
    public String getAccelerometerMode() {
        if(appConfig.getRecorderConfig().isAccelerometerOneChannelMode()) {
            return ACCELEROMETER_COMMUTATORS[0];
        }
        return ACCELEROMETER_COMMUTATORS[1];
    }

    @Override
    public void setAccelerometerMode(String mode) {
        if(mode != null && mode.equals(ACCELEROMETER_COMMUTATORS[0])) {
            appConfig.getRecorderConfig().setAccelerometerOneChannelMode(true);
        } else {
            appConfig.getRecorderConfig().setAccelerometerOneChannelMode(false);
        }

    }

    @Override
    public boolean isBatteryVoltageChannelDeletingEnable() {
        return appConfig.getRecorderConfig().isBatteryVoltageChannelDeletingEnable();
    }

    @Override
    public void setBatteryVoltageChannelDeletingEnable(boolean isEnable) {
        appConfig.getRecorderConfig().setBatteryVoltageChannelDeletingEnable(isEnable);
    }

    @Override
    public boolean isDurationOfDataRecordAdjustable() {
        return appConfig.getRecorderConfig().isDurationOfDataRecordAdjustable();
    }


    @Override
    public void setDurationOfDataRecordAdjustable(boolean isAdjustable) {
        appConfig.getRecorderConfig().setDurationOfDataRecordAdjustable(isAdjustable);

    }

    @Override
    public boolean isLabStreamingEnabled() {
        return appConfig.isLabStreamingEnabled();
    }

    @Override
    public void setLabStreamingEnabled(boolean isEnable) {
        appConfig.setLabStreamingEnabled(isEnable);

    }

    @Override
    public double getDataRecordDuration() {
        return appConfig.getRecorderConfig().getDurationOfDataRecord();
    }

    @Override
    public void setDataRecordDuration(double duration) {
        appConfig.getRecorderConfig().setDurationOfDataRecord(duration);
    }

    @Override
    public String[] getAccelerometerAvailableModes() {
        return ACCELEROMETER_COMMUTATORS;
    }

    @Override
    public String[] getAvailableDeviseTypes() {
        RecorderType[] devises = RecorderType.values();
        String[] names = new String[devises.length];
        for (int i = 0; i < devises.length; i++) {
            names[i] = devises[i].name();
        }
        return names;
    }

    @Override
    public  Integer[] getAvailableMaxFrequencies() {
        RecorderSampleRate[] sampleRates = RecorderSampleRate.values();
        Integer[] values = new Integer[sampleRates.length];

        for (int i = 0; i < sampleRates.length; i++) {
            values[i] = sampleRates[i].getValue();
        }
        return values;
    }

    @Override
    public Integer[] getChannelsAvailableFrequencies() {
        RecorderDivider[] dividers = RecorderDivider.values();
        int maxDivider = dividers[dividers.length - 1].getValue();
        List<Integer> frequencies = new ArrayList<>();

        for (int i = 0; i < dividers.length; i++) {
            frequencies.add(getMaxFrequency() / dividers[i].getValue());
        }

        // remove last element (that coincide with extra_divider = 1)
        frequencies.remove(frequencies.size() - 1);

        ExtraDivider[] extraDividers = ExtraDivider.values();
        for (int i = 0; i < extraDividers.length; i++) {
            frequencies.add(getMaxFrequency() / (maxDivider * extraDividers[i].getValue()));
        }

        Integer[] frequenciesArr = new Integer[frequencies.size()];
        return frequencies.toArray(frequenciesArr);
    }

    @Override
    public Integer[] getAccelerometerAvailableFrequencies() {
        RecorderDivider accDivider = appConfig.getRecorderConfig().getAccelerometerDivider();

        List<Integer> frequencies = new ArrayList<>();
        frequencies.add(getMaxFrequency() / accDivider.getValue());

        // remove last element (that coincide with extra_divider = 1)
        frequencies.remove(frequencies.size() - 1);

        ExtraDivider[] extraDividers = ExtraDivider.values();
        for (int i = 0; i < extraDividers.length; i++) {
            frequencies.add(getMaxFrequency() / (accDivider.getValue() * extraDividers[i].getValue()));
        }

        Integer[] frequenciesArr = new Integer[frequencies.size()];
        return frequencies.toArray(frequenciesArr);
    }

    @Override
    public  Integer[] getChannelsAvailableGains() {
        RecorderGain[] gains = RecorderGain.values();
        Integer[] values = new Integer[gains.length];
        for (int i = 0; i < gains.length; i++) {
            values[i] = gains[i].getValue();
        }
        return values;
    }

    @Override
    public String[] getChannelsAvailableModes() {
        RecorderCommutator[] modes = RecorderCommutator.values();
        String[] names = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            names[i] = modes[i].name();
        }
        return names;
    }

    @Override
    public String[] getAvailableComports() {
        return availableComports;
    }
}
