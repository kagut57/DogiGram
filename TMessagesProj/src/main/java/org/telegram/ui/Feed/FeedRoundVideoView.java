package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Outline;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

@SuppressLint("ViewConstructor")
public class FeedRoundVideoView extends FrameLayout {

    private static final int CIRCLE_COLLAPSED = dp(240);
    private static final int CIRCLE_EXPANDED  = dp(300);
    private static final long EXPAND_DURATION = 250;       // ms
    private static final float[] SPEED_OPTIONS = {1f, 1.5f, 2f};

    private final int currentAccount;
    private final BackupImageView thumbView;
    private final TextureView textureView;
    private final PlayButtonView playButton;
    private final SeekProgressView progressView;
    private final SpeedButtonView speedButton;
    private final TimestampView timestampView;

    private MediaPlayer mediaPlayer;
    private MessageObject currentMessage;
    private boolean isPlaying   = false;
    private boolean isPrepared  = false;
    private boolean surfaceReady = false;
    private boolean finished    = false;

    private int speedIndex = 0;

    private int currentCircleSize = CIRCLE_COLLAPSED;
    private ValueAnimator expandAnimator;
    private boolean isExpanded = false;

    private boolean isSeeking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying && isPrepared) {
                try {
                    int pos = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();
                    if (dur > 0) {
                        float p = (float) pos / dur;
                        progressView.setProgress(p);
                        timestampView.setTime(pos, dur);
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(this, 50);
            }
        }
    };

    public interface SizeChangeListener {
        void onRoundVideoSizeChanged(int newSizeDp);
    }
    private SizeChangeListener sizeChangeListener;
    public void setSizeChangeListener(SizeChangeListener l) { sizeChangeListener = l; }

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
            @Override public void getOutline(View v, Outline outline) {
                outline.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        textureView.setClipToOutline(true);
        textureView.setVisibility(GONE);
        addView(textureView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new SeekProgressView(context);
        progressView.setVisibility(GONE);
        addView(progressView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        playButton = new PlayButtonView(context);
        addView(playButton, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        speedButton = new SpeedButtonView(context);
        speedButton.setVisibility(GONE);
        addView(speedButton, LayoutHelper.createFrame(
                36, 36, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 8, 8));
        speedButton.setOnClickListener(v -> cycleSpeed());

        timestampView = new TimestampView(context);
        timestampView.setVisibility(GONE);
        addView(timestampView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        setOnClickListener(v -> togglePlayback());

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture s, int w, int h) {
                surfaceReady = true;
                if (mediaPlayer != null && isPrepared)
                    mediaPlayer.setSurface(new android.view.Surface(s));
            }
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) {
                surfaceReady = false; return true;
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

        if (msg == null) { setVisibility(GONE); return; }

        TLRPC.Document doc = msg.messageOwner.media != null ? msg.messageOwner.media.document : null;
        if (doc == null) { setVisibility(GONE); return; }

        thumbView.setRoundRadius(CIRCLE_COLLAPSED / 2);
        if (doc.thumbs != null && !doc.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumb = FeedMediaHelper.bestSize(doc.thumbs);
            if (thumb != null)
                thumbView.setImage(ImageLocation.getForDocument(thumb, doc),
                        CIRCLE_COLLAPSED + "_" + CIRCLE_COLLAPSED, null, null, 0, doc);
        }

        resetToCollapsed(false);
        setVisibility(VISIBLE);
        FileLoader.getInstance(currentAccount).loadFile(doc, msg, FileLoader.PRIORITY_NORMAL, 0);
    }

    public void release() {
        handler.removeCallbacks(progressUpdater);
        isPlaying = false;
        isPrepared = false;
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void togglePlayback() {
        if (currentMessage == null) return;
        if (finished) { replay(); return; }
        if (isPlaying) pause(); else play();
    }

    private void play() {
        if (currentMessage == null) return;

        TLRPC.Document doc = currentMessage.messageOwner.media != null
                ? currentMessage.messageOwner.media.document : null;
        if (doc == null) return;

        File file = getVideoFile(doc);
        if (file == null || !file.exists()) {
            FileLoader.getInstance(currentAccount).loadFile(doc, currentMessage,
                    FileLoader.PRIORITY_HIGH, 0);
            return;
        }

        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.start();
            isPlaying = true;
            playButton.setVisibility(GONE);
            speedButton.setVisibility(VISIBLE);
            timestampView.setVisibility(VISIBLE);
            handler.post(progressUpdater);
            animateExpand(true);
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
                if (surfaceReady && textureView.getSurfaceTexture() != null)
                    mp.setSurface(new android.view.Surface(textureView.getSurfaceTexture()));

                applySpeed();
                mp.start();
                isPlaying = true;

                thumbView.setVisibility(GONE);
                textureView.setVisibility(VISIBLE);
                playButton.setVisibility(GONE);
                progressView.setVisibility(VISIBLE);
                speedButton.setVisibility(VISIBLE);
                timestampView.setVisibility(VISIBLE);

                handler.post(progressUpdater);
                animateExpand(true);
            });

            mediaPlayer.setOnCompletionListener(mp -> onPlaybackFinished());

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                release();
                resetToCollapsed(true);
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
            playButton.setPlaying();
            playButton.setVisibility(VISIBLE);
            handler.removeCallbacks(progressUpdater);
        }
    }

    private void onPlaybackFinished() {
        isPlaying = false;
        finished = true;
        handler.removeCallbacks(progressUpdater);
        progressView.setProgress(1f);

        playButton.setReplay(true);
        playButton.setVisibility(VISIBLE);
        speedButton.setVisibility(GONE);

        animateExpand(false);
    }

    private void replay() {
        finished = false;
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.seekTo(0);
            mediaPlayer.start();
            isPlaying = true;
            playButton.setVisibility(GONE);
            speedButton.setVisibility(VISIBLE);
            timestampView.setVisibility(VISIBLE);
            handler.post(progressUpdater);
            animateExpand(true);
        } else {
            play();
        }
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEED_OPTIONS.length;
        applySpeed();
        speedButton.setSpeed(SPEED_OPTIONS[speedIndex]);
    }

    private void applySpeed() {
        if (mediaPlayer == null || !isPrepared) return;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                boolean wasPlaying = mediaPlayer.isPlaying();
                mediaPlayer.setPlaybackParams(
                        mediaPlayer.getPlaybackParams().setSpeed(SPEED_OPTIONS[speedIndex]));
                if (!wasPlaying) mediaPlayer.pause();
            } catch (Exception ignored) {}
        }
    }

    private void animateExpand(boolean expand) {
        if (expand == isExpanded) return;
        isExpanded = expand;

        int from = currentCircleSize;
        int to   = expand ? CIRCLE_EXPANDED : CIRCLE_COLLAPSED;

        if (expandAnimator != null) expandAnimator.cancel();
        expandAnimator = ValueAnimator.ofInt(from, to);
        expandAnimator.setDuration(EXPAND_DURATION);
        expandAnimator.addUpdateListener(a -> {
            currentCircleSize = (int) a.getAnimatedValue();
            applyCircleSize(currentCircleSize);
        });
        expandAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { expandAnimator = null; }
        });
        expandAnimator.start();
    }

    private void applyCircleSize(int size) {
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) return;
        lp.width  = size;
        lp.height = size;
        setLayoutParams(lp);
        thumbView.setRoundRadius(size / 2);
        if (sizeChangeListener != null) sizeChangeListener.onRoundVideoSizeChanged(size);
    }

    private void resetToCollapsed(boolean showThumb) {
        currentCircleSize = CIRCLE_COLLAPSED;
        isExpanded = false;
        applyCircleSize(CIRCLE_COLLAPSED);

        thumbView.setVisibility(showThumb ? VISIBLE : VISIBLE);
        textureView.setVisibility(GONE);
        playButton.setPlaying();
        playButton.setReplay(false);
        playButton.setVisibility(VISIBLE);
        progressView.setVisibility(GONE);
        progressView.setProgress(0);
        speedButton.setVisibility(GONE);
        timestampView.setVisibility(GONE);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (progressView.getVisibility() != VISIBLE) return super.onInterceptTouchEvent(ev);
        if (ev.getAction() == MotionEvent.ACTION_DOWN && isOnRing(ev)) {
            isSeeking = true;
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isSeeking) {
            float cx = getWidth()  / 2f;
            float cy = getHeight() / 2f;
            double angle = Math.atan2(ev.getY() - cy, ev.getX() - cx);
            float progress = (float) ((angle + Math.PI / 2) / (2 * Math.PI));
            if (progress < 0) progress += 1f;

            switch (ev.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    progressView.setProgress(progress);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isSeeking = false;
                    seekTo(progress);
                    if (!isPlaying && !finished) {
                        if (mediaPlayer != null && isPrepared) {
                            mediaPlayer.start();
                            isPlaying = true;
                            playButton.setVisibility(GONE);
                            handler.post(progressUpdater);
                        }
                    }
                    break;
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }

    private boolean isOnRing(MotionEvent ev) {
        float cx = getWidth()  / 2f;
        float cy = getHeight() / 2f;
        float dist = (float) Math.hypot(ev.getX() - cx, ev.getY() - cy);
        float radius = Math.min(cx, cy);
        float ringWidth = dp(24);
        return dist >= radius - ringWidth && dist <= radius + dp(8);
    }

    private void seekTo(float progress) {
        if (mediaPlayer != null && isPrepared) {
            int duration = mediaPlayer.getDuration();
            int target = (int) (progress * duration);
            mediaPlayer.seekTo(target);
            if (finished) {
                finished = false;
                mediaPlayer.start();
                isPlaying = true;
                playButton.setVisibility(GONE);
                speedButton.setVisibility(VISIBLE);
                handler.post(progressUpdater);
                animateExpand(true);
            }
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
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
        resetToCollapsed(true);
    }

    private static class PlayButtonView extends View {
        private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path  iconPath  = new Path();
        private boolean showReplay = false;

        PlayButtonView(Context context) {
            super(context);
            bgPaint.setColor(0x7F000000);
            iconPaint.setColor(0xFFFFFFFF);
            iconPaint.setStyle(Paint.Style.FILL);
        }

        void setPlaying() { showReplay = false; invalidate(); }
        void setReplay(boolean replay)   { showReplay = replay; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy);
            canvas.drawCircle(cx, cy, r, bgPaint);

            iconPath.reset();
            if (showReplay) {
                iconPaint.setStyle(Paint.Style.STROKE);
                iconPaint.setStrokeWidth(dp(2));
                @SuppressLint("DrawAllocation") RectF arc = new RectF(cx - r * 0.35f, cy - r * 0.35f,
                        cx + r * 0.35f, cy + r * 0.35f);
                canvas.drawArc(arc, -90, 300, false, iconPaint);
                iconPaint.setStyle(Paint.Style.FILL);
                float tx = cx, ty = cy - r * 0.35f;
                iconPath.moveTo(tx - dp(4), ty - dp(4));
                iconPath.lineTo(tx + dp(4), ty);
                iconPath.lineTo(tx - dp(4), ty + dp(4));
                iconPath.close();
                canvas.drawPath(iconPath, iconPaint);
            } else {
                iconPaint.setStyle(Paint.Style.FILL);
                float s = r * 0.6f, ox = s * 0.15f;
                iconPath.moveTo(cx - s * 0.4f + ox, cy - s * 0.5f);
                iconPath.lineTo(cx - s * 0.4f + ox, cy + s * 0.5f);
                iconPath.lineTo(cx + s * 0.5f + ox, cy);
                iconPath.close();
                canvas.drawPath(iconPath, iconPaint);
            }
        }
    }

    private static class SeekProgressView extends View {
        private final Paint bgPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect          = new RectF();
        private float progress = 0f;

        SeekProgressView(Context context) {
            super(context);
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(dp(3));
            bgPaint.setColor(0x40FFFFFF);

            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeWidth(dp(3));
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setColor(0xFFFFFFFF);

            dotPaint.setColor(0xFFFFFFFF);
            dotPaint.setStyle(Paint.Style.FILL);
        }

        void setProgress(float p) { progress = p; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            float stroke = dp(3);
            float inset  = stroke / 2f + dp(2);
            rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

            canvas.drawArc(rect, 0, 360, false, bgPaint);
            float sweep = progress * 360f;
            canvas.drawArc(rect, -90, sweep, false, progressPaint);

            float angle = (float) Math.toRadians(-90 + sweep);
            float dotCx = rect.centerX() + rect.width()  / 2f * (float) Math.cos(angle);
            float dotCy = rect.centerY() + rect.height() / 2f * (float) Math.sin(angle);
            canvas.drawCircle(dotCx, dotCy, dp(5), dotPaint);
        }
    }

    private static class SpeedButtonView extends View {
        private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String label = "1×";

        SpeedButtonView(Context context) {
            super(context);
            bgPaint.setColor(0x7F000000);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(dp(12));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(AndroidUtilities.bold());
        }

        void setSpeed(float speed) {
            if (speed == (int) speed) label = (int) speed + "×";
            else label = speed + "×";
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            canvas.drawCircle(cx, cy, Math.min(cx, cy), bgPaint);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float ty = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, cx, ty, textPaint);
        }
    }

    private static class TimestampView extends View {
        private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String text = "";

        TimestampView(Context context) {
            super(context);
            bgPaint.setColor(0x66000000);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(dp(11));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @SuppressLint("DefaultLocale")
        void setTime(int posMs, int durMs) {
            int ps = posMs / 1000, ds = durMs / 1000;
            text = String.format("%d:%02d / %d:%02d", ps / 60, ps % 60, ds / 60, ds % 60);
            int w = (int) textPaint.measureText(text) + dp(16);
            int h = dp(20);
            if (getLayoutParams() != null) {
                getLayoutParams().width = w;
                getLayoutParams().height = h;
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = getHeight() / 2f;
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, bgPaint);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
        }
    }
}