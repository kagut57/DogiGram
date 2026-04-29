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
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class FeedInlineButtonsView extends LinearLayout {

    public interface OnButtonClickListener {
        void onInlineButtonClick(FeedController.FeedItem item, TLRPC.KeyboardButton button);
    }

    private final Theme.ResourcesProvider resourceProvider;
    private FeedController.FeedItem currentItem;
    private OnButtonClickListener listener;

    public FeedInlineButtonsView(Context context, Theme.ResourcesProvider resourceProvider) {
        super(context);
        this.resourceProvider = resourceProvider;
        setOrientation(VERTICAL);
    }

    public void setOnButtonClickListener(OnButtonClickListener l) { listener = l; }

    @SuppressLint("SetTextI18n")
    public void setData(TLRPC.Message raw, FeedController.FeedItem item) {
        removeAllViews();
        currentItem = item;

        if (!(raw.reply_markup instanceof TLRPC.TL_replyInlineMarkup)) {
            setVisibility(GONE);
            return;
        }

        TLRPC.TL_replyInlineMarkup markup = (TLRPC.TL_replyInlineMarkup) raw.reply_markup;
        if (markup.rows == null || markup.rows.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourceProvider);
        int bg = (accent & 0x00FFFFFF) | 0x1A000000;
        int pressed = (accent & 0x00FFFFFF) | 0x33000000;

        for (int r = 0; r < markup.rows.size(); r++) {
            TLRPC.TL_keyboardButtonRow row = markup.rows.get(r);
            if (row.buttons == null || row.buttons.isEmpty()) continue;

            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setOrientation(HORIZONTAL);

            for (int i = 0; i < row.buttons.size(); i++) {
                TLRPC.KeyboardButton button = row.buttons.get(i);

                TextView btn = new TextView(getContext());
                btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                btn.setTypeface(AndroidUtilities.bold());
                btn.setTextColor(accent);
                btn.setGravity(Gravity.CENTER);
                btn.setPadding(dp(12), dp(8), dp(12), dp(8));
                btn.setMaxLines(1);
                btn.setEllipsize(TextUtils.TruncateAt.END);

                String label = button.text;
                if (button instanceof TLRPC.TL_keyboardButtonUrl
                        || button instanceof TLRPC.TL_keyboardButtonUrlAuth
                        || button instanceof TLRPC.TL_keyboardButtonWebView) {
                    btn.setText(label + " ↗");
                } else if (button instanceof TLRPC.TL_keyboardButtonCopy) {
                    btn.setText("📋 " + label);
                } else {
                    btn.setText(label);
                }

                btn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                        dp(6), bg, pressed));
                btn.setOnClickListener(v -> handleClick(button));

                LayoutParams lp = new LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f);
                if (i > 0) lp.leftMargin = dp(4);
                rowLayout.addView(btn, lp);
            }

            LayoutParams rowLp = new LayoutParams(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            if (r > 0) rowLp.topMargin = dp(4);
            addView(rowLayout, rowLp);
        }

        setVisibility(VISIBLE);
    }

    public void clear() {
        removeAllViews();
        currentItem = null;
        setVisibility(GONE);
    }

    private void handleClick(TLRPC.KeyboardButton button) {
        if (button instanceof TLRPC.TL_keyboardButtonUrl) {
            String url = button.url;
            if (url != null) Browser.openUrl(getContext(), url);
            return;
        }
        if (button instanceof TLRPC.TL_keyboardButtonUrlAuth) {
            String url = button.url;
            if (url != null) Browser.openUrl(getContext(), url);
            return;
        }
        if (button instanceof TLRPC.TL_keyboardButtonWebView) {
            String url = button.url;
            if (url != null) Browser.openUrl(getContext(), url);
            return;
        }
        if (button instanceof TLRPC.TL_keyboardButtonCopy) {
            try {
                String text = ((TLRPC.TL_keyboardButtonCopy) button).copy_text;
                if (text != null) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(
                            android.content.ClipData.newPlainText("", text));
                }
            } catch (Exception ignored) {}
            return;
        }
        if (listener != null && currentItem != null)
            listener.onInlineButtonClick(currentItem, button);
    }
}