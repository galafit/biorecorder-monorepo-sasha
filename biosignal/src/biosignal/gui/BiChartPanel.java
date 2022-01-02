package biosignal.gui;

import biosignal.filter.XYData;
import com.biorecorder.bichart.*;
import com.biorecorder.bichart.axis.Orientation;
import com.biorecorder.bichart.traces.TracePainter;
import com.biorecorder.datalyb.datatable.DataTable;

import javax.swing.*;
import java.awt.*;

public class BiChartPanel extends JPanel {
    BiChart biChart;
    ChartPanel chartPanel;

    public BiChartPanel(boolean isDateTime, ProcessingConfig processingConfig, boolean scrollsAtEnd) {
        biChart = new BiChart(isDateTime, processingConfig, scrollsAtEnd);
        chartPanel = new ChartPanel(biChart);
        setLayout(new BorderLayout());
        add(chartPanel);
    }


    public BiChartPanel(boolean isDateTime, boolean scrollsAtEnd) {
       this(isDateTime, new ProcessingConfig(), scrollsAtEnd);
    }

    public void addChartStack() {
        biChart.addChartStack();
    }

    public void addChartTrace(String name, XYData data, GroupingApproximation groupingApproximation, TracePainter tracePainter, boolean isXOpposite,  boolean isYOpposite) {
        biChart.addChartTrace(name, convertData(data, groupingApproximation), tracePainter, isXOpposite, isYOpposite);
    }

    public void addNavigatorStack() {
        biChart.addNavigatorStack();
    }

    public void addNavigatorTrace(String name, XYData data, GroupingApproximation groupingApproximation,TracePainter tracePainter) {
        biChart.addNavigatorTrace(name, convertData(data, groupingApproximation), tracePainter);
    }

    public double[] getChartXRange() {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (int i = 0; i < 2; i++) {
            min = Math.min(min, biChart.getChartXMin(i));
            max = Math.max(max, biChart.getChartXMax(i));
        }
        double[] range = {min, max};
        return range;
    }


    private XYSeries convertData(XYData xyData, GroupingApproximation groupingApproximation) {
        DataTable dt = xyData.getDataTable();
        XYSeries xySeries = new XYSeries(dt);
        xySeries.setGroupingApproximationY(groupingApproximation);
        return xySeries;
    }

    @Override
    public void setPreferredSize(Dimension preferredSize) {
        super.setPreferredSize(preferredSize);
        chartPanel.setPreferredSize(preferredSize);
    }

    public void dataAppended() {
        biChart.dataAppended();
    }

    public void autoScale() {
        biChart.autoScaleX();
        biChart.autoScaleChartY();
        biChart.autoScaleNavigatorY();
    }
}
