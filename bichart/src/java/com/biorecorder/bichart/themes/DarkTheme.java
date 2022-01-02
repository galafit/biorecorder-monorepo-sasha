package com.biorecorder.bichart.themes;

import com.biorecorder.bichart.ChartConfig;
import com.biorecorder.bichart.BiChartConfig;
import com.biorecorder.bichart.axis.AxisConfig;
import com.biorecorder.bichart.graphics.BColor;
import com.biorecorder.bichart.ScrollConfig;

/**
 * Created by galafit on 24/2/19.
 */
public class DarkTheme {
    public static final BColor CYAN = new BColor(0, 200, 220);
    public static final BColor BLUE = new BColor(100, 120, 250);
    public static final BColor MAGENTA = new BColor(165, 80, 220);
    public static final BColor GREEN = new BColor(110, 250, 110);
    public static final BColor RED = new BColor(250, 64, 82);
    public static final BColor ORANGE = new BColor(200, 80, 0);//new BColor(173, 105, 49);
    public static final BColor YELLOW = new BColor(252, 177, 48);
    public static final BColor GRAY = new BColor(180, 180, 200);
    public static final BColor PINK = new BColor(255, 50, 200);//new BColor(255, 60, 130); //new BColor(250, 0, 200);
    public static final BColor GOLD = new BColor(190, 140, 110);
    private static final BColor[] COLORS = {BLUE, RED, GRAY, MAGENTA, ORANGE, YELLOW, GREEN, CYAN, PINK, GOLD};

    private static final int X_MARK_SIZE = 4;
    private static final int Y_MARK_SIZE = 6;

    private static final BColor BG_COLOR = new BColor(30, 30, 40);
    private static final BColor MARGIN_COLOR = new BColor(20, 20, 20);

    private static final BColor TEXT_COLOR = new BColor(160, 140, 110);
    private static final BColor AXIS_COLOR = new BColor(100, 86, 60);
    private static final BColor GRID_COLOR = new BColor(70, 65, 45);
    private static final BColor CROSSHAIR_COLOR = BColor.WHITE_OBSCURE;
    private static final BColor SCROLL_COLOR = BColor.WHITE;


    public static ChartConfig getChartConfig() {
        AxisConfig xAxisConfig = new AxisConfig();
        xAxisConfig.setColors(AXIS_COLOR, TEXT_COLOR, GRID_COLOR, GRID_COLOR);
        xAxisConfig.setTickMarkSize(X_MARK_SIZE, 0);
        xAxisConfig.setCrosshairLineColor(CROSSHAIR_COLOR);
        xAxisConfig.setAxisLineWidth(1);
        //xAxisConfig.setMinorTickIntervalCount(3);

        AxisConfig yAxisConfig = new AxisConfig();
        yAxisConfig.setColors(AXIS_COLOR, TEXT_COLOR, GRID_COLOR, GRID_COLOR);
        yAxisConfig.setTickMarkSize(Y_MARK_SIZE, 0);
        yAxisConfig.setCrosshairLineColor(CROSSHAIR_COLOR);
        yAxisConfig.setAxisLineWidth(0);
        //yAxisConfig.setMinorTickIntervalCount(3);

        ChartConfig chartConfig = new ChartConfig();
        chartConfig.setTraceColors(COLORS);
        chartConfig.setBackgroundColor(BG_COLOR);
        chartConfig.setMarginColor(MARGIN_COLOR);
        chartConfig.getTitleConfig().setTextColor(TEXT_COLOR);
        chartConfig.setYAxisConfig(yAxisConfig);
        chartConfig.setXAxisConfig(xAxisConfig);
        chartConfig.getLegendConfig().setBackgroundColor(BG_COLOR);
        return chartConfig;
    }

    public static BiChartConfig getNavigableChartConfig() {
        BColor navigatorBgColor = MARGIN_COLOR;
        BColor navigatorMarginColor = navigatorBgColor;
        BColor bgColor = navigatorBgColor;

        ChartConfig navigatorConfig = getChartConfig();
        navigatorConfig.setBackgroundColor(navigatorBgColor);
        navigatorConfig.setMarginColor(navigatorMarginColor);
        navigatorConfig.getTitleConfig().setTextColor(TEXT_COLOR);
        navigatorConfig.getLegendConfig().setBackgroundColor(navigatorBgColor);
        navigatorConfig.getYAxisConfig().setTickLabelOutside(false);

        ScrollConfig scrollConfig = new ScrollConfig();
        scrollConfig.setColor(SCROLL_COLOR);

        ChartConfig chartConfig1 = getChartConfig();
        chartConfig1.getYAxisConfig().setTickLabelOutside(false);
        BiChartConfig biChartConfig = new BiChartConfig(chartConfig1, navigatorConfig, scrollConfig);
        biChartConfig.setBackgroundColor(bgColor);
        return biChartConfig;
    }
}
