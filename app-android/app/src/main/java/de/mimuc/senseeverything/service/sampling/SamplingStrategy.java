package de.mimuc.senseeverything.service.sampling;

import android.content.Context;

public interface SamplingStrategy {
    void start(Context context);
    void stop(Context context);
    void pause(Context context);
    void resume(Context context);

    boolean isRunning(Context context);
}
