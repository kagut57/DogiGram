package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSlider;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.SpeedIconDrawable;
import org.telegram.ui.Components.TypefaceSpan;

@SuppressLint("ViewConstructor")
public class FeedPlayerBar extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int BAR_HEIGHT = 36;
    private static final float[] speeds = new float[]{.5f, 1f, 1.2f, 1.5f, 1.7f, 2f};
    private static final float[] toggleSpeeds = new float[]{1.0f, 1.5f, 2f};

    private final Theme.ResourcesProvider resourcesProvider;

    private final PlayPauseDrawable playPauseDrawable;
    private final AudioPlayerAlert.ClippingTextViewSwitcher titleTextView;
    private ActionBarMenuItem playbackSpeedButton;
    private SpeedIconDrawable speedIcon;
    private ActionBarMenuSlider.SpeedSlider speedSlider;
    private ActionBarMenuItem.Item[] speedItems;

    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private MessageObject lastMessageObject;
    private boolean isMusic;
    private boolean visible;
    private boolean slidingSpeed;
    private AnimatorSet animatorSet;
    private float topPadding;
    private final View applyingView;

    private int baseTopPadding = -1;

    private boolean isShowingRoundVideo;

    private final FeedRoundVideoView.RoundVideoStateListener roundVideoListener =
            () -> checkPlayer(false);

    public FeedPlayerBar(Context context, BaseFragment fragment, View applyingView,
                         Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.applyingView = applyingView;
        this.resourcesProvider = resourcesProvider;

        setTag(1);
        visible = false;

        FrameLayout contentFrame = new FrameLayout(context);
        contentFrame.setBackgroundColor(getThemedColor(Theme.key_inappPlayerBackground));
        addView(contentFrame, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, BAR_HEIGHT, Gravity.TOP));

        View selector = new View(context);
        selector.setBackground(Theme.getSelectorDrawable(false));
        contentFrame.addView(selector, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.blockpanel_shadow);
        addView(shadow, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 2, Gravity.TOP, 0, BAR_HEIGHT, 0, 0));

        ImageView playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playPauseDrawable = new PlayPauseDrawable(16);
        playButton.setImageDrawable(playPauseDrawable);
        playButton.setColorFilter(new PorterDuffColorFilter(
                getThemedColor(Theme.key_inappPlayerPlayPause), PorterDuff.Mode.MULTIPLY));
        playButton.setBackground(Theme.createSelectorDrawable(
                getThemedColor(Theme.key_inappPlayerPlayPause) & 0x19ffffff, 1, dp(14)));
        playButton.setOnClickListener(v -> {
            if (isShowingRoundVideo) {
                FeedRoundVideoView player = FeedRoundVideoView.getActivePlayer();
                if (player != null) player.externalTogglePlayback();
                return;
            }
            MediaController mc = MediaController.getInstance();
            MessageObject playing = mc.getPlayingMessageObject();
            if (playing != null) {
                if (mc.isMessagePaused()) mc.playMessage(playing);
                else mc.pauseMessage(playing);
            }
        });
        addView(playButton, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.LEFT, 3, 0, 0, 0));

        titleTextView = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            protected android.widget.TextView createTextView() {
                android.widget.TextView tv = new android.widget.TextView(context);
                tv.setMaxLines(1);
                tv.setLines(1);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
                tv.setTextColor(getThemedColor(Theme.key_inappPlayerTitle));
                return tv;
            }
        };
        addView(titleTextView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, BAR_HEIGHT, Gravity.LEFT | Gravity.TOP,
                37, 0, 36, 0));

        ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.miniplayer_close);
        closeButton.setColorFilter(new PorterDuffColorFilter(
                getThemedColor(Theme.key_inappPlayerClose), PorterDuff.Mode.MULTIPLY));
        closeButton.setBackground(Theme.createSelectorDrawable(
                getThemedColor(Theme.key_inappPlayerClose) & 0x19ffffff, 1, dp(14)));
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setOnClickListener(v -> {
            if (isShowingRoundVideo) {
                FeedRoundVideoView player = FeedRoundVideoView.getActivePlayer();
                if (player != null) player.externalStop();
                return;
            }
            MediaController.getInstance().cleanupPlayer(true, true);
        });
        addView(closeButton, LayoutHelper.createFrame(
                36, 36, Gravity.RIGHT | Gravity.TOP, 0, 0, 4, 0));

        createPlaybackSpeedButton();

        setOnClickListener(v -> {
            if (isShowingRoundVideo) return;
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (fragment != null && messageObject != null) {
                if (messageObject.isMusic()) {
                    if (fragment.getParentActivity() != null) {
                        fragment.showDialog(new AudioPlayerAlert(context, resourcesProvider));
                    }
                }
            }
        });

        setVisibility(GONE);
    }

    private void createPlaybackSpeedButton() {
        playbackSpeedButton = new ActionBarMenuItem(getContext(), null, 0,
                getThemedColor(Theme.key_dialogTextBlack), resourcesProvider);
        playbackSpeedButton.setAdditionalYOffset(dp(24 + 6));
        playbackSpeedButton.setLongClickEnabled(false);
        playbackSpeedButton.setVisibility(GONE);
        playbackSpeedButton.setTag(null);
        playbackSpeedButton.setShowSubmenuByMove(false);
        playbackSpeedButton.setDelegate(id -> {
            if (id < 0 || id >= speeds.length) return;
            if (isShowingRoundVideo) return;
            MediaController.getInstance().setPlaybackSpeed(isMusic, speeds[id]);
        });
        playbackSpeedButton.setIcon(speedIcon = new SpeedIconDrawable(true));

        speedSlider = new ActionBarMenuSlider.SpeedSlider(getContext(), resourcesProvider);
        speedSlider.setRoundRadiusDp(6);
        speedSlider.setDrawShadow(true);
        speedSlider.setOnValueChange((value, isFinal) -> {
            slidingSpeed = !isFinal;
            MediaController.getInstance().setPlaybackSpeed(isMusic, speedSlider.getSpeed(value));
        });

        speedItems = new ActionBarMenuItem.Item[6];
        speedItems[0] = playbackSpeedButton.lazilyAddSubItem(0, R.drawable.msg_speed_slow,
                org.telegram.messenger.LocaleController.getString(R.string.SpeedSlow));
        speedItems[1] = playbackSpeedButton.lazilyAddSubItem(1, R.drawable.msg_speed_normal,
                org.telegram.messenger.LocaleController.getString(R.string.SpeedNormal));
        speedItems[2] = playbackSpeedButton.lazilyAddSubItem(2, R.drawable.msg_speed_medium,
                org.telegram.messenger.LocaleController.getString(R.string.SpeedMedium));
        speedItems[3] = playbackSpeedButton.lazilyAddSubItem(3, R.drawable.msg_speed_fast,
                org.telegram.messenger.LocaleController.getString(R.string.SpeedFast));
        speedItems[4] = playbackSpeedButton.lazilyAddSubItem(4, R.drawable.msg_speed_veryfast,
                org.telegram.messenger.LocaleController.getString(R.string.SpeedVeryFast));
        speedItems[5] = playbackSpeedButton.lazilyAddSubItem(5, R.drawable.msg_speed_superfast,
                org.telegram.messenger.LocaleController.getString(R.string.SpeedSuperFast));

        playbackSpeedButton.setAdditionalXOffset(dp(8));
        addView(playbackSpeedButton, LayoutHelper.createFrame(
                36, 36, Gravity.TOP | Gravity.RIGHT, 0, 0, 36, 0));

        playbackSpeedButton.setOnClickListener(v -> {
            if (isShowingRoundVideo) {
                FeedRoundVideoView player = FeedRoundVideoView.getActivePlayer();
                if (player != null) {
                    player.externalCycleSpeed();
                    updatePlaybackButton(true);
                }
                return;
            }
            float current = MediaController.getInstance().getPlaybackSpeed(isMusic);
            int index = -1;
            for (int i = 0; i < toggleSpeeds.length; ++i) {
                if (current - 0.1f <= toggleSpeeds[i]) {
                    index = i;
                    break;
                }
            }
            index++;
            if (index >= toggleSpeeds.length) index = 0;
            MediaController.getInstance().setPlaybackSpeed(isMusic, toggleSpeeds[index]);
            updatePlaybackButton(true);
        });

        playbackSpeedButton.setOnLongClickListener(view -> {
            if (isShowingRoundVideo) return false;
            float speed = MediaController.getInstance().getPlaybackSpeed(isMusic);
            speedSlider.setSpeed(speed, false);
            speedSlider.setBackgroundColor(Theme.getColor(
                    Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
            playbackSpeedButton.redrawPopup(Theme.getColor(
                    Theme.key_actionBarDefaultSubmenuBackground));
            playbackSpeedButton.updateColor();
            updatePlaybackButton(false);
            playbackSpeedButton.setDimMenu(.3f);
            playbackSpeedButton.toggleSubMenu(speedSlider, null);
            return true;
        });
    }

    private void updatePlaybackButton(boolean animated) {
        if (speedIcon == null) return;
        float speed;
        if (isShowingRoundVideo) {
            speed = FeedRoundVideoView.getActivePlaybackSpeed();
        } else {
            speed = MediaController.getInstance().getPlaybackSpeed(isMusic);
        }
        speedIcon.setValue(speed, animated);

        int color = getThemedColor(!equals(speed, 1.0f)
                ? Theme.key_featuredStickers_addButtonPressed
                : Theme.key_inappPlayerClose);
        speedIcon.setColor(color);
        playbackSpeedButton.setBackground(Theme.createSelectorDrawable(
                color & 0x19ffffff, 1, dp(14)));

        if (!isShowingRoundVideo) {
            boolean isFinal = !slidingSpeed;
            slidingSpeed = false;
            for (int a = 0; a < speedItems.length; a++) {
                if (isFinal && Math.abs(speed - speeds[a]) < 0.05f) {
                    speedItems[a].setColors(
                            getThemedColor(Theme.key_featuredStickers_addButtonPressed),
                            getThemedColor(Theme.key_featuredStickers_addButtonPressed));
                } else {
                    speedItems[a].setColors(
                            getThemedColor(Theme.key_actionBarDefaultSubmenuItem),
                            getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
                }
            }
            speedSlider.setSpeed(speed, animated);
        }
    }

    private boolean equals(float a, float b) {
        return Math.abs(a - b) < 0.05f;
    }

    @Keep
    public float getTopPadding() {
        return topPadding;
    }

    @Keep
    public void setTopPadding(float value) {
        topPadding = value;
        if (applyingView != null) {
            if (baseTopPadding < 0) {
                baseTopPadding = applyingView.getPaddingTop();
            }
            int target = baseTopPadding + (int) value;
            if (applyingView.getPaddingTop() != target) {
                applyingView.setPadding(
                        applyingView.getPaddingLeft(),
                        target,
                        applyingView.getPaddingRight(),
                        applyingView.getPaddingBottom()
                );
            }
        }
    }

    public void checkPlayer(boolean create) {
        MessageObject audioMsg = MediaController.getInstance().getPlayingMessageObject();
        boolean hasAudio = audioMsg != null && audioMsg.getId() != 0 && !audioMsg.isVideo();

        MessageObject roundMsg = FeedRoundVideoView.getActiveMessage();
        boolean hasRound = roundMsg != null;

        if (hasAudio && hasRound) {
            FeedRoundVideoView player = FeedRoundVideoView.getActivePlayer();
            if (player != null) player.externalStop();
            hasRound = false;
        }

        boolean showRound = hasRound && !hasAudio;
        MessageObject displayMsg = showRound ? roundMsg : (hasAudio ? audioMsg : null);

        if (displayMsg == null) {
            isShowingRoundVideo = false;
            if (visible) {
                visible = false;
                lastMessageObject = null;
                if (create) {
                    setVisibility(GONE);
                    setTopPadding(0);
                } else {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(this, "topPadding", 0));
                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animatorSet != null && animatorSet.equals(animation)) {
                                setVisibility(GONE);
                                animatorSet = null;
                            }
                        }
                    });
                    animatorSet.start();
                }
            }
            return;
        }

        isShowingRoundVideo = showRound;

        if (create && topPadding == 0) {
            setTopPadding(AndroidUtilities.dp2(BAR_HEIGHT + 2));
        }

        if (!visible) {
            if (!create) {
                if (animatorSet != null) {
                    animatorSet.cancel();
                    animatorSet = null;
                }
                animatorSet = new AnimatorSet();
                animatorSet.playTogether(ObjectAnimator.ofFloat(this,
                        "topPadding", AndroidUtilities.dp2(BAR_HEIGHT + 2)));
                animatorSet.setDuration(200);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animatorSet != null && animatorSet.equals(animation)) {
                            animatorSet = null;
                        }
                    }
                });
                animatorSet.start();
            }
            visible = true;
            setVisibility(VISIBLE);
        }

        if (showRound) {
            playPauseDrawable.setPause(FeedRoundVideoView.isActivelyPlaying(), !create);
        } else {
            playPauseDrawable.setPause(!MediaController.getInstance().isMessagePaused(), !create);
        }

        if (lastMessageObject != displayMsg) {
            lastMessageObject = displayMsg;
            SpannableStringBuilder sb;

            if (showRound || displayMsg.isVoice() || displayMsg.isRoundVideo()) {
                isMusic = false;
                playbackSpeedButton.setAlpha(1.0f);
                playbackSpeedButton.setEnabled(true);
                playbackSpeedButton.setVisibility(VISIBLE);
                playbackSpeedButton.setTag(1);
                titleTextView.setPadding(0, 0, dp(44), 0);
                sb = new SpannableStringBuilder(String.format("%s %s",
                        displayMsg.getMusicAuthor(), displayMsg.getMusicTitle()));
                for (int i = 0; i < 2; i++) {
                    android.widget.TextView tv = i == 0
                            ? titleTextView.getTextView() : titleTextView.getNextTextView();
                    if (tv != null) tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                }
            } else {
                isMusic = true;
                if (displayMsg.getDuration() >= 10 * 60) {
                    playbackSpeedButton.setAlpha(1.0f);
                    playbackSpeedButton.setEnabled(true);
                    playbackSpeedButton.setVisibility(VISIBLE);
                    playbackSpeedButton.setTag(1);
                    titleTextView.setPadding(0, 0, dp(44), 0);
                } else {
                    playbackSpeedButton.setAlpha(0.0f);
                    playbackSpeedButton.setEnabled(false);
                    titleTextView.setPadding(0, 0, 0, 0);
                }
                sb = new SpannableStringBuilder(String.format("%s - %s",
                        displayMsg.getMusicAuthor(), displayMsg.getMusicTitle()));
                for (int i = 0; i < 2; i++) {
                    android.widget.TextView tv = i == 0
                            ? titleTextView.getTextView() : titleTextView.getNextTextView();
                    if (tv != null) tv.setEllipsize(TextUtils.TruncateAt.END);
                }
            }

            TypefaceSpan span = new TypefaceSpan(AndroidUtilities.bold(), 0,
                    getThemedColor(Theme.key_inappPlayerPerformer));
            sb.setSpan(span, 0, displayMsg.getMusicAuthor().length(),
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            titleTextView.setText(sb, visible && isMusic);

            updatePlaybackButton(false);
        }

        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);

        if (!visible) return;

        float progress = 0;
        boolean needsContinuousUpdate = false;

        if (isShowingRoundVideo) {
            progress = FeedRoundVideoView.getActiveProgress();
            needsContinuousUpdate = FeedRoundVideoView.isActivelyPlaying()
                    || FeedRoundVideoView.isActiveSeeking();
        } else {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null) {
                progress = playing.audioProgress;
            }
        }

        if (progress > 0) {
            float left = -dpf2(1);
            float right = getMeasuredWidth() + dpf2(1);
            float p = lerp(left, right, progress);
            float bottom = dp(BAR_HEIGHT);
            float top = bottom - dpf2(2);

            progressPaint.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
            canvas.drawRoundRect(left, top, p, bottom, dpf2(1), dpf2(1), progressPaint);
        }

        if (needsContinuousUpdate) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                dp(BAR_HEIGHT + 2), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingSpeedChanged);

        FeedRoundVideoView.setRoundVideoStateListener(roundVideoListener);
        checkPlayer(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingSpeedChanged);

        FeedRoundVideoView.setRoundVideoStateListener(null);

        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart
                || id == NotificationCenter.messagePlayingPlayStateChanged
                || id == NotificationCenter.messagePlayingDidReset) {
            checkPlayer(false);
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            invalidate();
        } else if (id == NotificationCenter.messagePlayingSpeedChanged) {
            updatePlaybackButton(true);
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}