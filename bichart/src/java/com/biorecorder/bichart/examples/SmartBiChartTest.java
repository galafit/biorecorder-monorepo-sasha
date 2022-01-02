package com.biorecorder.bichart.examples;

import com.biorecorder.bichart.BiChart;
import com.biorecorder.bichart.ChartPanel;
import com.biorecorder.bichart.XYSeries;
import com.biorecorder.bichart.traces.LineTracePainter;

import javax.swing.*;
import java.awt.*;

public class SmartBiChartTest extends JFrame {
    public SmartBiChartTest() {
        int width = 400;
        int height = 500;

        int[] data1 = new int[10];
        int[] data2 = new int[10];

        for (int i = 0; i < data1.length; i++) {
            data1[i] = i;
        }

        for (int i = 0; i < data2.length; i++) {
            data2[i] = i;
        }


        XYSeries xySeries1 = new XYSeries(new int[0], new int[0]);
        XYSeries xySeries2 = new XYSeries(0, 1, new int[0]);
        xySeries1 = new XYSeries(data1, data1);
        xySeries2 = new XYSeries(0, 1, data1);
        XYSeries xySeries3 = new XYSeries(0, 1, data2);


        BiChart smartBiChart = new BiChart(false, true);
        smartBiChart.addChartTrace("No Regular", xySeries1, new LineTracePainter());
        smartBiChart.addChartStack();
        smartBiChart.addChartTrace("Regular", xySeries2, new LineTracePainter(), true, false);

        //  chartPanel.addNavigatorTrace("zero", new XYData(new int[0]), new LineTracePainter() );
        // chartPanel.addNavigatorTrace("No Regular", xyData1, new LineTracePainter() );
        // chartPanel.addNavigatorStack();
        smartBiChart.addNavigatorTrace("No Regular", xySeries1, new LineTracePainter());

        ChartPanel chartPanel = new ChartPanel(smartBiChart);
        chartPanel.setPreferredSize(new Dimension(width, height));
        add(chartPanel, BorderLayout.CENTER);
        pack();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        // addKeyListener(chartPanel);
        setLocationRelativeTo(null);
        setVisible(true);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.out.println(e);
        }
        int size = 10;
        for (int i = 0; i < 6; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println(e);
            }
            int[] data = new int[size];
            for (int j = 0; j < data.length; j++) {
                data[j] = data1.length + i * size + j;
            }
            System.out.println(i + " "+ xySeries1.size() + " "+ xySeries2.size());
            XYSeries unregData = new XYSeries(data, data);
            XYSeries regData = new XYSeries(data[0], 1, data);
            xySeries1.appendData(unregData);
            xySeries2.appendData(regData);
            smartBiChart.dataAppended();
            chartPanel.repaint();
        }
        chartPanel.repaint();
    }

    public static void main(String[] args) {
        new SmartBiChartTest();
    }
}

