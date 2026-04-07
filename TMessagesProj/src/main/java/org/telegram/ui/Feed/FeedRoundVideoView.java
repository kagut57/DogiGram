package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PlayPauseDrawable;

import java.io.File;
import java.lang.ref.WeakReference;

@SuppressLint("ViewConstructor")
public class FeedRoundVideoView extends FrameLayout
        implements NotificationCenter.NotificationCenterDelegate {

    private static final int CIRCLE_COLLAPSED = dp(240);
    private static final int CIRCLE_EXPANDED = dp(300);
    private static final long EXPAND_DURATION = 250;
    private static final long SYNC_INTERVAL = 1000;
    private static final long LONG_PRESS_DELAY = 300;
    private static final float RING_TOUCH_WIDTH = dp(32);
    private static final float TOUCH_SLOP = dp(8);
    private static final float[] SPEEDS = {1f, 1.5f, 2f};

    private static final long SEEK_THROTTLE_MS = 100;
    private long lastSeekUpdateTime;

    private static final int TOUCH_IDLE = 0;
    private static final int TOUCH_PENDING = 1;
    private static final int TOUCH_SEEKING = 2;
    private static final int TOUCH_SPEEDUP = 3;

    private int savedSpeedIndex;

    private int touchState = TOUCH_IDLE;
    private float touchDownX, touchDownY;
    private boolean wasPlayingBeforeSeek;

    private final int currentAccount;
    private final BackupImageView thumbView;
    private final TextureView textureView;
    private final FeedRoundProgressView progressView;
    private final PlayButtonView playButton;
    private final SpeedBadgeView speedBadge;
    private final TimestampLabel timestampLabel;

    private MediaPlayer mediaPlayer;
    private MessageObject currentMessage;
    private boolean isPlaying;
    private boolean isPrepared;
    private boolean surfaceReady;
    private boolean finished;
    private int speedIndex;
    private int cachedDuration;

    private int currentCircleSize = CIRCLE_COLLAPSED;
    private boolean isExpanded;
    private ValueAnimator expandAnim;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static WeakReference<FeedRoundVideoView> activePlayerRef;
    private static RoundVideoStateListener stateListener;

    private boolean fileLoading;
    private float fileProgress;
    private boolean pendingPlayAfterDownload;
    private String currentFileName;

    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying && isPrepared) {
                try {
                    int pos = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();
                    cachedDuration = dur;
                    progressView.sync(pos, dur, SPEEDS[speedIndex]);
                    timestampLabel.setTime(pos, dur);
                } catch (Exception ignored) {}
                handler.postDelayed(this, SYNC_INTERVAL);
            }
        }
    };

    private final Runnable longPressRunnable = () -> {
        if (touchState == TOUCH_PENDING && isPlaying) {
            enterSpeedUpMode();
        }
    };

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount || currentMessage == null) return;

        if (id == NotificationCenter.fileLoaded) {
            if (isCurrentFileEvent(args)) {
                onCurrentFileLoaded();
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            if (isCurrentFileEvent(args)) {
                fileLoading = true;
                fileProgress = extractFileProgress(args);
                if (!isPrepared && !isPlaying && !finished) {
                    playButton.setState(PlayButtonView.STATE_LOADING);
                    progressView.setVisibility(VISIBLE);
                    progressView.setDownloadProgress(fileProgress);
                }
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            if (isCurrentFileEvent(args)) {
                fileLoading = false;
                fileProgress = 0f;
                pendingPlayAfterDownload = false;
                if (!isPrepared && !isPlaying && !finished) {
                    progressView.clearDownload();
                    progressView.setVisibility(GONE);
                    playButton.setState(PlayButtonView.STATE_DOWNLOAD);
                }
            }
        }
    }

    public interface SizeChangeListener {
        void onRoundVideoSizeChanged(int newSizePx);
    }

    public interface RoundVideoStateListener {
        void onRoundVideoStateChanged();
    }

    private SizeChangeListener sizeChangeListener;
    public void setSizeChangeListener(SizeChangeListener l) { sizeChangeListener = l; }
    public static void setRoundVideoStateListener(RoundVideoStateListener l) {
        stateListener = l;
    }

    public static FeedRoundVideoView getActivePlayer() {
        return activePlayerRef != null ? activePlayerRef.get() : null;
    }

    public static boolean isActivelyPlaying() {
        FeedRoundVideoView v = getActivePlayer();
        return v != null && v.isPlaying;
    }

    public static boolean isActivePausedOrFinished() {
        FeedRoundVideoView v = getActivePlayer();
        return v != null && !v.isPlaying && v.isPrepared;
    }

    public static MessageObject getActiveMessage() {
        FeedRoundVideoView v = getActivePlayer();
        if (v != null && v.currentMessage != null && v.isPrepared
                && (!v.finished || v.touchState == TOUCH_SEEKING)) {
            return v.currentMessage;
        }
        return null;
    }

    public static float getActiveProgress() {
        FeedRoundVideoView v = getActivePlayer();
        if (v != null && v.progressView != null) {
            return v.progressView.computeCurrentProgress();
        }
        return 0;
    }

    public static float getActivePlaybackSpeed() {
        FeedRoundVideoView v = getActivePlayer();
        return v != null ? SPEEDS[v.speedIndex] : 1f;
    }

    private void notifyStateChanged() {
        if (stateListener != null) {
            stateListener.onRoundVideoStateChanged();
        }
    }

    public void externalTogglePlayback() {
        togglePlayback();
    }

    public void externalCycleSpeed() {
        cycleSpeed();
    }

    public void externalStop() {
        release();
        resetToCollapsed();
        notifyStateChanged();
    }

    public FeedRoundVideoView(Context context, int account) {
        super(context);
        this.currentAccount = account;
        setClipChildren(false);
        setClipToPadding(false);

        thumbView = new BackupImageView(context);
        thumbView.setRoundRadius(CIRCLE_COLLAPSED / 2);
        addView(thumbView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        textureView = new TextureView(context);
        textureView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        textureView.setClipToOutline(true);
        textureView.setVisibility(GONE);
        addView(textureView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new FeedRoundProgressView(context);
        progressView.setVisibility(GONE);
        addView(progressView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        playButton = new PlayButtonView(context);
        addView(playButton, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        speedBadge = new SpeedBadgeView(context);
        speedBadge.setVisibility(GONE);
        addView(speedBadge, LayoutHelper.createFrame(
                36, 36, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 8, 8));
        speedBadge.setOnClickListener(v -> cycleSpeed());

        timestampLabel = new TimestampLabel(context);
        timestampLabel.setVisibility(GONE);
        addView(timestampLabel, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture s, int w, int h) {
                surfaceReady = true;
                if (mediaPlayer != null && isPrepared)
                    mediaPlayer.setSurface(new Surface(s));
            }
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s,
                                                              int w, int h) {}
            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) {
                surfaceReady = false;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
        });
    }

    public void setMessage(MessageObject msg) {
        release();
        currentMessage = msg;
        isPlaying = false;
        isPrepared = false;
        finished = false;
        speedIndex = 0;

        fileLoading = false;
        fileProgress = 0f;
        pendingPlayAfterDownload = false;
        currentFileName = null;

        if (msg == null) { setVisibility(GONE); return; }

        TLRPC.Document doc = msg.messageOwner.media != null
                ? msg.messageOwner.media.document : null;
        if (doc == null) { setVisibility(GONE); return; }

        currentFileName = FileLoader.getAttachFileName(doc);

        thumbView.setRoundRadius(CIRCLE_COLLAPSED / 2);
        if (doc.thumbs != null && !doc.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumb = FeedMediaHelper.bestSize(doc.thumbs);
            if (thumb != null)
                thumbView.setImage(ImageLocation.getForDocument(thumb, doc),
                        CIRCLE_COLLAPSED + "_" + CIRCLE_COLLAPSED,
                        null, null, 0, doc);
        }

        resetToCollapsed();
        setVisibility(VISIBLE);
        refreshFileStateUi();
        FileLoader.getInstance(currentAccount)
                .loadFile(doc, msg, FileLoader.PRIORITY_NORMAL, 0);
    }

    public void release() {
        handler.removeCallbacks(syncRunnable);
        handler.removeCallbacks(longPressRunnable);
        progressView.stopSmooth();
        isPlaying = false;
        isPrepared = false;
        if (mediaPlayer != null) {
            try { mediaPlayer.setOnSeekCompleteListener(null); } catch (Exception ignored) {}
            try { mediaPlayer.stop(); }    catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (activePlayerRef != null && activePlayerRef.get() == this) {
            activePlayerRef = null;
            notifyStateChanged();
        }
    }

    private void togglePlayback() {
        if (currentMessage == null) return;
        if (finished)  { replay(); return; }
        if (isPlaying) { pause();  return; }
        play();
    }

    private void play() {
        if (currentMessage == null) return;

        TLRPC.Document doc = currentMessage.messageOwner.media != null
                ? currentMessage.messageOwner.media.document : null;
        if (doc == null) return;

        File file = getVideoFile(doc);
        if (file == null || !file.exists()) {
            pendingPlayAfterDownload = true;
            requestCurrentDownload(FileLoader.PRIORITY_HIGH);
            return;
        }

        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.start();
            isPlaying = true;
            activePlayerRef = new WeakReference<>(this);
            MediaController.getInstance().cleanupPlayer(true, true);
            playButton.setVisibility(GONE);
            speedBadge.setVisibility(VISIBLE);
            timestampLabel.setVisibility(VISIBLE);
            startSmoothProgress();
            animateExpand(true);
            notifyStateChanged();
            return;
        }

        try {
            release();
            finished = false;
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getContext(), Uri.fromFile(file));
            mediaPlayer.setLooping(false);

            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                cachedDuration = mp.getDuration();
                if (surfaceReady && textureView.getSurfaceTexture() != null)
                    mp.setSurface(new Surface(textureView.getSurfaceTexture()));

                applySpeed();
                mp.start();
                isPlaying = true;

                activePlayerRef = new WeakReference<>(FeedRoundVideoView.this);
                MediaController.getInstance().cleanupPlayer(true, true);

                thumbView.setVisibility(GONE);
                textureView.setVisibility(VISIBLE);
                playButton.setVisibility(GONE);
                progressView.setVisibility(VISIBLE);
                speedBadge.setVisibility(VISIBLE);
                timestampLabel.setVisibility(VISIBLE);

                progressView.clearDownload();

                progressView.sync(0, cachedDuration, SPEEDS[speedIndex]);
                startSmoothProgress();
                animateExpand(true);
                notifyStateChanged();
            });

            mediaPlayer.setOnCompletionListener(mp -> onFinished());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                release();
                resetToCollapsed();
                return true;
            });

            mediaPlayer.prepareAsync();
            textureView.setVisibility(VISIBLE);
        } catch (Exception e) {
            e.printStackTrace();
            release();
        }
    }

    private void pause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            progressView.stopSmooth();
            handler.removeCallbacks(syncRunnable);
            playButton.setState(PlayButtonView.STATE_PLAY);
            playButton.setVisibility(VISIBLE);
            notifyStateChanged();
        }
    }

    private void onFinished() {
        isPlaying = false;
        finished = true;
        progressView.stopSmooth();
        handler.removeCallbacks(syncRunnable);
        progressView.setDirect(1f);

        playButton.setState(PlayButtonView.STATE_REPLAY);
        playButton.setVisibility(VISIBLE);
        speedBadge.setVisibility(GONE);

        if (touchState == TOUCH_SPEEDUP) {
            speedIndex = savedSpeedIndex;
            applySpeed();
            speedBadge.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            touchState = TOUCH_IDLE;
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
        }

        animateExpand(false);
        notifyStateChanged();
    }

    private void replay() {
        finished = false;
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.seekTo(0);
            mediaPlayer.start();
            isPlaying = true;
            activePlayerRef = new WeakReference<>(this);
            playButton.setVisibility(GONE);
            speedBadge.setVisibility(VISIBLE);
            timestampLabel.setVisibility(VISIBLE);
            progressView.sync(0, cachedDuration, SPEEDS[speedIndex]);
            startSmoothProgress();
            animateExpand(true);
            notifyStateChanged();
        } else {
            play();
        }
    }

    private void startSmoothProgress() {
        progressView.startSmooth();
        handler.removeCallbacks(syncRunnable);
        handler.postDelayed(syncRunnable, SYNC_INTERVAL);
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        applySpeed();
        speedBadge.setSpeed(SPEEDS[speedIndex]);
        if (mediaPlayer != null && isPrepared) {
            try {
                progressView.sync(mediaPlayer.getCurrentPosition(),
                        mediaPlayer.getDuration(), SPEEDS[speedIndex]);
            } catch (Exception ignored) {}
        }
    }

    private void applySpeed() {
        if (mediaPlayer == null || !isPrepared) return;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                boolean was = mediaPlayer.isPlaying();
                mediaPlayer.setPlaybackParams(
                        mediaPlayer.getPlaybackParams().setSpeed(SPEEDS[speedIndex]));
                if (!was) mediaPlayer.pause();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN
                && progressView.getVisibility() == VISIBLE
                && (isPlaying || finished)
                && isNearRing(ev)) {
            touchDownX = ev.getX();
            touchDownY = ev.getY();
            enterSeekMode();
            updateSeekFromTouch(ev);
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = ev.getX();
                touchDownY = ev.getY();

                if (touchState == TOUCH_SEEKING) {
                    return true;
                }

                touchState = TOUCH_PENDING;
                handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (touchState == TOUCH_PENDING) {
                    if (movedBeyondSlop(ev)) {
                        handler.removeCallbacks(longPressRunnable);
                        touchState = TOUCH_IDLE;
                        return false;
                    }
                } else if (touchState == TOUCH_SEEKING) {
                    updateSeekFromTouch(ev);
                }
                return true;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                if (touchState == TOUCH_SEEKING) {
                    exitSeekMode();
                } else if (touchState == TOUCH_SPEEDUP) {
                    exitSpeedUpMode();
                } else if (touchState == TOUCH_PENDING) {
                    togglePlayback();
                }
                touchState = TOUCH_IDLE;
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                if (touchState == TOUCH_SEEKING) cancelSeek();
                else if (touchState == TOUCH_SPEEDUP) exitSpeedUpMode();
                touchState = TOUCH_IDLE;
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private void enterSeekMode() {
        touchState = TOUCH_SEEKING;
        wasPlayingBeforeSeek = isPlaying;

        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);

        if (isPlaying && mediaPlayer != null) {
            mediaPlayer.pause();
            isPlaying = false;
        }
        progressView.stopSmooth();
        handler.removeCallbacks(syncRunnable);
        handler.removeCallbacks(longPressRunnable);

        progressView.setSeekProgress(progressView.getProgress());

        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        notifyStateChanged();
    }

    private void exitSeekMode() {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(false);

        final float progress = progressView.getProgress();
        final boolean shouldResume = wasPlayingBeforeSeek || finished;

        if (mediaPlayer != null && isPrepared && cachedDuration > 0) {
            final int target = (int) (progress * cachedDuration);

            mediaPlayer.setOnSeekCompleteListener(mp -> {
                mp.setOnSeekCompleteListener(null);
                progressView.endSeek();

                try {
                    int pos = mp.getCurrentPosition();
                    int dur = mp.getDuration();
                    cachedDuration = dur;
                    progressView.sync(pos, dur, SPEEDS[speedIndex]);
                    timestampLabel.setTime(pos, dur);
                } catch (Exception ignored) {
                    progressView.sync(target, cachedDuration, SPEEDS[speedIndex]);
                }

                if (shouldResume) {
                    finished = false;
                    try {
                        mp.start();
                        isPlaying = true;
                        playButton.setVisibility(GONE);
                        speedBadge.setVisibility(VISIBLE);
                        startSmoothProgress();
                        animateExpand(true);
                        notifyStateChanged();
                    } catch (Exception e) {
                        onFinished();
                    }
                } else {
                    notifyStateChanged();
                }
            });

            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    mediaPlayer.seekTo(target, MediaPlayer.SEEK_CLOSEST);
                } else {
                    mediaPlayer.seekTo(target);
                }
            } catch (Exception e) {
                mediaPlayer.setOnSeekCompleteListener(null);
                progressView.endSeek();
                notifyStateChanged();
            }
        } else {
            progressView.endSeek();
            notifyStateChanged();
        }
    }

    private void cancelSeek() {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(false);

        progressView.endSeek();

        if (mediaPlayer != null && isPrepared) {
            try {
                int pos = mediaPlayer.getCurrentPosition();
                int dur = mediaPlayer.getDuration();
                progressView.sync(pos, dur, SPEEDS[speedIndex]);
                progressView.setDirect((float) pos / dur);
                timestampLabel.setTime(pos, dur);
            } catch (Exception ignored) {}

            if (wasPlayingBeforeSeek) {
                mediaPlayer.start();
                isPlaying = true;
                startSmoothProgress();
            }
        }
    }

    private void updateSeekFromTouch(MotionEvent ev) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        double angle = Math.atan2(ev.getY() - cy, ev.getX() - cx);
        float progress = (float) ((angle + Math.PI / 2) / (2 * Math.PI));
        if (progress < 0) progress += 1f;

        progressView.setSeekProgress(progress);

        if (cachedDuration > 0) {
            timestampLabel.setTime((int) (progress * cachedDuration), cachedDuration);

            long now = SystemClock.elapsedRealtime();
            if (now - lastSeekUpdateTime > SEEK_THROTTLE_MS) {
                lastSeekUpdateTime = now;
                seekMediaTo(progress);
            }
        }
    }

    private void seekMediaTo(float progress) {
        if (mediaPlayer != null && isPrepared) {
            int target = (int) (progress * mediaPlayer.getDuration());
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    mediaPlayer.seekTo(target, MediaPlayer.SEEK_CLOSEST);
                } else {
                    mediaPlayer.seekTo(target);
                }
            } catch (Exception ignored) {}
        }
    }

    private boolean isNearRing(MotionEvent ev) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dist = (float) Math.hypot(ev.getX() - cx, ev.getY() - cy);
        float r = Math.min(cx, cy);
        return dist >= r - RING_TOUCH_WIDTH && dist <= r + dp(12);
    }

    private boolean movedBeyondSlop(MotionEvent ev) {
        return Math.hypot(ev.getX() - touchDownX, ev.getY() - touchDownY) > TOUCH_SLOP;
    }

    private void animateExpand(boolean expand) {
        if (expand == isExpanded) return;
        isExpanded = expand;

        int from = currentCircleSize;
        int to = expand ? CIRCLE_EXPANDED : CIRCLE_COLLAPSED;

        if (expandAnim != null) expandAnim.cancel();
        expandAnim = ValueAnimator.ofInt(from, to);
        expandAnim.setDuration(EXPAND_DURATION);
        expandAnim.setInterpolator(new DecelerateInterpolator());
        expandAnim.addUpdateListener(a -> {
            currentCircleSize = (int) a.getAnimatedValue();
            applyCircleSize(currentCircleSize);
        });
        expandAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { expandAnim = null; }
        });
        expandAnim.start();
    }

    private void applyCircleSize(int size) {
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) return;
        lp.width = size;
        lp.height = size;
        setLayoutParams(lp);
        thumbView.setRoundRadius(size / 2);
        if (sizeChangeListener != null) sizeChangeListener.onRoundVideoSizeChanged(size);
    }

    private void resetToCollapsed() {
        currentCircleSize = CIRCLE_COLLAPSED;
        isExpanded = false;
        applyCircleSize(CIRCLE_COLLAPSED);

        thumbView.setVisibility(VISIBLE);
        textureView.setVisibility(GONE);
        playButton.setVisibility(VISIBLE);
        progressView.clearDownload();
        progressView.setVisibility(GONE);
        progressView.setDirect(0);
        speedBadge.setVisibility(GONE);
        timestampLabel.setVisibility(GONE);

        if (currentMessage != null && !isPrepared && !finished) {
            refreshFileStateUi();
        } else {
            playButton.setState(PlayButtonView.STATE_PLAY);
        }
    }

    private File getVideoFile(TLRPC.Document doc) {
        FileLoader fl = FileLoader.getInstance(currentAccount);
        File f = fl.getPathToAttach(doc, false);
        if (f != null && f.exists()) return f;
        f = fl.getPathToAttach(doc, true);
        if (f != null && f.exists()) return f;
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.addObserver(this, NotificationCenter.fileLoaded);
        nc.addObserver(this, NotificationCenter.fileLoadProgressChanged);
        nc.addObserver(this, NotificationCenter.fileLoadFailed);

        refreshFileStateUi();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.removeObserver(this, NotificationCenter.fileLoaded);
        nc.removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        nc.removeObserver(this, NotificationCenter.fileLoadFailed);
        release();
        resetToCollapsed();
    }

    static class PlayButtonView extends View {

        static final int STATE_PLAY = 0;
        static final int STATE_PAUSE = 1;
        static final int STATE_REPLAY = 2;
        static final int STATE_DOWNLOAD = 3;
        static final int STATE_LOADING = 4;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF tmpRect = new RectF();

        private final PlayPauseDrawable playPauseDrawable;

        private int state = STATE_PLAY;

        PlayButtonView(Context context) {
            super(context);
            bgPaint.setColor(0x66000000);
            iconPaint.setColor(0xFFFFFFFF);

            playPauseDrawable = new PlayPauseDrawable(14);
            playPauseDrawable.setParent(this);
            playPauseDrawable.setColor(0xFFFFFFFF);
            playPauseDrawable.setPause(false, false);
        }

        void setState(int s) {
            boolean animate = (state == STATE_PLAY || state == STATE_PAUSE)
                    && (s == STATE_PLAY || s == STATE_PAUSE);
            state = s;
            switch (s) {
                case STATE_PLAY:
                    playPauseDrawable.setPause(false, animate);
                    break;
                case STATE_PAUSE:
                    playPauseDrawable.setPause(true, animate);
                    break;
                case STATE_REPLAY:
                case STATE_DOWNLOAD:
                case STATE_LOADING:
                    playPauseDrawable.setPause(false, false);
                    break;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy);
            canvas.drawCircle(cx, cy, r, bgPaint);

            if (state == STATE_REPLAY) {
                drawReplay(canvas, cx, cy);
            } else if (state == STATE_DOWNLOAD || state == STATE_LOADING) {
                drawDownload(canvas, cx, cy);
            } else {
                int size = playPauseDrawable.getIntrinsicWidth();
                playPauseDrawable.setBounds(
                        (int) (cx - size / 2f), (int) (cy - size / 2f),
                        (int) (cx + size / 2f), (int) (cy + size / 2f));
                playPauseDrawable.draw(canvas);
            }
        }

        private void drawDownload(Canvas c, float cx, float cy) {
            float s = dp(7f);

            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(dp(2f));
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);

            c.drawLine(cx, cy - s * 0.9f, cx, cy + s * 0.15f, iconPaint);

            path.reset();
            path.moveTo(cx - s * 0.55f, cy - s * 0.05f);
            path.lineTo(cx, cy + s * 0.6f);
            path.lineTo(cx + s * 0.55f, cy - s * 0.05f);
            c.drawPath(path, iconPaint);

            c.drawLine(cx - s * 0.8f, cy + s * 0.95f, cx + s * 0.8f, cy + s * 0.95f, iconPaint);
        }

        private void drawReplay(Canvas c, float cx, float cy) {
            float arcR = dp(8);
            float sw = dp(2f);

            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(sw);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            tmpRect.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR);
            c.drawArc(tmpRect, -70, 320, false, iconPaint);

            iconPaint.setStyle(Paint.Style.FILL);
            iconPaint.setStrokeCap(Paint.Cap.BUTT);
            float aRad = (float) Math.toRadians(-70);
            float px = cx + arcR * (float) Math.cos(aRad);
            float py = cy + arcR * (float) Math.sin(aRad);
            float as = dp(4f);
            float tx = -(float) Math.sin(aRad);
            float ty = (float) Math.cos(aRad);
            float nx = (float) Math.cos(aRad);
            float ny = (float) Math.sin(aRad);

            path.reset();
            path.moveTo(px + tx * as, py + ty * as);
            path.lineTo(px - tx * as * 0.3f + nx * as * 0.65f,
                    py - ty * as * 0.3f + ny * as * 0.65f);
            path.lineTo(px - tx * as * 0.3f - nx * as * 0.65f,
                    py - ty * as * 0.3f - ny * as * 0.65f);
            path.close();
            c.drawPath(path, iconPaint);
        }
    }

    static class SpeedBadgeView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String label = "1×";

        SpeedBadgeView(Context context) {
            super(context);
            bgPaint.setColor(0x7F000000);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(dp(12));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(AndroidUtilities.bold());
        }

        void setSpeed(float speed) {
            label = (speed == (int) speed) ? ((int) speed + "×") : (speed + "×");
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            canvas.drawCircle(cx, cy, Math.min(cx, cy), bgPaint);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
        }
    }

    @SuppressLint("DefaultLocale")
    static class TimestampLabel extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String text = "";
        private int lastWidth = -1;

        TimestampLabel(Context context) {
            super(context);
            bgPaint.setColor(0x66000000);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(dp(11));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setTime(int posMs, int durMs) {
            int ps = posMs / 1000, ds = durMs / 1000;
            String newText = String.format("%d:%02d / %d:%02d",
                    ps / 60, ps % 60, ds / 60, ds % 60);
            if (newText.equals(text)) return;
            text = newText;

            int w = (int) textPaint.measureText(text) + dp(16);
            int h = dp(20);
            if (getLayoutParams() != null) {
                boolean sizeChanged = getLayoutParams().width != w
                        || getLayoutParams().height != h;
                getLayoutParams().width = w;
                getLayoutParams().height = h;
                if (sizeChanged) {
                    requestLayout();
                }
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = getHeight() / 2f;
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, bgPaint);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
        }

        public int getLastWidth() {
            return lastWidth;
        }

        public void setLastWidth(int lastWidth) {
            this.lastWidth = lastWidth;
        }
    }

    private void enterSpeedUpMode() {
        touchState = TOUCH_SPEEDUP;
        savedSpeedIndex = speedIndex;
        speedIndex = SPEEDS.length - 1;
        applySpeed();
        speedBadge.setSpeed(SPEEDS[speedIndex]);

        speedBadge.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start();

        if (mediaPlayer != null && isPrepared) {
            try {
                progressView.sync(mediaPlayer.getCurrentPosition(),
                        mediaPlayer.getDuration(), SPEEDS[speedIndex]);
            } catch (Exception ignored) {}
        }

        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    private void exitSpeedUpMode() {
        speedIndex = savedSpeedIndex;
        applySpeed();
        speedBadge.setSpeed(SPEEDS[speedIndex]);

        speedBadge.animate().scaleX(1f).scaleY(1f).setDuration(150).start();

        if (mediaPlayer != null && isPrepared) {
            try {
                progressView.sync(mediaPlayer.getCurrentPosition(),
                        mediaPlayer.getDuration(), SPEEDS[speedIndex]);
            } catch (Exception ignored) {}
        }

        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
    }

    public static boolean isActiveSeeking() {
        FeedRoundVideoView v = getActivePlayer();
        return v != null && v.touchState == TOUCH_SEEKING;
    }

    private TLRPC.Document getCurrentDocument() {
        return currentMessage != null
                && currentMessage.messageOwner != null
                && currentMessage.messageOwner.media != null
                ? currentMessage.messageOwner.media.document
                : null;
    }

    private void refreshFileStateUi() {
        TLRPC.Document doc = getCurrentDocument();
        if (doc == null) {
            playButton.setState(PlayButtonView.STATE_PLAY);
            progressView.clearDownload();
            progressView.setVisibility(GONE);
            return;
        }

        File file = getVideoFile(doc);
        boolean loaded = file != null && file.exists();
        if (loaded) {
            fileLoading = false;
            fileProgress = 1f;
            progressView.clearDownload();
            progressView.setVisibility(GONE);
            if (!isPrepared && !isPlaying && !finished) {
                playButton.setState(PlayButtonView.STATE_PLAY);
            }
            return;
        }

        fileLoading = currentFileName != null
                && FileLoader.getInstance(currentAccount).isLoadingFile(currentFileName);

        if (!fileLoading) {
            fileProgress = 0f;
            progressView.clearDownload();
            progressView.setVisibility(GONE);
            playButton.setState(PlayButtonView.STATE_DOWNLOAD);
        } else {
            playButton.setState(PlayButtonView.STATE_LOADING);
            progressView.setVisibility(VISIBLE);
            progressView.setDownloadProgress(fileProgress);
        }
    }

    private void requestCurrentDownload(int priority) {
        TLRPC.Document doc = getCurrentDocument();
        if (doc == null || currentMessage == null) return;

        FileLoader.getInstance(currentAccount)
                .loadFile(doc, currentMessage, priority, 0);

        fileLoading = true;
        playButton.setState(PlayButtonView.STATE_LOADING);
        progressView.setVisibility(VISIBLE);
        progressView.setDownloadProgress(fileProgress);
    }

    private boolean isCurrentFileEvent(Object... args) {
        return currentFileName != null
                && args != null
                && args.length > 0
                && currentFileName.equals(args[0]);
    }

    private float extractFileProgress(Object... args) {
        if (args == null || args.length < 2) return 0f;

        if (args[1] instanceof Float) {
            return Math.max(0f, Math.min(1f, (Float) args[1]));
        }

        if (args.length >= 3 && args[1] instanceof Long && args[2] instanceof Long) {
            long loaded = (Long) args[1];
            long total = (Long) args[2];
            if (total > 0) {
                return Math.max(0f, Math.min(1f, (float) loaded / total));
            }
        }

        return 0f;
    }

    private void onCurrentFileLoaded() {
        fileLoading = false;
        fileProgress = 1f;
        progressView.clearDownload();
        progressView.setVisibility(GONE);

        if (!isPrepared && !isPlaying && !finished) {
            playButton.setState(PlayButtonView.STATE_PLAY);
        }

        if (pendingPlayAfterDownload) {
            pendingPlayAfterDownload = false;
            play();
        }
    }
}