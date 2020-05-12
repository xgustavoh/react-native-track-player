package com.guichaguri.trackplayer.service.player;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.os.Bundle;

import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.metadata.icy.IcyInfo;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.metadata.id3.UrlLinkFrame;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.guichaguri.trackplayer.service.MusicManager;
import com.guichaguri.trackplayer.service.Utils;
import com.guichaguri.trackplayer.service.models.Track;

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
    protected boolean isAutoPlay = false;
    protected float volumeMultiplier = 1.0F;

    protected Track currentTrack = null;
    protected int currentTrackPos = C.INDEX_UNSET;

    protected int lastKnownWindow = C.INDEX_UNSET;
    protected long lastKnownPosition = C.POSITION_UNSET;
    protected int previousState = PlaybackStateCompat.STATE_NONE;

    /**
     * create ExoPlayback instance
     * @param context App Context
     * @param manager Music Manager
     * @param player ExoPlayer
     */
    public ExoPlayback(Context context, MusicManager manager, T player) {
        this.context = context;
        this.manager = manager;
        this.player = player;

        Player.MetadataComponent component = player.getMetadataComponent();
        if(component != null) component.addMetadataOutput(this);
    }

    /**
     * Initialize config default player
     */
    public void initialize() {
        player.addListener(this);
        player.setPlayWhenReady(isAutoPlay);
    }

    /**
     * Get Queue Tracks
     * @return List Track
     */
    public List<Track> getQueue() {
        return queue;
    }

    /**
     * Add new track to Queue
     * @param track Track
     * @param index Index to insert
     */
    public void add(Track track, int index) {
        boolean autoPlay = isAutoPlay == true && queue.size() == 0;

        boolean insert = true;
        for(int i=0; i < queue.size(); i++){
            if(queue.get(i).id == track.id){
                insert = false;
                queue.set(i, track);

                if(currentTrackPos == i) {
                    currentTrackPos = C.INDEX_UNSET;
                    setCurrentTrack(i);
                }
            }
        }

        if(insert) {
            queue.add(index, track);
        }

        if(autoPlay) {
            play();
        } else if(currentTrack != null) {
            currentTrackPos = queue.indexOf(currentTrack);
        }
    }

    /**
     * Add list os Tracks in Queue
     * @param tracks
     * @param index
     */
    public void add(Collection<Track> tracks, int index) {
        boolean autoPlay = isAutoPlay == true && queue.size() == 0;

        for(Track t : tracks) {
            boolean insert = true;
            for(int i=0; i < queue.size(); i++){
                if(queue.get(i).id == t.id){
                    insert = false;
                    queue.set(i, t);

                    if(currentTrackPos == i) {
                        currentTrackPos = C.INDEX_UNSET;
                        setCurrentTrack(i);
                    }
                }
            }

            if(insert) {
                queue.add(index++, t);
            }
        }

        if(autoPlay) {
            play();
        } else if(currentTrack != null) {
            currentTrackPos = queue.indexOf(currentTrack);
        }
    }

    /**
     * Remove Tracks to list (queue)
     * @param indexes
     */
    public void remove(List<Integer> indexes) {
        // Sort the list so we can loop through sequentially
        Collections.sort(indexes);

        for(int i = indexes.size() - 1; i >= 0; i--) {
            int index = indexes.get(i);
            if (queue.get(i) == currentTrack) {
                currentTrack = null;
                // Stop de Track!
            }
            queue.remove(index);
        }

        if(currentTrack != null) {
            setCurrentTrack(queue.indexOf(currentTrack));
        } else {
            setCurrentTrack(C.INDEX_UNSET);
        }
    }

    /**
     * Remove all 'next' track
     */
    public void removeUpcomingTracks() {
        for(int i = queue.size(); i > currentTrackPos; i--) {
            queue.remove(i);
        }
    }

    /**
     * Update the Track metadata
     * @param index index in queue
     * @param track new Track
     */
    public void updateTrack(int index, Track track) {
        queue.set(index, track);

        if(index != C.INDEX_UNSET && currentTrackPos == index) {
            currentTrack = queue.get(index);
            manager.getMetadata().updateMetadata(this, track);
        }
    }

    /**
     * Get current Track
     * @return Track
     */
    public Track getCurrentTrack() {
        return getTrack(currentTrackPos);
    }

    /**
     * Get Track
     * @return Track
     */
    public Track getTrack(int index) {
        return index != C.INDEX_UNSET && index < queue.size() ? queue.get(index) : null;
    }

    /**
     * Skip to Track
     * @param id Track.ID
     */
    public int skip(String id) {
        if(id == null || id.isEmpty()) {
            return -1;
        }

        for(int i = 0; i < queue.size(); i++) {
            if(id.equals(queue.get(i).id)) {
                setCurrentTrack(i);
                return i;
            }
        }

        return -1;
    }

    /**
     * Skip to Previous Track
     * @return track
     */
    public Track skipToPrevious() {
        if (currentTrackPos == C.INDEX_UNSET || queue.size() == 0) {
            return null;
        }

        int pos = currentTrackPos;
        if (pos == 0) {
            pos = queue.size() - 1;
        } else {
            pos--;
        }

        setCurrentTrack(pos);
        return currentTrack;
    }

    /**
     * Skip to Next Track
     * @return track
     */
    public Track skipToNext() {
        if (currentTrackPos == C.INDEX_UNSET || queue.size() == 0) {
            return null;
        }

        int pos = currentTrackPos;
        if (pos == queue.size() - 1 ) {
            pos = 0;
        } else {
            pos++;
        }

        setCurrentTrack(pos);
        return currentTrack;
    }

    /**
     * Set current Track
     * @param pos Queue position
     */
    public void setCurrentTrack(int pos) {
        Track old = getCurrentTrack();
        long position = player.getCurrentPosition();

        if(pos == C.INDEX_UNSET || pos < 0 || pos >= queue.size()) {
            currentTrack = null;
            currentTrackPos = C.INDEX_UNSET;
        } else {
            currentTrack = queue.get(pos);
            currentTrackPos = pos;
        }

        manager.onTrackUpdate(old, position, currentTrack);
    }

    /**
     * Updates the current position of the player.
     */
    protected void updateLastKnownPosition() {
        if (player != null) {
            isAutoPlay = player.getPlayWhenReady();
            lastKnownWindow = player.getCurrentWindowIndex();
            lastKnownPosition = player.getContentPosition() != C.TIME_UNSET ? Math.max(0, player.getContentPosition()) : C.TIME_UNSET;
        }
    }

    /**
     * Resets/Clear the current position of the player
     * @param autoPlay isAutoPlay
     */
    protected void clearLastKnownPosition(boolean autoPlay) {
        isAutoPlay = autoPlay;
        lastKnownWindow = C.INDEX_UNSET;
        lastKnownPosition = C.TIME_UNSET;
    }

    /**
     * Play current track
     */
    public void play() {
        if(queue.size() == 0) {
            return;
        }

        if(currentTrackPos == C.INDEX_UNSET) {
            setCurrentTrack(0);
        } else {
            setCurrentTrack(currentTrackPos);
        }

        player.setPlayWhenReady(true);
        updateLastKnownPosition();
    }

    /**
     * Pause current track
     */
    public void pause() {
        updateLastKnownPosition();
        player.setPlayWhenReady(false);
    }

    /**
     * Stop current track
     */
    public void stop() {
        player.setPlayWhenReady(false);
        player.stop(true);
        clearLastKnownPosition(false);
    }

    /**
     * Reset Queue
     */
    public void reset() {
        queue.clear();
        stop();
        setCurrentTrack(C.INDEX_UNSET);
        clearLastKnownPosition(isAutoPlay);
        manager.onReset();
    }

    /**
     * Seek Track position
     * @param time new position
     */
    public void seekTo(long time) {
        updateLastKnownPosition();
        player.seekTo(time);
    }

    /**
     * Check is remote connection
     * @return Remote connection
     */
    public boolean isRemote() {
        return currentTrack == null ? false : currentTrack.isRemote;
    }

    /**
     * Get current position
     * @return Position Track (seconds)
     */
    public long getPosition() {
        return player.getCurrentPosition();
    }

    /**
     * Get current buffer position
     * @return Buffer Postion Track (seconds)
     */
    public long getBufferedPosition() {
        return player.getBufferedPosition();
    }

    /**
     * Get track duration
     * @return duration (seconds)
     */
    public long getDuration() {
        Track current = getCurrentTrack();
        if (current != null && current.duration > 0) {
            return current.duration;
        }

        long duration = player.getDuration();
        return duration == C.TIME_UNSET ? 0 : duration;
    }

    /**
     * Get volume Player
     * @return volume -> [volume] / multiplier
     */
    public float getVolume() {
        return getPlayerVolume() / volumeMultiplier;
    }

    /**
     * Set volume Player
     * @param volume volume -> multiplier * [volume]
     */
    public void setVolume(float volume) {
        setPlayerVolume(volume * volumeMultiplier);
    }

    /**
     * Set multiplier volume ExoPlayer
     * @param multiplier multiplier -> multiplier * volume
     */
    public void setVolumeMultiplier(float multiplier) {
        setPlayerVolume(getVolume() * multiplier);
        this.volumeMultiplier = multiplier;
    }

    /**
     * Get volume ExoPlayer
     * @return volume * multiplier
     */
    public abstract float getPlayerVolume();

    /**
     * Set volume ExoPlayer
     * @param volume
     */
    public abstract void setPlayerVolume(float volume);

    /**
     * Get Track rate speed (Ex.: 0.5, 1.0, 2.0)
     * @return speed-rate
     */
    public float getRate() {
        return player.getPlaybackParameters().speed;
    }

    /**
     * Set Player rate Speed
     * @param rate (Ex.: 0.5, 1.0, 2.0)
     */
    public void setRate(float rate) {
        player.setPlaybackParameters(new PlaybackParameters(rate, player.getPlaybackParameters().pitch));
    }

    /**
     * Get current player state!
     * @return PlaybackStateCompat.X
     */
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

    /**
     * Destroy Player
     */
    public void destroy() {
        stop();
        reset();
        player.release();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
        Log.d(Utils.LOG, "onTimelineChanged: " + reason);

        if((reason == Player.TIMELINE_CHANGE_REASON_PREPARED || reason == Player.TIMELINE_CHANGE_REASON_DYNAMIC) && !timeline.isEmpty()) {
            onPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
        }
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        Log.d(Utils.LOG, "onPositionDiscontinuity: " + reason);

        if(lastKnownWindow != player.getCurrentWindowIndex()) {
            Track previous = currentTrackPos == C.INDEX_UNSET || currentTrackPos >= queue.size() ? null : queue.get(currentTrackPos);
            Track next = getCurrentTrack();

            // Track changed because it ended
            // We'll use its duration instead of the last known position
            if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION && lastKnownWindow != C.INDEX_UNSET) {
                if (lastKnownWindow >= player.getCurrentTimeline().getWindowCount()) return;

                long duration = player.getCurrentTimeline().getWindow(lastKnownWindow, new Window()).getDurationMs();
                if(duration != C.TIME_UNSET) {
                    lastKnownPosition = duration != C.TIME_UNSET ? Math.max(0, duration) : C.TIME_UNSET;
                }
            }

            manager.onTrackUpdate(previous, lastKnownPosition, next);
        }

        updateLastKnownPosition();
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
                updateLastKnownPosition();
                manager.onEnd(getCurrentTrack(), getPosition());
                Track previous = getCurrentTrack();
                long position = getPosition();
                manager.onTrackUpdate(previous, position, null);
                manager.onEnd(previous, position);
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

    /**
     * ID3 meta data
     * @param metadata
     */
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

    /**
     * IceCast metadata
     * @param metadata
     */
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

    /**
     * Update track Metadata
     * @param metadata
     */
    @Override
    public void onMetadata(Metadata metadata) {
        handleId3Metadata(metadata);
        handleIcyMetadata(metadata);
    }

    /**
     * Check this connection is Live type (streaming)
     * @param e ExoPlaybackException
     * @return isLive
     */
    protected boolean isBehindLiveWindow(ExoPlaybackException e) {
        if (e.type != ExoPlaybackException.TYPE_SOURCE) {
            return false;
        }
        Throwable cause = e.getSourceException();
        while (cause != null) {
            if (cause instanceof BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
