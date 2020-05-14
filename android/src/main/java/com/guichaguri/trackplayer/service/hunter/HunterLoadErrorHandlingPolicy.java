package com.guichaguri.trackplayer.service.hunter;

import android.util.Log;

import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.guichaguri.trackplayer.service.Utils;

import java.io.IOException;

public class HunterLoadErrorHandlingPolicy implements LoadErrorHandlingPolicy {
    @Override
    public long getBlacklistDurationMsFor(
            int dataType,
            long loadDurationMs,
            IOException exception,
            int errorCount) {

        Log.e(Utils.LOG, "getBlacklistDurationMsFor: dataType: "+dataType+", loadDurationMs: "+loadDurationMs+", errorCount: "+errorCount+" \n" + exception.getMessage());
        // if (exception instanceof HttpDataSourceException) {
        //     return (1 << Math.min(errorCount - 1, 10)) * 1000;
        // }
        // return super.getBlacklistDurationMsFor(dataType, loadDurationMs, exception, errorCount);
        return Math.max(Math.min(errorCount, 10) * 500, 500);
    }

    @Override
    public long getRetryDelayMsFor(
            int dataType,
            long loadDurationMs,
            IOException exception,
            int errorCount) {

        Log.e(Utils.LOG, "getRetryDelayMsFor: dataType: "+dataType+", loadDurationMs: "+loadDurationMs+", errorCount: "+errorCount+" \n" + exception.getMessage());
        // if (
        //         exception instanceof HttpDataSourceException
        //     || (exception instanceof InvalidResponseCodeException && ((InvalidResponseCodeException) exception).responseCode == 500)) {
        //     return (1 << Math.min(errorCount - 1, 3)) * 1000;
        // }
        // return super.getBlacklistDurationMsFor(dataType, loadDurationMs, exception, errorCount);
        return Math.max(Math.min(errorCount, 10) * 500, 500);
    }

    @Override
    public int getMinimumLoadableRetryCount(int dataType) {
        return Integer.MAX_VALUE;
    }
}
