package com.guichaguri.trackplayer.service.hunter;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

public class HunterLoadControl implements LoadControl {

    private static final int ABOVE_HIGH_WATERMARK = 0;
    private static final int BETWEEN_WATERMARKS = 1;
    private static final int BELOW_LOW_WATERMARK = 2;

    private DefaultAllocator allocator;

    private long minBufferUs;
    private long maxBufferUs;
    private long bufferForPlaybackUs;
    private long bufferForPlaybackAfterRebufferUs;
    private long backBufferDurationUs;
    private boolean retainBackBufferFromKeyframe;

    private int targetBufferSize;
    private boolean isBuffering;

    public HunterLoadControl(
            int minBufferMs,
            int maxBufferMs,
            int bufferForPlaybackMs,
            int bufferForPlaybackAfterRebufferMs,
            int backBufferDurationMs,
            boolean retainBackBufferFromKeyframe) {

        allocator = new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
        minBufferUs = C.msToUs(minBufferMs);
        maxBufferUs = C.msToUs(maxBufferMs);
        bufferForPlaybackUs = C.msToUs(bufferForPlaybackMs);
        bufferForPlaybackAfterRebufferUs = C.msToUs(bufferForPlaybackAfterRebufferMs);

        backBufferDurationUs = C.msToUs(backBufferDurationMs);
        this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe;
    }

    @Override
    public void onPrepared() {
        reset(false);
    }

    @Override
    public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        targetBufferSize = 0;
        for (int i = 0; i < renderers.length; i++) {
            if (trackSelections.get(i) != null) {
                targetBufferSize += getDefaultBufferSize(renderers[i].getTrackType());
            }
        }
        allocator.setTargetBufferSize(targetBufferSize);
    }

    @Override
    public void onStopped() {
        reset(true);
    }

    @Override
    public void onReleased() {
        reset(true);
    }

    @Override
    public Allocator getAllocator() {
        return allocator;
    }

    @Override
    public long getBackBufferDurationUs() {
        return backBufferDurationUs;
    }

    @Override
    public boolean retainBackBufferFromKeyframe() {
        return retainBackBufferFromKeyframe;
    }

    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
        long minBufferDurationUs = rebuffering ? bufferForPlaybackAfterRebufferUs : bufferForPlaybackUs;
        return minBufferDurationUs <= 0 || bufferedDurationUs >= minBufferDurationUs;
    }

    @Override
    public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
        int bufferTimeState = getBufferTimeState(bufferedDurationUs);
        boolean targetBufferSizeReached = allocator.getTotalBytesAllocated() >= targetBufferSize;

        isBuffering = bufferTimeState == BELOW_LOW_WATERMARK
                || (bufferTimeState == BETWEEN_WATERMARKS && isBuffering && !targetBufferSizeReached);
        return isBuffering;
    }

    private int getBufferTimeState(long bufferedDurationUs) {
        return bufferedDurationUs > maxBufferUs ? ABOVE_HIGH_WATERMARK
                : (bufferedDurationUs < minBufferUs ? BELOW_LOW_WATERMARK : BETWEEN_WATERMARKS);
    }

    private void reset(boolean resetAllocator) {
        targetBufferSize = 0;
        isBuffering = false;
        if (resetAllocator) {
            allocator.reset();
        }
    }

    private static int getDefaultBufferSize(int trackType) {
        switch (trackType) {
            case C.TRACK_TYPE_DEFAULT:
                return DefaultLoadControl.DEFAULT_MUXED_BUFFER_SIZE;
            case C.TRACK_TYPE_AUDIO:
                return DefaultLoadControl.DEFAULT_AUDIO_BUFFER_SIZE;
            case C.TRACK_TYPE_VIDEO:
                return DefaultLoadControl.DEFAULT_VIDEO_BUFFER_SIZE;
            case C.TRACK_TYPE_TEXT:
                return DefaultLoadControl.DEFAULT_TEXT_BUFFER_SIZE;
            case C.TRACK_TYPE_METADATA:
                return DefaultLoadControl.DEFAULT_METADATA_BUFFER_SIZE;
            case C.TRACK_TYPE_CAMERA_MOTION:
                return DefaultLoadControl.DEFAULT_CAMERA_MOTION_BUFFER_SIZE;
            case C.TRACK_TYPE_NONE:
                return 0;
            default:
                throw new IllegalArgumentException();
        }
    }
}
