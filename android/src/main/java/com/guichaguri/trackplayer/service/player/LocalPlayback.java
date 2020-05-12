package com.guichaguri.trackplayer.service.player;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import com.facebook.react.bridge.Promise;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.guichaguri.trackplayer.service.MusicManager;
import com.guichaguri.trackplayer.service.Utils;
import com.guichaguri.trackplayer.service.models.Track;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author Guichaguri
 */
public class LocalPlayback extends ExoPlayback<SimpleExoPlayer> {

    private SimpleCache cache;
    private final long cacheMaxSize;
    // private ConcatenatingMediaSource source;
    // private boolean prepared = false;

    public LocalPlayback(Context context, MusicManager manager, SimpleExoPlayer player, long maxCacheSize) {
        super(context, manager, player);
        this.cacheMaxSize = maxCacheSize;
    }

    @Override
    public void initialize() {
        if(cacheMaxSize > 0) {
            File cacheDir = new File(context.getCacheDir(), "TrackPlayer");
            DatabaseProvider db = new ExoDatabaseProvider(context);
            cache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(cacheMaxSize), db);
        } else {
            cache = null;
        }

        super.initialize();
        reset();
    }

    public DataSource.Factory enableCaching(DataSource.Factory ds) {
        if(cache == null || cacheMaxSize <= 0) return ds;

        return new CacheDataSourceFactory(cache, ds, CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    /**
     * Add new track to Queue
     * @param track Track
     * @param index Index to insert
     */
    public void add(Track track, int index, Promise promise) {
        boolean autoPlay = isAutoPlay == true && queue.size() == 0;

        super.add(track, index);
        promise.resolve(null);
    }

    /**
     * Add list os Tracks in Queue
     * @param tracks
     * @param index
     */
    public void add(Collection<Track> tracks, int index, Promise promise) {
        super.add(tracks, index);
        promise.resolve(null);
    }

    /**
     * Remove Tracks to list (queue)
     * @param indexes
     */
    public void remove(List<Integer> indexes, Promise promise) {
        super.remove(indexes);
        promise.resolve(null);
    }

    /**
     * Set current Track
     * @param pos Queue position
     */
    @Override
    public void setCurrentTrack(int pos) {
        Log.d(Utils.LOG, "Preparing the media source... Pos:"+ pos);

        if(pos == C.INDEX_UNSET && currentTrackPos == C.INDEX_UNSET) {
            return;
        }

        int state = getState();
        if(pos == currentTrackPos && state == PlaybackStateCompat.STATE_PLAYING) {
            return;
        }

        Track t = getTrack(pos);
        if(pos != C.INDEX_UNSET && t != null) {
            player.prepare(t.toMediaSource(context, this), true, true);
        } else {
            player.stop(true);
        }

        super.setCurrentTrack(pos);
    }

    /**
     * Skip to Track
     * @param id
     * @param promise
     */
    public void skip(String id, Promise promise) {
        if(super.skip(id) != -1) {
            promise.resolve(null);
        } else {
            promise.reject("track_not_in_queue", "track not found in queue");
        }
    }


    /**
     * Skip to Previous Track
     */
    public void skipToPrevious(Promise promise) {
        Track track = super.skipToPrevious();
        if(track == null) {
            promise.reject("track_queue_empty", "not track in queue");
        } else {
            promise.resolve(null);
        }
    }

    /**
     * Skip to Next Track
     */
    public void skipToNext(Promise promise) {
        Track track = super.skipToNext();
        if(track == null) {
            promise.reject("track_queue_empty", "not track in queue");
        } else {
            promise.resolve(null);
        }
    }

    /**
     * Get volume ExoPlayer
     * @return volume * multiplier
     */
    public float getPlayerVolume() {
        return player.getVolume();
    }

    /**
     * Set volume ExoPlayer
     * @param volume
     */
    public void setPlayerVolume(float volume) {
        player.setVolume(volume);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        super.onPlayerError(error);
        if (isBehindLiveWindow(error)) {
            stop();
            play();
        }
    }

    /**
     * destroy the Player
     */
    @Override
    public void destroy() {
        super.destroy();

        if(cache != null) {
             try {
                 cache.release();
                 cache = null;
             } catch(Exception ex) {
                 Log.w(Utils.LOG, "Couldn't release the cache properly", ex);
             }
         }
    }

}
