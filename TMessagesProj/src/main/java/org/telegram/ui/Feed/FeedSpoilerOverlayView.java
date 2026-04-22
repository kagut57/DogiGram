package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

import java.util.ArrayList;
import java.util.Stack;

@SuppressLint("ViewConstructor")
public class FeedSpoilerOverlayView extends View {

    private BackupImageView sourceImageView;
    private boolean hasSpoiler = false;
    private boolean isRevealed = false;
    private float revealProgress = 0f;

    private android.animation.ValueAnimator animator;
    private Runnable onRevealedCallback;

    private final ArrayList<SpoilerEffect> spoilerEffects = new ArrayList<>();
    private final Stack<SpoilerEffect> spoilerPool = new Stack<>();

    private Bitmap blurBitmap;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private static android.text.TextPaint labelPaint;

    public FeedSpoilerOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
    }

    public void setSourceImageView(BackupImageView iv) {
        this.sourceImageView = iv;
    }

    public void setSpoiler(boolean hasSpoiler) {
        this.hasSpoiler = hasSpoiler;
        this.isRevealed = false;
        this.revealProgress = 0f;
        if (!hasSpoiler) {
            cleanup();
        }
        invalidate();
    }

    public boolean isSpoilerVisible() {
        return hasSpoiler && !isRevealed;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (hasSpoiler && !isRevealed && w > 0 && h > 0) {
            buildBlur(w, h);
            initParticles(w, h);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!hasSpoiler || isRevealed) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float alpha = 1f - revealProgress;

        if (blurBitmap == null && sourceImageView != null) {
            buildBlur(w, h);
        }

        canvas.save();
        rect.set(0, 0, w, h);

        if (blurBitmap != null && !blurBitmap.isRecycled()) {
            bitmapPaint.setAlpha((int) (255 * alpha));
            canvas.drawBitmap(blurBitmap, null, rect, bitmapPaint);
        } else {
            dimPaint.setColor(0xFF1A1A2E);
            dimPaint.setAlpha((int) (255 * alpha));
            canvas.drawRect(rect, dimPaint);
        }

        if (!spoilerEffects.isEmpty()) {
            for (SpoilerEffect eff : spoilerEffects) {
                eff.setAlpha((int) (255 * alpha));
                eff.draw(canvas);
            }
            invalidate();
        }

        drawLabel(canvas, w, h, alpha);

        canvas.restore();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && isSpoilerVisible()) {
            reveal();
            return true;
        }
        return super.onTouchEvent(event);
    }

    void reveal() {
        if (isRevealed) return;
        isRevealed = true;

        if (!spoilerEffects.isEmpty()) {
            SpoilerEffect eff = spoilerEffects.get(0);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float maxR = (float) Math.sqrt(cx * cx + cy * cy);
            eff.startRipple(cx, cy, maxR);
        }

        animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(400);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(anim -> {
            revealProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                revealProgress = 1f;
                cleanup();
                invalidate();
                if (onRevealedCallback != null) onRevealedCallback.run();
            }
        });
        animator.start();
    }

    private void buildBlur(int w, int h) {
        try {
            if (sourceImageView == null) return;
            Bitmap src = sourceImageView.getImageReceiver().getBitmap();
            if (src == null || src.isRecycled()) return;

            int blurW = Math.max(1, w / 8);
            int blurH = Math.max(1, h / 8);

            blurBitmap = Bitmap.createScaledBitmap(src, blurW, blurH, true);
            Utilities.stackBlurBitmap(blurBitmap, 8);
        } catch (Exception e) {
            blurBitmap = null;
        }
    }

    private void initParticles(int w, int h) {
        spoilerEffects.clear();
        SpoilerEffect eff = spoilerPool.isEmpty() ? new SpoilerEffect() : spoilerPool.pop();
        eff.setBounds(0, 0, w, h);
        eff.setColor(0xFFFFFFFF);
        eff.setParentView(this);
        eff.setMaxParticlesCount(SpoilerEffect.MAX_PARTICLES_PER_ENTITY);
        eff.updateMaxParticles();
        spoilerEffects.add(eff);
    }

    private void drawLabel(Canvas canvas, int w, int h, float alpha) {
        if (labelPaint == null) {
            labelPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(0xFFFFFFFF);
            labelPaint.setTextSize(dp(14));
            labelPaint.setTypeface(AndroidUtilities.bold());
            labelPaint.setTextAlign(Paint.Align.CENTER);
        }

        String label = "Tap to view";
        float cx = w / 2f;
        float cy = h / 2f;
        float textW = labelPaint.measureText(label) + dp(24);
        float textH = dp(32);
        float r = textH / 2f;

        rect.set(cx - textW / 2f, cy - textH / 2f, cx + textW / 2f, cy + textH / 2f);

        overlayPaint.setColor(0x55000000);
        overlayPaint.setAlpha((int) (0x88 * alpha));
        canvas.drawRoundRect(rect, r, r, overlayPaint);

        labelPaint.setAlpha((int) (255 * alpha));
        canvas.drawText(label, cx, cy + dp(5), labelPaint);
    }

    private void cleanup() {
        spoilerEffects.clear();
        if (blurBitmap != null) {
            blurBitmap.recycle();
            blurBitmap = null;
        }
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cleanup();
    }
}