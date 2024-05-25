package de.mimuc.senseeverything.service.sampling;

import android.content.Context;

public class SamplingManager {
    private SamplingStrategy samplingStrategy;

    public SamplingManager(SamplingStrategy defaultStrategy) {
        samplingStrategy = defaultStrategy;
    }

    public void setSamplingStrategy(Context context, SamplingStrategy samplingStrategy) {
        // clean exit
        if (samplingStrategy != null && samplingStrategy.isRunning(context)) {
            samplingStrategy.stop(context);
        }

        this.samplingStrategy = samplingStrategy;
    }

    public void startSampling(Context context) {
        samplingStrategy.start(context);
    }

    public void stopSampling(Context context) {
        samplingStrategy.stop(context);
    }

    public boolean isRunning(Context context) {
        return samplingStrategy.isRunning(context);
    }
}
