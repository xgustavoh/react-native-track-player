package com.guichaguri.trackplayer.service.hunter;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.DefaultCompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.hls.DefaultHlsDataSourceFactory;
import com.google.android.exoplayer2.source.hls.HlsDataSourceFactory;
import com.google.android.exoplayer2.source.hls.HlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaPeriod;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;

import java.io.IOException;
import java.util.List;

import static com.google.android.exoplayer2.source.hls.HlsMediaSource.METADATA_TYPE_ID3;

/** An HLS {@link MediaSource}. */
public final class HunterHlsMediaSource extends BaseMediaSource
        implements HlsPlaylistTracker.PrimaryPlaylistListener {

    private final HlsExtractorFactory extractorFactory;
    private final Uri manifestUri;
    private final HlsDataSourceFactory dataSourceFactory;
    private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private final DrmSessionManager<?> drmSessionManager;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private final boolean allowChunklessPreparation;
    private final @HlsMediaSource.MetadataType
    int metadataType;
    private final boolean useSessionKeys;
    private final HlsPlaylistTracker playlistTracker;
    @Nullable private Object tag;

    @Nullable private TransferListener mediaTransferListener;

    public final class HlsManifest {

        /**
         * The master playlist of an HLS stream.
         */
        public final HlsMasterPlaylist masterPlaylist;
        /**
         * A snapshot of a media playlist referred to by {@link #masterPlaylist}.
         */
        public final HlsMediaPlaylist mediaPlaylist;

        HlsManifest(HlsMasterPlaylist masterPlaylist, HlsMediaPlaylist mediaPlaylist) {
            this.masterPlaylist = masterPlaylist;
            this.mediaPlaylist = mediaPlaylist;
        }
    }

    public HunterHlsMediaSource(DataSource.Factory dataSourceFactory, Uri manifestUri) {
        this(
                HlsExtractorFactory.DEFAULT,
                manifestUri,
                new DefaultHlsDataSourceFactory(dataSourceFactory),
                new DefaultCompositeSequenceableLoaderFactory(),
                DrmSessionManager.getDummyDrmSessionManager(),
                new HunterLoadErrorHandlingPolicy(),
                false,
                METADATA_TYPE_ID3,
                false,
                null);
    }

    public HunterHlsMediaSource(HlsExtractorFactory extractorFactory,
                                 Uri manifestUri,
                                 HlsDataSourceFactory dataSourceFactory,
                                 CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
                                 DrmSessionManager<?> drmSessionManager,
                                 LoadErrorHandlingPolicy loadErrorHandlingPolicy,
                                 boolean allowChunklessPreparation,
                                 int metadataType,
                                 boolean useSessionKeys,
                                 @Nullable Object tag) {
        this.extractorFactory = extractorFactory;
        this.manifestUri = manifestUri;
        this.dataSourceFactory = dataSourceFactory;
        this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
        this.drmSessionManager = drmSessionManager;
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        this.allowChunklessPreparation = allowChunklessPreparation;
        this.metadataType = metadataType;
        this.useSessionKeys = useSessionKeys;
        this.playlistTracker = DefaultHlsPlaylistTracker.FACTORY.createTracker(dataSourceFactory, loadErrorHandlingPolicy, new DefaultHlsPlaylistParserFactory());
        this.tag = tag;
    }

    @Override
    @Nullable
    public Object getTag() {
        return tag;
    }

    @Override
    protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
        this.mediaTransferListener = mediaTransferListener;
        drmSessionManager.prepare();
        MediaSourceEventListener.EventDispatcher eventDispatcher = createEventDispatcher(/* mediaPeriodId= */ null);
        playlistTracker.start(manifestUri, eventDispatcher, /* listener= */ this);
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        playlistTracker.maybeThrowPrimaryPlaylistRefreshError();
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
        MediaSourceEventListener.EventDispatcher eventDispatcher = createEventDispatcher(id);
        return new HlsMediaPeriod(
                extractorFactory,
                playlistTracker,
                dataSourceFactory,
                mediaTransferListener,
                drmSessionManager,
                loadErrorHandlingPolicy,
                eventDispatcher,
                allocator,
                compositeSequenceableLoaderFactory,
                allowChunklessPreparation,
                metadataType,
                useSessionKeys);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        ((HlsMediaPeriod) mediaPeriod).release();
    }

    @Override
    protected void releaseSourceInternal() {
        playlistTracker.stop();
        drmSessionManager.release();
    }

    @Override
    public void onPrimaryPlaylistRefreshed(HlsMediaPlaylist playlist) {
        SinglePeriodTimeline timeline;
        long windowStartTimeMs = playlist.hasProgramDateTime ? C.usToMs(playlist.startTimeUs)
                : C.TIME_UNSET;
        // For playlist types EVENT and VOD we know segments are never removed, so the presentation
        // started at the same time as the window. Otherwise, we don't know the presentation start time.
        long presentationStartTimeMs =
                playlist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
                        || playlist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD
                        ? windowStartTimeMs
                        : C.TIME_UNSET;
        long windowDefaultStartPositionUs = playlist.startOffsetUs;
        // masterPlaylist is non-null because the first playlist has been fetched by now.
        HlsManifest manifest = new HlsManifest(Assertions.checkNotNull(playlistTracker.getMasterPlaylist()), playlist);
        if (playlistTracker.isLive()) {
            long offsetFromInitialStartTimeUs =
                    playlist.startTimeUs - playlistTracker.getInitialStartTimeUs();
            long periodDurationUs =
                    playlist.hasEndTag ? offsetFromInitialStartTimeUs + playlist.durationUs : C.TIME_UNSET;
            List<HlsMediaPlaylist.Segment> segments = playlist.segments;
            if (windowDefaultStartPositionUs == C.TIME_UNSET) {
                windowDefaultStartPositionUs = 0;
                if (!segments.isEmpty()) {
                    windowDefaultStartPositionUs = segments.get(0).relativeStartTimeUs;
                }
            }

            timeline =
                    new SinglePeriodTimeline(
                            presentationStartTimeMs,
                            windowStartTimeMs,
                            periodDurationUs,
                            /* windowDurationUs= */ playlist.durationUs,
                            /* windowPositionInPeriodUs= */ offsetFromInitialStartTimeUs,
                            windowDefaultStartPositionUs,
                            /* isSeekable= */ true,
                            /* isDynamic= */ !playlist.hasEndTag,
                            /* isLive= */ true,
                            manifest,
                            tag);
        } else /* not live */ {
            if (windowDefaultStartPositionUs == C.TIME_UNSET) {
                windowDefaultStartPositionUs = 0;
            }
            timeline =
                    new SinglePeriodTimeline(
                            presentationStartTimeMs,
                            windowStartTimeMs,
                            /* periodDurationUs= */ playlist.durationUs,
                            /* windowDurationUs= */ playlist.durationUs,
                            /* windowPositionInPeriodUs= */ 0,
                            windowDefaultStartPositionUs,
                            /* isSeekable= */ true,
                            /* isDynamic= */ false,
                            /* isLive= */ false,
                            manifest,
                            tag);
        }
        refreshSourceInfo(timeline);
    }
}