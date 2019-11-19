package com.guichaguri.trackplayer.service.player;

import android.os.Bundle;
import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import com.facebook.react.bridge.Promise;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.metadata.icy.IcyInfo;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.metadata.id3.UrlLinkFrame;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.guichaguri.trackplayer.service.MusicManager;
import com.guichaguri.trackplayer.service.Utils;
import com.guichaguri.trackplayer.service.models.Track;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Guichaguri
 */
public abstract class ExoPlayback<T extends Player> implements EventListener, MetadataOutput {

    protected final Context context;
    protected final MusicManager manager;
    protected final T player;

    protected List<Track> queue = Collections.synchronizedList(new ArrayList<>());

    // https://github.com/google/ExoPlayer/issues/2728
    protected boolean startAutoPlay = false;
    protected int startWindow = C.INDEX_UNSET;
    protected long startPosition = C.POSITION_UNSET;
    protected int previousState = PlaybackStateCompat.STATE_NONE;
    protected float volumeMultiplier = 1.0F;

    public ExoPlayback(Context context, MusicManager manager, T player) {
        this.context = context;
        this.manager = manager;
        this.player = player;

        Player.MetadataComponent component = player.getMetadataComponent();
        if(component != null) component.addMetadataOutput(this);
    }

    public void initialize() {
        player.addListener(this);
    }

    public List<Track> getQueue() {
        return queue;
    }

    public abstract void add(Track track, int index, Promise promise);

    public abstract void add(Collection<Track> tracks, int index, Promise promise);

    public abstract void update(List<Track> update, List<Integer> updateIndex, Promise promise);

    public abstract void addOrUpdate(Collection<Track> insert, int index, List<Track> update, List<Integer> updateIndex, Promise promise);

    public abstract void remove(List<Integer> indexes, Promise promise);

    public abstract void removeUpcomingTracks();

    public void updateTrack(int index, Track track) {
        int currentIndex = player.getCurrentWindowIndex();

        queue.set(index, track);

        if(currentIndex == index)
            manager.getMetadata().updateMetadata(track);
    }

    public Track getCurrentTrack() {
        int index = player.getCurrentWindowIndex();
        return index < 0 || index >= queue.size() ? null : queue.get(index);
    }

    public void skip(String id, Promise promise) {
        if(id == null || id.isEmpty()) {
            promise.reject("invalid_id", "The ID can't be null or empty");
            return;
        }

        for(int i = 0; i < queue.size(); i++) {
            if(id.equals(queue.get(i).id)) {
                updateStartPosition();
                player.seekToDefaultPosition(i);
                promise.resolve(null);
                return;
            }
        }

        promise.reject("track_not_in_queue", "Given track ID was not found in queue");
    }

    public void skipToPrevious(Promise promise) {
        int prev = player.getPreviousWindowIndex();

        if(prev == C.INDEX_UNSET) {
            promise.reject("no_previous_track", "There is no previous track");
            return;
        }

        updateStartPosition();
        player.seekToDefaultPosition(prev);
        promise.resolve(null);
    }

    public void skipToNext(Promise promise) {
        int next = player.getNextWindowIndex();

        if(next == C.INDEX_UNSET) {
            promise.reject("queue_exhausted", "There is no tracks left to play");
            return;
        }

        updateStartPosition();
        player.seekToDefaultPosition(next);
        promise.resolve(null);
    }

    public void play() {
        player.setPlayWhenReady(true);
    }

    public void pause() {
        player.setPlayWhenReady(false);
    }

    public void stop() {
        clearStartPosition();

        player.stop(false);
        player.setPlayWhenReady(false);
        player.seekToDefaultPosition();
    }

    public void reset() {
        clearStartPosition();

        player.stop(true);
        player.setPlayWhenReady(false);
    }

