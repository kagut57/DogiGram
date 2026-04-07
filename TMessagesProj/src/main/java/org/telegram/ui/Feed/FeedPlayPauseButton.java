package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.PlayPauseDrawable;

@SuppressLint("ViewConstructor")
public class FeedPlayPauseButton extends View {

    public static final int STATE_PLAY = 0;
    public static final int STATE_PAUSE = 1;
    public static final int STATE_DOWNLOAD = 2;
    public static final int STATE_LOADING = 3;

    private int state = STATE_PLAY;
    private float loadingProgress;
    private float spinnerAngle;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF progressRect = new RectF();
    private final Path downloadPath = new Path();

    private final PlayPauseDrawable playPauseDrawable;

    public FeedPlayPauseButton(Context context, int bgColor) {
        super(context);

        bgPaint.setColor(bgColor);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(2f));
        trackPaint.setColor(0x55FFFFFF);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(dp(2.25f));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(0xFFFFFFFF);

        iconPaint.setColor(0xFFFFFFFF);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(2f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        playPauseDrawable = new PlayPauseDrawable(14);
        playPauseDrawable.setCallback(this);
        playPauseDrawable.setColor(0xFFFFFFFF);
        playPauseDrawable.setPause(false, false);
    }

    public void setPlaying(boolean playing) {
        setState(playing ? STATE_PAUSE : STATE_PLAY);
    }

    public void setState(int newState) {
        if (state == newState) return;

        boolean animatePlayPause =
                (state == STATE_PLAY || state == STATE_PAUSE) &&
                        (newState == STATE_PLAY || newState == STATE_PAUSE);

        state = newState;

        if (newState == STATE_PLAY) {
            playPauseDrawable.setPause(false, animatePlayPause);
        } else if (newState == STATE_PAUSE) {
            playPauseDrawable.setPause(true, animatePlayPause);
        }

        invalidate();
    }

    public int getState() {
        return state;
    }

    public void setLoadingProgress(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (loadingProgress == clamped) return;
        loadingProgress = clamped;
        if (state == STATE_LOADING) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy);

        canvas.drawCircle(cx, cy, r, bgPaint);

        if (state == STATE_LOADING) {
            float inset = dp(4f);
            progressRect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(progressRect, 0, 360, false, trackPaint);

            if (loadingProgress > 0f) {
                float sweep = Math.max(10f, 360f * loadingProgress);
                canvas.drawArc(progressRect, -90, sweep, false, progressPaint);
            } else {
                spinnerAngle += 7f;
                canvas.drawArc(progressRect, spinnerAngle - 90, 80f, false, progressPaint);
                postInvalidateOnAnimation();
            }
        }

        if (state == STATE_PLAY || state == STATE_PAUSE) {
            int size = dp(16);
            playPauseDrawable.setBounds(
                    (int) (cx - size), (int) (cy - size),
                    (int) (cx + size), (int) (cy + size));
            playPauseDrawable.draw(canvas);
        } else {
            drawDownloadIcon(canvas, cx, cy);
        }
    }

    private void drawDownloadIcon(Canvas canvas, float cx, float cy) {
        float s = dp(7f);

        canvas.drawLine(cx, cy - s * 0.9f, cx, cy + s * 0.15f, iconPaint);

        downloadPath.reset();
        downloadPath.moveTo(cx - s * 0.55f, cy - s * 0.05f);
        downloadPath.lineTo(cx, cy + s * 0.6f);
        downloadPath.lineTo(cx + s * 0.55f, cy - s * 0.05f);
        canvas.drawPath(downloadPath, iconPaint);

        canvas.drawLine(cx - s * 0.8f, cy + s * 0.95f, cx + s * 0.8f, cy + s * 0.95f, iconPaint);
    }

    @Override
    protected boolean verifyDrawable(@NonNull android.graphics.drawable.Drawable who) {
        return who == playPauseDrawable || super.verifyDrawable(who);
    }
}