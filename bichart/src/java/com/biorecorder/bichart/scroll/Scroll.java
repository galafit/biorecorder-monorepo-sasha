package com.biorecorder.bichart.scroll;


import com.biorecorder.bichart.ScrollConfig;
import com.biorecorder.bichart.graphics.*;
import com.biorecorder.bichart.scales.Scale;

import java.util.ArrayList;
import java.util.List;

public class Scroll {
    private Scale scale; // шкала вдоль которой двигается вьюпорт (все параметры вьюпортра а также start и end содержатся в модели)
    private ScrollConfig config;
    private ScrollModel model;
    private List<ScrollListener> listeners = new ArrayList<ScrollListener>();

    public Scroll(ScrollConfig config, Scale scale) {
        this.model = new ScrollModel();
        this.config = config;
        this.scale = scale;
        int start = (int)scale.getStart();
        int end = (int)scale.getEnd();
        double position = start;
        int extent = end - start;
        model.setRangeProperties(position, extent, start, end);
        model.addListener(new ChangeListener() {
            @Override
            public void stateChanged() {
                fireListeners();
            }
        });
    }

    public void addListener(ScrollListener listener) {
        listeners.add(listener);
    }

    private void fireListeners() {
        for (ScrollListener listener : listeners) {
            listener.onScrollChanged(scale.invert(model.getViewportPosition()), scale.invert(model.getViewportPosition() + model.getViewportExtent()));
        }
    }

    public boolean isViewportAtTheEnd() {
        if(model.getViewportPosition() + model.getViewportExtent() == model.getEnd()) {
            return true;
        }
        return false;
    }

    public void setViewportAtTheEnd() {
        model.setViewportPosition(model.getEnd() - model.getViewportExtent());
    }

    public double getStart() {
       return model.getStart();
    }

    public double getEnd() {
        return model.getEnd();
    }

    public double getViewportExtent() {
        return model.getViewportExtent();
    }

    public void setStartEnd(int start, int end) {
        model.setStartEnd(start, end);
    }

    public void setViewportExtent(int extent) {
        model.setViewportExtent(extent);
    }

    public void setMinMax(double min, double max) {
        model.setStartEnd((int)scale.scale(min), (int)scale.scale(max));
    }

    public double getMin() {
        return scale.invert(model.getStart());
    }

    public double getMax() {
        return scale.invert(model.getEnd());
    }

    public double getViewportMin() {
        return scale.invert(model.getViewportPosition());
    }

    public double getViewportMax() {
        return scale.invert(model.getViewportPosition() + model.getViewportExtent());
    }


    public double getViewportCenterValue() {
        return scale.invert(model.getViewportPosition() + model.getViewportExtent()/2);
    }

    public void setViewportCenterValue(double centerValue) {
        double newPosition = scale.scale(centerValue) - model.getViewportExtent()/2;
        model.setViewportPosition(newPosition);
    }

    public void setViewportCenter(double position) {
        double newPosition = position - model.getViewportExtent()/2;
        model.setViewportPosition(newPosition);
    }

    public void moveViewport(double dx) {
        model.setViewportPosition(model.getViewportPosition() + dx);
    }

    public void zoom(double zoomFactor, int anchorPoint) {
        if(zoomFactor == 1) {
            return;
        }
        int length = model.getEnd() - model.getStart();
        if(length == model.getViewportExtent() && zoomFactor < 1) {
            return;
        }
        zoomFactor = Math.max(zoomFactor, 1.0 * model.getViewportExtent()/length);

        int scaleEnd = (int)(scale.getStart() + (scale.getEnd() - scale.getStart()) * zoomFactor);
        scale.setStartEnd(scale.getStart(), scaleEnd);
        int start = model.getStart();
        int end = (int)(start + (model.getEnd() - model.getStart()) * zoomFactor);
        double position = start + (model.getViewportPosition() - start) * zoomFactor;
        position += (zoomFactor - 1) * anchorPoint;
        // to prevent double listeners firing
        model.removeListeners();
        model.setRangeProperties(position, model.getViewportExtent(), start, end);
        model.addListener(new ChangeListener() {
            @Override
            public void stateChanged() {
                fireListeners();
            }
        });
        fireListeners();
    }

    public boolean viewportContainValue(double value) {
        if(value < getViewportMin() - config.getTouchRadius() || value > getViewportMax() + config.getTouchRadius()) {
            return false;
        }
        return true;
    }

    public double viewportRatio() {
        return model.getViewportExtent() / (model.getEnd() - model.getStart());
    }

    public BColor getColor() {
        return config.getColor();
    }

    public int getBorder() {
        return config.getBorder();
    }
}
