package com.biorecorder.edflib;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EdfWriter permits to write digital or physical samples
 * from multiple measuring channels to  EDF or BDF File.
 * Every channel (signal) has its own sample frequency.
 * <p>
 * This class is  partially thread safe! It means that all methods
 * writing data must be called from the same thread.
 * But method close() may be called from a different (usually GUI) thread!
 * <p>
 * If the file does not exist it will be created.
 * Already existing file with the same name
 * will be silently overwritten without advance warning!!
 * <p>
 * When we write <b>physical</b>  samples
 * every physical (floating point) sample
 * is converted to the corresponding digital (int) one
 * using physical maximum, physical minimum, digital maximum and digital minimum of the signal.
 * <p>
 * Every digital (int) value is converted
 * to 2 LITTLE_ENDIAN ordered bytes (16 bits) for EDF files or
 * to 3 LITTLE_ENDIAN ordered bytes (24 bits) for BDF files
 * and in this form written to the file.
 */
public class EdfFileWriter implements DataRecordStream {
    private final String CLOSED_MSG = "File was closed. Data can not be written";
    private final String NUMBER_OF_SIGNALS_ZERO = "Number of signals is 0. Data can not be written";
    private final String RECORD_INCOMPLETE = "Last data record is incomplete. Incorrect use of method: writeSamples/writePhysicalSamples.";
    private static final int MAX_RECORD_NUMBER = 100000000; // possible edf record number is 8 digits => 99999999

    private DataHeader header;
    private final File file;
    private volatile boolean isClosed = false;
    private volatile boolean isWriting = false;
    private volatile long sampleCount;

    private final FileOutputStream fileOutputStream;
    private int recordSize; // helper field to avoid unnecessary calculations
    private int currentSignal;

    public EdfFileWriter(File file) throws FileNotFoundException {
        this.file = file;
        fileOutputStream = new FileOutputStream(file);
    }

    /**
     * Creates EdfWriter to write data samples to the file represented by
     * the specified File object. EdfHeader object specifies the type of the file
     * (EDF_16BIT or BDF_24BIT) and provides all necessary information
     * for the file header record.
     *
     * @param file   the file to be opened for writing
     * @param header object containing all necessary information for the header record
     * @throws FileNotFoundException if the file exists but is a directory rather
     * than a regular file, does not exist but cannot be created,
     * or cannot be opened for any other reason
     */
    public EdfFileWriter(File file, DataHeader header) throws FileNotFoundException {
        this.header = new DataHeader(header);
        this.file = file;
        fileOutputStream = new FileOutputStream(file);
        recordSize = header.getRecordSize();
        this.header.setNumberOfDataRecords(-1);
    }

    public File getFile() {
        return file;
    }

    @Override
    public void setHeader(DataHeader header) throws IllegalArgumentException {
        if(this.header != null) {
            if(this.header.getFormatVersion() != header.getFormatVersion()) {
                String errMsg = "File format version: " + this.header.getFormatVersion() + " new format version: " + header.getFormatVersion();
                throw new IllegalArgumentException(errMsg);
            }

            if(this.header.numberOfSignals() != header.numberOfSignals()) {
                String errMsg = "Number of signals: " + this.header.numberOfSignals() + " new number of signals: " + header.numberOfSignals();
                throw new IllegalArgumentException(errMsg);
            }

            for (int i = 0; i < header.numberOfSignals(); i++) {
                if(this.header.getNumberOfSamplesInEachDataRecord(i) != header.getNumberOfSamplesInEachDataRecord(i)) {
                    String errMsg = "Signal: " + i + ",  number of samples: " + this.header.getNumberOfSamplesInEachDataRecord(i) + ",  new number of samples: " + header.getNumberOfSamplesInEachDataRecord(i);
                    throw new IllegalArgumentException(errMsg);
                }
            }
        }
        this.header = new DataHeader(header);
        recordSize = header.getRecordSize();
        this.header.setNumberOfDataRecords(-1);
    }

    public DataHeader getHeader() {
        return new DataHeader(header);
    }

