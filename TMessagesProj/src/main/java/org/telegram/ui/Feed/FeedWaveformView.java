package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

@SuppressLint("ViewConstructor")
public class FeedWaveformView extends View {
    private float[] bars;
    private float progress = 0f;
    private boolean seeking = false;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPlayedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    public interface SeekListener {
        void onSeek(float progress);
        void onSeekStart();
        void onSeekEnd(float progress);
    }

    private SeekListener seekListener;

    public FeedWaveformView(Context context, int accentColor) {
        super(context);
        barPaint.setColor((accentColor & 0x00FFFFFF) | 0x44000000);
        barPlayedPaint.setColor(accentColor);
    }

    public void setSeekListener(SeekListener l) {
        seekListener = l;
    }

    public void setProgress(float p) {
        if (!seeking) {
            progress = Math.max(0, Math.min(1, p));
            invalidate();
        }
    }

    public void setWaveform(byte[] waveform) {
        progress = 0;
        if (waveform == null || waveform.length == 0) {
            bars = null;
        } else {
            int count = waveform.length * 8 / 5;
            bars = new float[count];
            for (int i = 0; i < count; i++) {
                int byteIndex = i * 5 / 8;
                int bitShift = i * 5 % 8;
                int val = (waveform[byteIndex] & 0xFF) >> bitShift;
                if (bitShift > 3 && byteIndex + 1 < waveform.length) {
                    val |= (waveform[byteIndex + 1] & 0xFF) << (8 - bitShift);
                }
                bars[i] = (val & 0x1F) / 31f;
            }
        }
        invalidate();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (bars == null || bars.length == 0) return false;
        float w = getWidth();
        if (w <= 0) return false;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                seeking = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                progress = Math.max(0, Math.min(1, event.getX() / w));
                invalidate();
                if (seekListener != null) seekListener.onSeekStart();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (seeking) {
                    progress = Math.max(0, Math.min(1, event.getX() / w));
                    invalidate();
                    if (seekListener != null) seekListener.onSeek(progress);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (seeking) {
                    seeking = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    progress = Math.max(0, Math.min(1, event.getX() / w));
                    invalidate();
                    if (seekListener != null) seekListener.onSeekEnd(progress);
                }
                return true;
        }
        return false;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        if (bars == null || bars.length == 0 || w <= 0) {
            float cy = h / 2f;
            float playedW = w * progress;
            if (playedW > 0) {
                canvas.drawRect(0, cy - dp(1), playedW, cy + dp(1), barPlayedPaint);
            }
            if (playedW < w) {
                canvas.drawRect(playedW, cy - dp(1), w, cy + dp(1), barPaint);
            }
            return;
        }

        float barW = dp(2);
        float gap = dp(1.5f);
        float step = barW + gap;
        int visibleBars = Math.max(1, (int) (w / step));
        float minH = dp(2);
        float maxH = h - dp(4);
        float progressX = w * progress;

        for (int i = 0; i < visibleBars; i++) {
            int di = i * bars.length / visibleBars;
            if (di >= bars.length) di = bars.length - 1;

            float barH = Math.max(minH, bars[di] * maxH);
            float x = i * step;
            float top = (h - barH) / 2f;

            barRect.set(x, top, x + barW, top + barH);
            float barCenter = x + barW / 2f;
            canvas.drawRoundRect(barRect, barW / 2f, barW / 2f,
                    barCenter <= progressX ? barPlayedPaint : barPaint);
        }
    }
}