package com.biorecorder.bichart.axis;

        import com.biorecorder.bichart.graphics.BText;
        import com.biorecorder.bichart.graphics.TextMetric;
        import com.biorecorder.bichart.graphics.*;
        import com.biorecorder.bichart.scales.*;
        import com.biorecorder.bichart.utils.StringUtils;
        import com.biorecorder.datalyb.list.IntArrayList;

        import java.util.ArrayList;
        import java.util.List;

class AxisPainter {
    public static int MIN_TICK_COUNT = 11;
    private static final int MAX_TICKS_COUNT = 500; // if bigger it means that there is some error

    private AxisConfig config;
    private OrientationFunctions orientationFunctions;
    private TickLabelFormat tickLabelPrefixAndSuffix;

    private List<BText> tickLabels = new ArrayList<>();
    private IntArrayList tickPositions = new IntArrayList();
    private IntArrayList minorTickPositions = new IntArrayList();
    private BText titleText;
    private BLine axisLine;
    private int axisLength;
    private int widthOut;

    public AxisPainter(Scale scale, AxisConfig axisConfig, OrientationFunctions orientationFunctions, RenderContext renderContext, String title, double tickInterval, TickLabelFormat tickLabelPrefixAndSuffix, boolean isRoundingEnabled) {
        this.config = axisConfig;
        this.orientationFunctions = orientationFunctions;
        axisLength = (int) scale.length();
        axisLine = orientationFunctions.createAxisLine((int)Math.round(scale.getStart()), (int)Math.round(scale.getEnd()));
        this.tickLabelPrefixAndSuffix = tickLabelPrefixAndSuffix;
        createAxisElements(renderContext, scale, tickInterval, isRoundingEnabled, title);
    }

    public int getWidthOut() {
        return widthOut;
    }

    public void drawCrosshair(BCanvas canvas, BRectangle area, int position) {
        canvas.save();
        orientationFunctions.translateCanvas(canvas, area);
        canvas.setColor(config.getCrosshairLineColor());
        canvas.setStroke(config.getCrosshairLineWidth(), config.getCrosshairLineDashStyle());
        canvas.drawLine(orientationFunctions.createGridLine(position, area));
        canvas.restore();
    }

    public void drawGrid(BCanvas canvas, BRectangle area) {
        if (isTooShort()) {
            return;
        }
        canvas.save();
        orientationFunctions.translateCanvas(canvas, area);

        canvas.setColor(config.getGridColor());
        canvas.setStroke(config.getGridLineWidth(), config.getGridLineDashStyle());
        for (int i = 0; i < tickPositions.size(); i++) {
            canvas.drawLine(orientationFunctions.createGridLine(tickPositions.get(i), area));
        }

        canvas.setColor(config.getMinorGridColor());
        canvas.setStroke(config.getMinorGridLineWidth(), config.getMinorGridLineDashStyle());
        for (int i = 0; i < minorTickPositions.size(); i++) {
            canvas.drawLine(orientationFunctions.createGridLine(minorTickPositions.get(i), area));
        }

        canvas.restore();
    }

    public void drawAxis(BCanvas canvas, BRectangle area) {
        canvas.save();
        orientationFunctions.translateCanvas(canvas, area);

        if (!isTooShort()) {
            if (config.getTickMarkInsideSize() > 0 || config.getTickMarkOutsideSize() > 0) {
                canvas.setColor(config.getTickMarkColor());
                canvas.setStroke(config.getTickMarkWidth(), DashStyle.SOLID);
                for (int i = 0; i < tickPositions.size(); i++) {
                    canvas.drawLine(orientationFunctions.createTickLine(tickPositions.get(i), config.getAxisLineWidth(),config.getTickMarkInsideSize(), config.getTickMarkOutsideSize()));
                }
            }

            if (config.getMinorTickMarkInsideSize() > 0 || config.getMinorTickMarkOutsideSize() > 0) {
                canvas.setColor(config.getMinorTickMarkColor());
                canvas.setStroke(config.getMinorTickMarkWidth(), DashStyle.SOLID);
                for (int i = 0; i < minorTickPositions.size(); i++) {
                    canvas.drawLine(orientationFunctions.createTickLine(minorTickPositions.get(i), config.getAxisLineWidth(), config.getMinorTickMarkInsideSize(), config.getMinorTickMarkOutsideSize()));
                }
            }

            canvas.setStroke(1, DashStyle.SOLID);
            canvas.setColor(config.getTickLabelColor());
            canvas.setTextStyle(config.getTickLabelTextStyle());
            for (BText tickLabel : tickLabels) {
                tickLabel.draw(canvas);
            }

            if(titleText != null) {
                canvas.setColor(config.getTitleColor());
                canvas.setTextStyle(config.getTitleTextStyle());
                titleText.draw(canvas);
            }
        }

        if (config.getAxisLineWidth() > 0) {
            canvas.setColor(config.getAxisLineColor());
            canvas.setStroke(config.getAxisLineWidth(), config.getAxisLineDashStyle());
            canvas.drawLine(axisLine);
        }

        canvas.restore();
    }