    /**
     * Writes n "raw" digital (integer) samples belonging to one signal.
     * The number of written samples : n = (sample frequency of the signal) * (duration of DataRecord).
     * <p>
     * Call this method for every signal (channel) in the file. The order is important!
     * When there are 4 signals,  the order of calling this method must be:
     * <br>samples belonging to signal 0, samples belonging to signal 1, samples belonging to signal 2, samples belonging to  signal 3,
     * <br>samples belonging to signal 0, samples belonging to signal 1, samples belonging to signal 2, samples belonging to  signal 3,
     * <br> ... etc.
     * @param digitalSamples data array with digital samples belonging to one signal
     * @throws IORuntimeException if an I/O error occurs
     * @throws IllegalStateException if file was close,
     * or number of signals for that file is 0.
     */
    public void writeSamples(int[] digitalSamples) throws IORuntimeException, IllegalStateException {
        if(header.numberOfSignals() == 0) {
            throw new IllegalStateException(NUMBER_OF_SIGNALS_ZERO);
        }
        int sn = header.getNumberOfSamplesInEachDataRecord(currentSignal);
        int digMin = header.getDigitalMin(currentSignal);
        int digMax = header.getDigitalMax(currentSignal);
        for (int i = 0; i < sn; i++) {
            if(digitalSamples[i] < digMin) {
                digitalSamples[i] = digMin;
            }
            if(digitalSamples[i] > digMax) {
                digitalSamples[i] = digMax;
            }
        }
        writeDataToFile(digitalSamples, sn);
        currentSignal++;
        if(currentSignal == header.numberOfSignals()) {
            currentSignal = 0;
        }
    }

    /**
     * Writes the entire data record (data pack) containing "raw" digital samples from all signals
     * starting with n_0 samples of signal 0, n_1 samples of signal 1, n_2 samples of signal 2, etc.
     * <br>
     * Where number of samples of signal i: n_i = (sample frequency of the signal_i) * (duration of data record).
     * @param digitalDataRecord array with digital (int) samples from all signals
     * @throws IORuntimeException if an I/O error occurs
     * @throws IllegalStateException if file was close,
     * if number of signals for that file is 0,
     * or last data record is incomplete (due to
     * the fact that samples from some channels were not recorded by methods
     * writeSamples/writePhysicalSamples).
     */
    @Override
    public void writeDataRecord(int[] digitalDataRecord) throws IORuntimeException, IllegalStateException {
        if(header.numberOfSignals() == 0) {
            throw new IllegalStateException(NUMBER_OF_SIGNALS_ZERO);
        }
        if(currentSignal != 0) {
            throw new IllegalStateException(RECORD_INCOMPLETE);
        }
        int counter = 0;
        for (int signal = 0; signal < header.numberOfSignals(); signal++) {
            int sn = header.getNumberOfSamplesInEachDataRecord(signal);
            int digMin = header.getDigitalMin(signal);
            int digMax = header.getDigitalMax(signal);
            for (int i = 0; i < sn; i++) {
                if(digitalDataRecord[counter] < digMin) {
                    digitalDataRecord[counter] = digMin;
                }
                if(digitalDataRecord[counter] > digMax) {
                    digitalDataRecord[counter] = digMax;
                }
                counter++;
            }
        }
        writeDataToFile(digitalDataRecord, recordSize);
    }


    /**
     * Writes n physical samples (uV, mA, Ohm) belonging to one signal.
     * The number of written samples : n = (sample frequency of the signal) * (duration of data record).
     * <p>
     * The physical samples will be converted to digital samples using the
     * values of physical maximum, physical minimum, digital maximum and digital minimum.
     * <p>
     * Call this method for every signal (channel) in the file. The order is important!
     * When there are 4 signals,  the order of calling this method must be:
     * <br>samples belonging to signal 0, samples belonging to signal 1, samples belonging to signal 2, samples belonging to  signal 3,
     * <br>samples belonging to signal 0, samples belonging to signal 1, samples belonging to signal 2, samples belonging to  signal 3,
     * <br> ... etc.
     * @param physicalSamples data array with physical (double) samples belonging to one signal
     * @throws IORuntimeException if an I/O error occurs
     * @throws IllegalStateException if file was close,
     * or number of signals for that file is 0
     */
    public void writePhysicalSamples(double[] physicalSamples) throws IORuntimeException, IllegalStateException {
        int ns = header.getNumberOfSamplesInEachDataRecord(currentSignal);
        int digSamples[] = new int[ns];
        for (int i = 0; i < ns; i++) {
            digSamples[i] = header.physicalValueToDigital(currentSignal, physicalSamples[i]);
        }
        writeSamples(digSamples);
    }

