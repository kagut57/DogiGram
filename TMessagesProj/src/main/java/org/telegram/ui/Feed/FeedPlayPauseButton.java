package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.PlayPauseDrawable;

@SuppressLint("ViewConstructor")
public class FeedPlayPauseButton extends View {
    private boolean playing;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PlayPauseDrawable playPauseDrawable;

    public FeedPlayPauseButton(Context context, int bgColor) {
        super(context);
        bgPaint.setColor(bgColor);
        playPauseDrawable = new PlayPauseDrawable(14);
        playPauseDrawable.setCallback(this);
        playPauseDrawable.setColor(0xFFFFFFFF);
    }

    public void setPlaying(boolean p) {
        if (playing == p) return;
        playing = p;
        playPauseDrawable.setPause(playing, true);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy);
        canvas.drawCircle(cx, cy, r, bgPaint);

        int size = dp(16);
        playPauseDrawable.setBounds(
                (int) (cx - size), (int) (cy - size),
                (int) (cx + size), (int) (cy + size));
        playPauseDrawable.draw(canvas);
    }

    @Override
    protected boolean verifyDrawable(@NonNull android.graphics.drawable.Drawable who) {
        return who == playPauseDrawable || super.verifyDrawable(who);
    }
}