    private List<Tick> generateTicks(TickProvider tickProvider, Scale scale, boolean isRoundingEnabled) {
        double min = scale.getMin();
        double max = scale.getMax();

        Tick tickMax;
        Tick tickMin;
        if (isRoundingEnabled) {
            tickMax = tickProvider.getUpperTick(max);
            tickMin = tickProvider.getLowerTick(min);
        } else {
            tickMax = tickProvider.getLowerTick(max);
            tickMin = tickProvider.getUpperTick(min);
        }
        Tick tickMinNext = tickProvider.getNextTick();

        int tickCount = 0;
        if (tickMax.getValue() > tickMin.getValue()) {
            double tickPixelInterval = scale.scale(tickMinNext.getValue()) - scale.scale(tickMin.getValue());
            int tickIntervalCount = (int) Math.abs(Math.round(Math.abs(scale.scale(tickMax.getValue()) - scale.scale(tickMin.getValue())) / tickPixelInterval));
            tickCount = tickIntervalCount + 1;
        }

        if (tickCount > MAX_TICKS_COUNT) {
            String errMsg = "Too many ticks: " + tickCount + ". Expected < " + MAX_TICKS_COUNT;
            throw new RuntimeException(errMsg);
        }

        List<Tick> ticks = new ArrayList<>();
        if (tickCount >= 2) {
            ticks.add(tickMin);
            ticks.add(tickMinNext);
            for (int i = 2; i < tickCount; i++) {
                ticks.add(tickProvider.getNextTick());
            }
        }
        return ticks;
    }

