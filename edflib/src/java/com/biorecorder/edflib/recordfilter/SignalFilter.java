package com.biorecorder.edflib.recordfilter;

import com.biorecorder.filters.digitalfilter.IntDigitalFilter;
import com.biorecorder.filters.digitalfilter.IntMovingAverage;
import com.biorecorder.edflib.DataHeader;
import com.biorecorder.edflib.FormatVersion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Permits to  add digital filters to any signal and realize corresponding
 * transformation  with the data samples belonging to the signals
 */
public class SignalFilter extends FilterRecordStream {
    private Map<Integer, List<NamedFilter>> signalsToFilters = new HashMap<Integer, List<NamedFilter>>();
    private int[] offsets; // gain and offsets to convert dig value to phys one
    public SignalFilter(com.biorecorder.edflib.DataRecordStream outStream) {
        super(outStream);
    }

    @Override
    public void setHeader(DataHeader header) {
        super.setHeader(header);
        offsets = new int[header.numberOfSignals()];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = header.getSignalOffset(i);
        }
    }

    /**
     * Indicates that the given filter should be applied to the samples
     * belonging to the given signal. This method can be called only
     * before setHeader()!
     *
     * @param signalFilter digital filter that will be applied to the samples
     * @param signalNumber number of the signal to whose samples
     *                     the filter should be applied to. Numbering starts from 0.
     */
    public void addSignalFilter(int signalNumber, IntDigitalFilter signalFilter, String filterName) {
        List<NamedFilter> signalFilters = signalsToFilters.get(signalNumber);
        if(signalFilters == null) {
            signalFilters = new ArrayList<NamedFilter>();
            signalsToFilters.put(signalNumber, signalFilters);
        }
        signalFilters.add(new NamedFilter(signalFilter, filterName));
    }


    public String getSignalFiltersName(int signalNumber) {
        StringBuilder name = new StringBuilder("");
        List<NamedFilter> signalFilters = signalsToFilters.get(signalNumber);
        if(signalFilters != null) {
            for (NamedFilter filter : signalFilters) {
                name.append(filter.getFilterName()).append(";");
            }
        }
        return name.toString();
    }

    @Override
    public DataHeader getOutConfig() {
        DataHeader outConfig = new DataHeader(inConfig);
        for (int i = 0; i < outConfig.numberOfSignals(); i++) {
            String prefilter = getSignalFiltersName(i);
            if(inConfig.getPrefiltering(i) != null && ! inConfig.getPrefiltering(i).isEmpty()) {
                prefilter = inConfig.getPrefiltering(i) + ";" +getSignalFiltersName(i);
            }
            outConfig.setPrefiltering(i, prefilter);
        }
        return outConfig;
    }

    @Override
    public void writeDataRecord(int[] inputRecord)  {
        int signalStart = 0;
        for (int signalNumber = 0; signalNumber < inConfig.numberOfSignals(); signalNumber++) {
            int signalSamples = inConfig.getNumberOfSamplesInEachDataRecord(signalNumber);
            List<NamedFilter> signalFilters = signalsToFilters.get(signalNumber);
            if(signalFilters != null) {
                for (int i = 0; i < signalSamples; i++) {
                    // for filtering we use (digValue + offset) that is proportional physValue !!!
                    int digValue = inputRecord[signalStart + i] + offsets[signalNumber];
                    for (IntDigitalFilter filter : signalFilters) {
                        digValue = filter.filteredValue(digValue);
                    }
                    outRecord[signalStart + i] = (int)(digValue - offsets[signalNumber]);
                }
            } else {
                System.arraycopy(inputRecord, signalStart, outRecord, signalStart, signalSamples);
            }
            signalStart += signalSamples;
        }
        sendData(outRecord);
    }

    class NamedFilter implements IntDigitalFilter {
        private IntDigitalFilter filter;
        private String filterName;

        public NamedFilter(IntDigitalFilter filter, String filterName) {
            this.filter = filter;
            this.filterName = filterName;
        }

        @Override
        public int getFilterLength() {
            return filter.getFilterLength();
        }

        @Override
        public int filteredValue(int inputValue) {
            return filter.filteredValue(inputValue);
        }

        public String getFilterName() {
            return filterName;
        }
    }

    /**
     * Unit Test. Usage Example.
     */
    public static void main(String[] args) {

        // 0 channel 1 sample, 1 channel 6 samples, 2 channel 2 samples
        int[] dataRecord = {1,  2,4,8,6,0,8,  3,5};

        DataHeader dataConfig = new DataHeader(FormatVersion.BDF_24BIT);

        dataConfig.addSignal(1);
        dataConfig.addSignal(6);
        dataConfig.addSignal( 2);


        // Moving average filter to channel 1

        // expected dataRecords
        int[] expectedDataRecord1 = {1,  2,3,6,7,3,4,  3,5};
        int[] expectedDataRecord2 = {1,  5,3,6,7,3,4,  3,5};
        List<int[]> expectedRecords = new ArrayList<>(2);
        expectedRecords.add(expectedDataRecord1);
        expectedRecords.add(expectedDataRecord2);

        SignalFilter recordFilter = new SignalFilter(new TestStream(expectedRecords));
        recordFilter.addSignalFilter(1, new IntMovingAverage(2), "movAvg:2");
        recordFilter.setHeader(dataConfig);

        // send 4 records and get 4 resultant records
        recordFilter.writeDataRecord(dataRecord);
        recordFilter.writeDataRecord(dataRecord);
        recordFilter.writeDataRecord(dataRecord);
        recordFilter.writeDataRecord(dataRecord);
    }

}
