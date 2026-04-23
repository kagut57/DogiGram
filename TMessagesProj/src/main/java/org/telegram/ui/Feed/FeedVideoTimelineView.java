package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;

import java.util.Locale;

public class FeedVideoTimelineView extends View {

    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF badgeRect = new RectF();

    private float progress = 0f;
    private long totalTime = 0;

    private final Drawable muteIcon;

    @SuppressLint("UseCompatLoadingForDrawables")
    public FeedVideoTimelineView(Context context) {
        super(context);
        backgroundPaint.setColor(0x4D000000);
        progressPaint.setColor(0xFFFFFFFF);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(dp(11));
        textPaint.setFakeBoldText(true);

        badgePaint.setColor(0xCC000000);

        Drawable icon = null;
        try {
            icon = context.getResources().getDrawable(R.drawable.video_muted).mutate();
            icon.setColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
        } catch (Exception ignored) {}
        muteIcon = icon;
    }

    public void setProgress(float progress, long totalTime) {
        if (Math.abs(this.progress - progress) > 0.001f || this.totalTime != totalTime) {
            this.progress = progress;
            this.totalTime = totalTime;
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();

        float lineY = h - dp(2);
        canvas.drawRect(0, lineY, w, h, backgroundPaint);
        canvas.drawRect(0, lineY, w * progress, h, progressPaint);

        String timeStr = formatTime(totalTime);
        float textW = textPaint.measureText(timeStr);
        float textHeight = textPaint.getFontMetrics().descent - textPaint.getFontMetrics().ascent;

        int iconSize = dp(14);
        int badgePad = dp(5);
        int innerPad = dp(4);

        float badgeWidth = badgePad + iconSize + innerPad + textW + badgePad;
        float badgeHeight = iconSize + badgePad * 2;

        float badgeLeft = dp(8);
        float badgeBottom = lineY - dp(2);
        float badgeTop = badgeBottom - badgeHeight;
        float badgeRight = badgeLeft + badgeWidth;

        badgeRect.set(badgeLeft, badgeTop, badgeRight, badgeBottom);

        float radius = dp(8);
        canvas.drawRoundRect(badgeRect, radius, radius, badgePaint);

        if (muteIcon != null) {
            float iconX = badgeLeft + badgePad;
            float iconY = badgeTop + (badgeHeight - iconSize) / 2f;
            muteIcon.setBounds((int) iconX, (int) iconY, (int) (iconX + iconSize), (int) (iconY + iconSize));
            muteIcon.draw(canvas);
        }

        float textX = badgeLeft + badgePad + iconSize + innerPad;
        float textY = badgeTop + (badgeHeight + textHeight) / 2f - textPaint.getFontMetrics().descent;
        canvas.drawText(timeStr, textX, textY, textPaint);
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "0:00";
        int m = (int) (seconds / 60);
        int s = (int) (seconds % 60);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(36), MeasureSpec.EXACTLY));
    }
}