    // create ticks and fix overlapping
    private List<Tick> createValidTicks(TextMetric tm, Scale scale, double tickInterval, boolean isRoundingEnabled) {
        if (isTooShort()) {
            return new ArrayList<>(0);
        }

        TickProvider tickProvider;
        // if tick interval specified (tickInterval > 0)
        if (tickInterval > 0) {
            tickProvider = scale.getTickProviderByInterval(tickInterval, tickLabelPrefixAndSuffix);
        } else {
            int tickIntervalCount;
            int fontFactor = 4;
            double tickPixelInterval = fontFactor * config.getTickLabelTextStyle().getSize();
            tickIntervalCount = (int)(scale.length() / tickPixelInterval);
            tickIntervalCount = Math.max(tickIntervalCount, MIN_TICK_COUNT);
            tickProvider = scale.getTickProviderByIntervalCount(tickIntervalCount, tickLabelPrefixAndSuffix);
        }

        double min = scale.getMin();
        double max = scale.getMax();
        List<Tick> ticks = generateTicks(tickProvider, scale, isRoundingEnabled);

        // Calculate how many ticks need to be skipped to avoid labels overlapping.
        // When ticksSkipStep = n, only every n'th label on the axis will be shown.
        // For example if ticksSkipStep = 2 every other label will be shown.
        int ticksSkipStep = 1;

        int tickIntervalCount = ticks.size() - 1;

        if (ticks.size() >= 2) {
            double tickPixelInterval = Math.abs(scale.scale(ticks.get(1).getValue()) - scale.scale(ticks.get(0).getValue()));
            // calculate tick distance to avoid labels overlapping.
            int requiredSpaceForTickLabel = getRequiredSpaceForTickLabel(tm, config, orientationFunctions, ticks);

            if (tickPixelInterval < requiredSpaceForTickLabel) {
                if(isRoundingEnabled) {
                    // need to take into account that some extra ticks will be added
                    int n = Math.max(1, axisLength / requiredSpaceForTickLabel);
                    ticksSkipStep = (tickIntervalCount + tickIntervalCount % n) / n;
                } else {
                    ticksSkipStep = (int) (requiredSpaceForTickLabel / tickPixelInterval);
                    if (ticksSkipStep * tickPixelInterval < requiredSpaceForTickLabel) {
                        ticksSkipStep++;
                    }
                }


                if (ticksSkipStep > tickIntervalCount) {
                    ticksSkipStep = tickIntervalCount;
                }
            }
            // commented string gives more precise rounding but tick numbers are not so "round" and nice
            //if (!isRoundingEnabled && ticksSkipStep > 1 && (ticks.size() - 1) / ticksSkipStep >= 1) {
            boolean isTickIntervalCanBeIncreased = false;
            if(!isRoundingEnabled && ticksSkipStep > 1 && (ticks.size() - 1) / ticksSkipStep > 1) {
                isTickIntervalCanBeIncreased = true;
            }
            if(isRoundingEnabled && ticksSkipStep > 1 && (ticks.size() - 1) / ticksSkipStep >= 1) {
                isTickIntervalCanBeIncreased = true;
            }
            if (isTickIntervalCanBeIncreased) {
                tickProvider.increaseTickInterval(ticksSkipStep);
                ticks = generateTicks(tickProvider, scale, isRoundingEnabled);
                ticksSkipStep = 1;
            }
        }

        // 1) skip ticks if ticksSkipStep > 1
        // 2) add 2 extra ticks: one at the beginning and one at the end to be able to create minor grid
        if (ticks.size() < 2) { // possible only if rounding disabled
            Tick tickMin = tickProvider.getUpperTick(min);

            if (tickMin.getValue() > max) {
                ticks.add(tickProvider.getPreviousTick());
                ticks.add(tickMin);
            } else {
                tickProvider.getUpperTick(min);
                ticks.add(tickProvider.getPreviousTick());
                ticks.add(tickProvider.getNextTick());
                ticks.add(tickProvider.getNextTick());
            }
        } else {
            boolean isLastExtraTickAdded = false;
            if (ticksSkipStep > 1) {
                // create extra ticks to get tickIntervalCount multiple to ticksSkipStep
                int roundExtraTicksCount = ticksSkipStep - tickIntervalCount % ticksSkipStep;
                if (roundExtraTicksCount < ticksSkipStep) {
                    for (int i = 0; i < roundExtraTicksCount; i++) {
                        ticks.add(tickProvider.getNextTick());
                    }
                    if (!isRoundingEnabled) {
                        isLastExtraTickAdded = true;
                    }
                }

                List<Tick> skippedTicks = new ArrayList<>();
                for (int i = 0; i < ticks.size(); i++) {
                    if (i % ticksSkipStep == 0) {
                        skippedTicks.add(ticks.get(i));
                    }
                }
                ticks = skippedTicks;
            }

            // add extra tick at the end
            if (!isLastExtraTickAdded) {
                Tick extraTick = tickProvider.getNextTick();
                for (int i = 1; i < ticksSkipStep; i++) {
                    extraTick = tickProvider.getNextTick();
                }
                ticks.add(extraTick);
            }

            // add extra tick at the beginning
            if (isRoundingEnabled) {
                tickProvider.getLowerTick(min);
            } else {
                tickProvider.getUpperTick(min);
            }
            Tick extraTick = tickProvider.getPreviousTick();
            for (int i = 1; i < ticksSkipStep; i++) {
                extraTick = tickProvider.getPreviousTick();
            }
            ticks.add(0, extraTick);
        }
        if(isRoundingEnabled) {
            Tick tickMin = ticks.get(1);
            Tick tickMax = ticks.get(ticks.size() - 2);
            scale.setMinMax(tickMin.getValue(), tickMax.getValue());
        }

        return ticks;

    }

    private boolean isTooShort() {
        return isTooShort(config, axisLength);
    }

    public static boolean isTooShort(AxisConfig config, int axisLength) {
        int lengthMin = config.getTickLabelTextStyle().getSize() * 2;
        if (axisLength > lengthMin) {
            return false;
        }
        return true;
    }