    /**
     * Writes the entire data record (data pack) containing physical samples (uV, mA, Ohm) from all signals
     * starting with n_0 samples of signal 0, n_1 samples of signal 1, n_2 samples of signal 2, etc.
     * <br>
     * Where number of samples of signal i: n_i = (sample frequency of the signal_i) * (duration of data record).
     * <p>
     * The physical samples will be converted to digital samples using the
     * values of physical maximum, physical minimum, digital maximum and digital minimum.
     * @param physicalDataRecord array with physical (double) samples from all signals
     * @throws IORuntimeException if an I/O error occurs
     * @throws IllegalStateException if file was close,
     * if number of signals for that file is 0,
     * or last data record is incomplete (due to
     * the fact that samples from some channels were not recorded by methods
     * writeSamples/writePhysicalSamples).
     */
    public void writePhysicalDataRecord(double[] physicalDataRecord) throws IORuntimeException, IllegalStateException {
        int digSamples[] = new int[recordSize];
        int counter = 0;
        for (int signal = 0; signal < header.numberOfSignals(); signal++) {
            int sn = header.getNumberOfSamplesInEachDataRecord(signal);
            for (int i = 0; i < sn; i++) {
                digSamples[counter] = header.physicalValueToDigital(signal, physicalDataRecord[counter]);
                counter++;
            }
        }
        writeDataRecord(digSamples);
    }

    /**
     * Closes this Edf/Bdf file for writing data records and releases any system resources associated with
     * it. This method MUST be called after finishing writing data records.
     * Failing to do so will cause unnessesary memory usage and corrupted and incomplete data writing.
     *
     * @throws IORuntimeException  if an I/O  occurs
     */
    @Override
    public void close() throws IORuntimeException {
        if(isClosed) {
            return;
        }
        isClosed = true;
        while(isWriting) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            writeHeaderToFile();
        } finally {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                throw new IORuntimeException(e);
            }
        }
    }

    /**
     * Gets the number of received data records (data packages).
     * @return number of received data records
     */
    public int getNumberOfReceivedDataRecords() {
        if(recordSize == 0) {
            return 0;
        }
        return (int) (sampleCount / recordSize);
    }

    public String getWritingInfo() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Start recording time = "  + dateFormat.format(new Date(header.getRecordingStartTimeMs())) + "\n");
        stringBuilder.append("Duration of data records(sec) = " + header.getDurationOfDataRecordSec()+ "\n");
        stringBuilder.append("Number of data records = " + header.getNumberOfDataRecords());
        return stringBuilder.toString();
    }

    public boolean isClosed() {
        return isClosed;
    }

    private void writeDataToFile(int[] samples, int length) throws IllegalStateException, IORuntimeException {
        isWriting = true;
        if(isClosed) {
            isWriting = false;
            throw new IllegalStateException(CLOSED_MSG);
        }
        try{
            if(sampleCount == 0) {
                long firstRecordTime = System.currentTimeMillis();
                if(header.getRecordingStartTimeMs() <= 0) {
                    header.setRecordingStartTimeMs(firstRecordTime);
                }
                writeHeaderToFile();
            }
            sampleCount += length;
            int numberOfBytesPerSample = header.getNumberOfBytesPerSample();
            byte[] byteArray = new byte[numberOfBytesPerSample * length];
            EndianBitConverter.intArrayToLittleEndianByteArray(samples, 0, byteArray, 0, length, numberOfBytesPerSample);
            try {
                fileOutputStream.write(byteArray);
            } catch (IOException e) {
                throw new IORuntimeException(e);
            }
        } finally {
            isWriting = false;
        }
    }

    private void writeHeaderToFile() throws IORuntimeException {
        int numberOfReceivedRecords = getNumberOfReceivedDataRecords();
        if(numberOfReceivedRecords > 0 && numberOfReceivedRecords < MAX_RECORD_NUMBER) {
            header.setNumberOfDataRecords(numberOfReceivedRecords);
        }

        FileChannel fileChannel = fileOutputStream.getChannel();
        try {
            fileChannel.position(0);
            fileOutputStream.write(new HeaderRecord(header).getBytes());
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }
}