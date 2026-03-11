package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class FeedVoiceView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final FeedPlayPauseButton playButton;
    private final FeedWaveformView waveformView;
    private final TextView labelView;
    private final TextView durationView;

    private MessageObject currentMessage;
    private int totalDuration;

    public FeedVoiceView(Context context, int account, Theme.ResourcesProvider resourceProvider) {
        super(context);
        this.currentAccount = account;

        int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourceProvider);
        int grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourceProvider);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(0, dp(8), 0, dp(4));

        playButton = new FeedPlayPauseButton(context, accentColor);
        playButton.setOnClickListener(v -> togglePlayback());
        addView(playButton, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL));

        LinearLayout middle = new LinearLayout(context);
        middle.setOrientation(VERTICAL);
        middle.setPadding(dp(10), 0, 0, 0);

        labelView = new TextView(context);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        labelView.setTypeface(AndroidUtilities.bold());
        labelView.setMaxLines(1);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        middle.addView(labelView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        waveformView = new FeedWaveformView(context, accentColor);
        middle.addView(waveformView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 24, 0, 2, 0, 0));

        addView(middle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        durationView = new TextView(context);
        durationView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        durationView.setTextColor(grayColor);
        durationView.setPadding(dp(8), 0, 0, 0);
        addView(durationView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        waveformView.setSeekListener(new FeedWaveformView.SeekListener() {
            @Override public void onSeekStart() {}

            @Override
            public void onSeek(float progress) {
                updateDurationText(progress);
            }

            @Override
            public void onSeekEnd(float progress) {
                if (currentMessage == null) return;
                MediaController mc = MediaController.getInstance();
                if (mc.isPlayingMessage(currentMessage)) {
                    mc.seekToProgress(currentMessage, progress);
                } else {
                    mc.playMessage(currentMessage);
                    AndroidUtilities.runOnUIThread(
                            () -> mc.seekToProgress(currentMessage, progress), 300);
                }
                updatePlayButton();
            }
        });
    }

    @SuppressLint("SetTextI18n")
    public void setData(FeedController.FeedItem item) {
        currentMessage = null;
        totalDuration = 0;

        if (item == null) {
            setVisibility(GONE);
            return;
        }

        MessageObject voiceMsg = null;
        boolean isVoice = false;
        String title = null;
        String performer = null;
        int duration = 0;
        byte[] waveform = null;

        for (MessageObject msg : item.messages) {
            TLRPC.MessageMedia media = msg.messageOwner.media;
            if (!(media instanceof TLRPC.TL_messageMediaDocument) || media.document == null)
                continue;
            for (TLRPC.DocumentAttribute attr : media.document.attributes) {
                if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                    TLRPC.TL_documentAttributeAudio audio =
                            (TLRPC.TL_documentAttributeAudio) attr;
                    voiceMsg = msg;
                    isVoice = audio.voice;
                    duration = (int) audio.duration;
                    title = audio.title;
                    performer = audio.performer;
                    waveform = audio.waveform;
                    break;
                }
            }
            if (voiceMsg != null) break;
        }

        if (voiceMsg == null) {
            setVisibility(GONE);
            return;
        }

        currentMessage = voiceMsg;
        totalDuration = duration;

        if (isVoice) {
            labelView.setText("Voice message");
            waveformView.setWaveform(waveform);
        } else {
            String label = (title != null && !title.isEmpty()) ? title : "Audio";
            if (performer != null && !performer.isEmpty()) label += " — " + performer;
            labelView.setText("🎵 " + label);
            waveformView.setWaveform(null);
        }
        waveformView.setVisibility(VISIBLE);

        durationView.setText(FeedUtils.formatVoiceDuration(duration));

        MediaController mc = MediaController.getInstance();
        if (mc.isPlayingMessage(currentMessage)) {
            MessageObject playing = mc.getPlayingMessageObject();
            if (playing != null) {
                waveformView.setProgress(playing.audioProgress);
                updateDurationText(playing.audioProgress);
            }
        }

        updatePlayButton();
        setVisibility(VISIBLE);
    }

    public void clear() {
        currentMessage = null;
        totalDuration = 0;
        setVisibility(GONE);
    }

    public MessageObject getCurrentMessage() {
        return currentMessage;
    }

    private void togglePlayback() {
        if (currentMessage == null) return;
        MediaController mc = MediaController.getInstance();
        if (mc.isPlayingMessage(currentMessage)) {
            if (mc.isMessagePaused()) mc.playMessage(currentMessage);
            else mc.pauseMessage(currentMessage);
        } else {
            mc.playMessage(currentMessage);
        }
        updatePlayButton();
    }

    private void updatePlayButton() {
        if (currentMessage == null) return;
        MediaController mc = MediaController.getInstance();
        boolean playing = mc.isPlayingMessage(currentMessage) && !mc.isMessagePaused();
        playButton.setPlaying(playing);
    }

    @SuppressLint("SetTextI18n")
    private void updateDurationText(float progress) {
        if (totalDuration <= 0) return;
        int current = (int) (progress * totalDuration);
        durationView.setText(FeedUtils.formatVoiceDuration(current)
                + " / " + FeedUtils.formatVoiceDuration(totalDuration));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.addObserver(this, NotificationCenter.messagePlayingDidReset);
        nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.removeObserver(this, NotificationCenter.messagePlayingDidReset);
        nc.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        nc.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount || currentMessage == null) return;

        if (id == NotificationCenter.messagePlayingDidReset) {
            updatePlayButton();
            waveformView.setProgress(0);
            updateDurationText(0);
        } else if (id == NotificationCenter.messagePlayingPlayStateChanged) {
            updatePlayButton();
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            MediaController mc = MediaController.getInstance();
            if (mc.isPlayingMessage(currentMessage)) {
                MessageObject playing = mc.getPlayingMessageObject();
                if (playing != null) {
                    waveformView.setProgress(playing.audioProgress);
                    updateDurationText(playing.audioProgress);
                }
            }
        }
    }
}