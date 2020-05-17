package com.guichaguri.trackplayer.service.hunter;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

import java.io.FileNotFoundException;
import java.io.IOException;

public class HunterLoadErrorHandlingPolicy implements LoadErrorHandlingPolicy {
    @Override
    public long getBlacklistDurationMsFor(
            int dataType,
            long loadDurationMs,
            IOException exception,
            int errorCount) {

        if (    exception instanceof FileNotFoundException
                || (exception instanceof InvalidResponseCodeException && ((InvalidResponseCodeException)exception).responseCode != 500)
                || (errorCount > 1 && exception instanceof ParserException)) {
            return C.TIME_UNSET;
        }

        return Math.max(Math.min(errorCount, 10) * 500, 500);
    }

    @Override
    public long getRetryDelayMsFor(
            int dataType,
            long loadDurationMs,
            IOException exception,
            int errorCount) {

        if (    exception instanceof FileNotFoundException
                || (exception instanceof InvalidResponseCodeException && ((InvalidResponseCodeException)exception).responseCode != 500)
                || (errorCount > 1 && exception instanceof ParserException)) {
            return C.TIME_UNSET;
        }

        return Math.max(Math.min(errorCount, 10) * 500, 500);
    }

    @Override
    public int getMinimumLoadableRetryCount(int dataType) {
        return Integer.MAX_VALUE;
    }
}