    public static int calculateWidthOut(RenderContext renderContext, OrientationFunctions orientationFunctions, int axisLength, AxisConfig config, String label, String title) {
        int widthOut = config.getAxisLineWidth() / 2 + 1;
        if(isTooShort(config, axisLength)) {
            return widthOut;
        }
        widthOut += config.getTickMarkOutsideSize();
        if (config.isTickLabelOutside()) {
            TextMetric labelTM = renderContext.getTextMetric(config.getTickLabelTextStyle());
            widthOut += config.getTickPadding() + orientationFunctions.labelSizeForWidth(labelTM, label);
        }
        if (! StringUtils.isNullOrBlank(title)) {
            TextMetric titleTM = renderContext.getTextMetric(config.getTitleTextStyle());
            widthOut += config.getTitlePadding() + titleTM.height();
        }
        return widthOut;
    }


    public void createAxisElements(RenderContext renderContext, Scale scale, double tickInterval, boolean isRoundingEnabled, String title) {
        tickPositions = new IntArrayList();
        minorTickPositions = new IntArrayList();
        tickLabels = new ArrayList<>();
        String longestLabel = "";
        TextMetric labelTM = renderContext.getTextMetric(config.getTickLabelTextStyle());
        List<Tick> ticks = createValidTicks(labelTM, scale, tickInterval, isRoundingEnabled);
        if(ticks.size() == 0) {
            return;
        }

        int minorTickIntervalCount = config.getMinorTickIntervalCount();
        Tick currentTick = ticks.get(0);
        Tick nextTick = ticks.get(1);
        int tickPixelInterval = (int) Math.abs(scale.scale(currentTick.getValue()) - scale.scale(nextTick.getValue()));
        for (int tickNumber = 2; tickNumber <= ticks.size(); tickNumber++) {
            if (minorTickIntervalCount > 0) {
                // minor tick positions
                double minorTickInterval = (nextTick.getValue() - currentTick.getValue()) / minorTickIntervalCount;
                double minorTickValue = currentTick.getValue();
                for (int i = 1; i < minorTickIntervalCount; i++) {
                    minorTickValue += minorTickInterval;
                    int minorTickPosition = (int) Math.round(scale.scale(minorTickValue));
                    if (orientationFunctions.contains(minorTickPosition, (int)Math.round(scale.getStart()), (int)Math.round(scale.getEnd()))) {
                        minorTickPositions.add(minorTickPosition);
                    }
                }
            }
            if (tickNumber < ticks.size()) {
                currentTick = nextTick;
                nextTick = ticks.get(tickNumber);
                int tickPosition = (int) Math.round(scale.scale(currentTick.getValue()));
                if(orientationFunctions.contains(tickPosition, (int)Math.round(scale.getStart()), (int)Math.round(scale.getEnd()))) {
                    // tick position
                    tickPositions.add(tickPosition);
                    // tick label
                    if(currentTick.getLabel().length() > longestLabel.length()) {
                        longestLabel = currentTick.getLabel();
                    }
                    tickLabels.add(orientationFunctions.createTickLabel(labelTM, tickPosition, currentTick.getLabel(), (int)Math.round(scale.getStart()), (int)Math.round(scale.getEnd()), tickPixelInterval, config, getInterLabelGap(config), scale instanceof CategoryScale));
                }
            }
        }
        widthOut = calculateWidthOut(renderContext, orientationFunctions, axisLength, config, longestLabel, title);
        if (! StringUtils.isNullOrBlank(title)) {
            TextMetric titleTM = renderContext.getTextMetric(config.getTitleTextStyle());
            titleText = orientationFunctions.createTitle(title, titleTM, (int)Math.round(scale.getStart()), (int)Math.round(scale.getEnd()), widthOut);
        }
    }

    private static int getInterLabelGap(AxisConfig config) {
        return  (int)(2 * config.getTickLabelTextStyle().getSize());
    }

    private static int getRequiredSpaceForTickLabel(TextMetric tm, AxisConfig config, OrientationFunctions orientationFunctions, List<Tick> ticks) {
        String longestLabel = "";
        for (Tick tick : ticks) {
            if(tick.getLabel().length() > longestLabel.length()) {
                longestLabel = tick.getLabel();
            }
        }
        return  orientationFunctions.labelSizeForOverlap(tm, longestLabel) + getInterLabelGap(config);
    }
}

