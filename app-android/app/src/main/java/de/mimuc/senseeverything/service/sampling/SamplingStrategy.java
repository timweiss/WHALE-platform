package de.mimuc.senseeverything.service.sampling;

import android.content.Context;

public interface SamplingStrategy {
    void start(Context context);
    void stop(Context context);

    boolean isRunning(Context context);
}