    public boolean isRemote() {
        return false;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public long getBufferedPosition() {
        return player.getBufferedPosition();
    }

    public long getDuration() {
        Track current = getCurrentTrack();

        if (current != null && current.duration > 0) {
            return current.duration;
        }

        long duration = player.getDuration();

        return duration == C.TIME_UNSET ? 0 : duration;
    }

    public void seekTo(long time) {
        updateStartPosition();
        player.seekTo(time);
    }

    public float getVolume() {
        return getPlayerVolume() / volumeMultiplier;
    }

    public void setVolume(float volume) {
        setPlayerVolume(volume * volumeMultiplier);
    }

    public void setVolumeMultiplier(float multiplier) {
        setPlayerVolume(getVolume() * multiplier);
        this.volumeMultiplier = multiplier;
    }

    public abstract float getPlayerVolume();

    public abstract void setPlayerVolume(float volume);

    public float getRate() {
        return player.getPlaybackParameters().speed;
    }

    public void setRate(float rate) {
        player.setPlaybackParameters(new PlaybackParameters(rate, player.getPlaybackParameters().pitch));
    }

    public int getState() {
        switch(player.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                return player.getPlayWhenReady() ? PlaybackStateCompat.STATE_BUFFERING : PlaybackStateCompat.STATE_CONNECTING;
            case Player.STATE_ENDED:
                return PlaybackStateCompat.STATE_STOPPED;
            case Player.STATE_IDLE:
                return PlaybackStateCompat.STATE_NONE;
            case Player.STATE_READY:
                return player.getPlayWhenReady() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        }
        return PlaybackStateCompat.STATE_NONE;
    }

    public void destroy() {
        player.release();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        Log.d(Utils.LOG, "onTimelineChanged: " + reason);

        if((reason == Player.TIMELINE_CHANGE_REASON_PREPARED || reason == Player.TIMELINE_CHANGE_REASON_DYNAMIC) && !timeline.isEmpty()) {
            onPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
        }
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        Log.d(Utils.LOG, "onPositionDiscontinuity: " + reason);

        if(startWindow != player.getCurrentWindowIndex()) {
            Track previous = startWindow == C.INDEX_UNSET || startWindow >= queue.size() ? null : queue.get(startWindow);
            Track next = getCurrentTrack();

            // Track changed because it ended
            // We'll use its duration instead of the last known position
            if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION && startWindow != C.INDEX_UNSET) {
                if (startWindow >= player.getCurrentTimeline().getWindowCount()) return;
                long duration = player.getCurrentTimeline().getWindow(startWindow, new Window()).getDurationMs();
                if(duration != C.TIME_UNSET) startPosition = duration;
            }

            manager.onTrackUpdate(previous, startPosition, next);
        }

        updateStartPosition();
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        for(int i = 0; i < trackGroups.length; i++) {
            // Loop through all track groups.
            // As for the current implementation, there should be only one
            TrackGroup group = trackGroups.get(i);

            for(int f = 0; f < group.length; f++) {
                // Loop through all formats inside the track group
                Format format = group.getFormat(f);

                // Parse the metadata if it is present
                if (format.metadata != null) {
                    onMetadata(format.metadata);
                }
            }
        }
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        // Buffering updates
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        int state = getState();

        if(state != previousState) {
            if(Utils.isPlaying(state) && !Utils.isPlaying(previousState)) {
                manager.onPlay();
            } else if(Utils.isPaused(state) && !Utils.isPaused(previousState)) {
                manager.onPause();
            } else if(Utils.isStopped(state) && !Utils.isStopped(previousState)) {
                manager.onStop();
            }

            manager.onStateChange(state);
            previousState = state;

            if(state == PlaybackStateCompat.STATE_STOPPED) {
                manager.onEnd(getCurrentTrack(), getPosition());
            }
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // Repeat mode update
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        // Shuffle mode update
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        String code;

        if(error.type == ExoPlaybackException.TYPE_SOURCE) {
            code = "playback-source";
        } else if(error.type == ExoPlaybackException.TYPE_RENDERER) {
            code = "playback-renderer";
        } else {
            code = "playback"; // Other unexpected errors related to the playback
        }

        manager.onError(code, error.getCause().getMessage());
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        // Speed or pitch changes
    }

    @Override
    public void onSeekProcessed() {
        // Finished seeking
    }

    private void handleId3Metadata(Metadata metadata) {
        String title = null, url = null, artist = null, album = null, date = null, genre = null;
        Bundle extra = new Bundle();

        for(int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);

            if (entry instanceof TextInformationFrame) {
                // ID3 text tag
                TextInformationFrame id3 = (TextInformationFrame) entry;
                String id = id3.id.toUpperCase();

                if (id.equals("TIT2") || id.equals("TT2")) {
                    title = id3.value;
                } else if (id.equals("TALB") || id.equals("TOAL") || id.equals("TAL")) {
                    album = id3.value;
                } else if (id.equals("TOPE") || id.equals("TPE1") || id.equals("TP1")) {
                    artist = id3.value;
                } else if (id.equals("TDAT") || id.equals("TDRC") || id.equals("TOR")) {
                    date = id3.value;
                } else if (id.equals("TCON") || id.equals("TCO")) {
                    genre = id3.value;
                } else {
                    extra.putString(id, id3.value);
                }

            } else if (entry instanceof UrlLinkFrame) {
                // ID3 URL tag
                UrlLinkFrame id3 = (UrlLinkFrame) entry;
                String id = id3.id.toUpperCase();

                if (id.equals("WOAS") || id.equals("WOAF") || id.equals("WOAR") || id.equals("WAR")) {
                    url = id3.url;
                }

            }
        }

        if (title != null || url != null || artist != null || album != null || date != null || genre != null) {
            manager.onMetadataReceived("id3", title, url, artist, album, date, genre, extra);
        }
    }

    private void handleIcyMetadata(Metadata metadata) {
        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);

            if(entry instanceof IcyHeaders) {
                // ICY headers
                IcyHeaders icy = (IcyHeaders)entry;

                manager.onMetadataReceived("icy-headers", icy.name, icy.url, null, null, null, icy.genre, null);

            } else if(entry instanceof IcyInfo) {
                // ICY data
                IcyInfo icy = (IcyInfo)entry;

                String artist, title;
                int index = icy.title == null ? -1 : icy.title.indexOf(" - ");

                if (index != -1) {
                    artist = icy.title.substring(0, index);
                    title = icy.title.substring(index + 3);
                } else {
                    artist = null;
                    title = icy.title;
                }

                manager.onMetadataReceived("icy", title, icy.url, artist, null, null, null, null);

            }
        }
    }

    @Override
    public void onMetadata(Metadata metadata) {
        handleId3Metadata(metadata);
        handleIcyMetadata(metadata);
    }

    // Updates the current position of the player.
    protected void updateStartPosition() {
        if (player != null) {
            startAutoPlay = player.getPlayWhenReady();
            startWindow = player.getCurrentWindowIndex();
            startPosition = Math.max(0, player.getContentPosition());
        }
    }

    // Resets/Clear the current position of the player.
    protected void clearStartPosition() {
        startAutoPlay = true;
        startWindow = C.INDEX_UNSET;
        startPosition = C.TIME_UNSET;
    }
}
