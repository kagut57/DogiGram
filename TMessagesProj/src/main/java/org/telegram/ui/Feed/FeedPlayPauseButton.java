package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

@SuppressLint("ViewConstructor")
public class FeedPlayPauseButton extends View {
    private boolean playing;
    private float animProgress = 0f;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path iconPath = new android.graphics.Path();
    private android.animation.ValueAnimator animator;

    public FeedPlayPauseButton(Context context, int bgColor) {
        super(context);
        bgPaint.setColor(bgColor);
        iconPaint.setColor(0xFFFFFFFF);
        iconPaint.setStyle(Paint.Style.FILL);
    }

    public void setPlaying(boolean p) {
        if (playing == p) return;
        playing = p;
        if (animator != null) animator.cancel();
        animator = android.animation.ValueAnimator.ofFloat(animProgress, playing ? 1f : 0f);
        animator.setDuration(200);
        animator.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy);
        canvas.drawCircle(cx, cy, r, bgPaint);

        float t = animProgress;

        float px1 = cx - dp(3), py1 = cy - dp(6);
        float px2 = cx + dp(7), py2 = cy;
        float px3 = cx - dp(3), py3 = cy + dp(6);

        float bw = dp(2.5f);
        float bh = dp(10);
        float gap = dp(1.5f);

        float l1x1 = lerp(px1, cx - gap - bw, t);
        float l1y1 = lerp(py1, cy - bh / 2, t);
        float l1x2 = lerp(px2, cx - gap, t);
        float l1y2 = lerp(py2, cy + bh / 2, t);

        float r1x1 = lerp(px1, cx + gap, t);
        float r1y1 = lerp(py3, cy - bh / 2, t);
        float r1x2 = lerp(px2, cx + gap + bw, t);
        float r1y2 = lerp(py2, cy + bh / 2, t);

        if (t < 0.5f) {
            iconPath.reset();
            iconPath.moveTo(l1x1, l1y1);
            iconPath.lineTo(l1x2, (l1y2 + l1y1) / 2f);
            iconPath.lineTo(r1x1, r1y1);
            iconPath.close();
            canvas.drawPath(iconPath, iconPaint);
        } else {
            canvas.drawRoundRect(l1x1, l1y1, lerp(px1 + dp(4), cx - gap, t),
                    l1y2, dp(1), dp(1), iconPaint);
            canvas.drawRoundRect(lerp(px1 + dp(4), cx + gap, t), r1y1,
                    r1x2, r1y2, dp(1), dp(1), iconPaint);
        }
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}