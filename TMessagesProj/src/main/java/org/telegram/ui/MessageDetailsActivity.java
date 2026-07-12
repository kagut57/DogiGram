/*
 * DogiGram: full-screen message details page (info rows + syntax-highlighted JSON).
 */
package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageDetailsActivity extends BaseFragment {

    private static final Pattern STRING_PATTERN = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern BOOL_PATTERN = Pattern.compile("(?<=: )(?:true|false|null)\\b");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<=: )(-?\\d+(?:\\.\\d+)?)\\b");

    private final int account;
    private final MessageObject message;
    private final boolean full;

    public MessageDetailsActivity(int account, MessageObject message) {
        this(account, message, false);
    }

    public MessageDetailsActivity(int account, MessageObject message, boolean full) {
        this.account = account;
        this.message = message;
        this.full = full;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(full ? R.string.DogiFullMessageDetails : R.string.DogiMessageDetails));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(16));
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (message != null && message.messageOwner != null) {
            buildContent(context, content);
        }

        fragmentView = scrollView;
        return fragmentView;
    }

    private void buildContent(Context context, LinearLayout content) {
        if (full) {
            buildJsonCard(context, content);
            return;
        }
        final MessagesController mc = MessagesController.getInstance(account);

        // --- sender header card: avatar + name + username/id ---
        long senderId = message.getSenderId();
        TLRPC.User senderUser = senderId > 0 ? mc.getUser(senderId) : null;
        TLRPC.Chat senderChat = senderId < 0 ? mc.getChat(-senderId) : null;
        if (senderUser != null || senderChat != null) {
            buildSenderCard(context, content, senderUser, senderChat, senderId);
        }

        // --- message info card ---
        LinearLayout info = newCard(context, content);

        addIconRow(info, R.drawable.msg_link, "ID", String.valueOf(message.getId()));

        if (!TextUtils.isEmpty(message.messageOwner.message)) {
            addIconRow(info, R.drawable.msg_message, LocaleController.getString(R.string.Message), message.messageOwner.message);
        }

        if (message.messageOwner.date != 0) {
            addIconRow(info, R.drawable.msg_calendar2, LocaleController.getString(R.string.DogiDetailDate),
                    LocaleController.formatDateTime(message.messageOwner.date, true));
        }
        if (message.messageOwner.edit_date != 0) {
            addIconRow(info, R.drawable.msg_edit, LocaleController.getString(R.string.DogiDetailEdited),
                    LocaleController.formatDateTime(message.messageOwner.edit_date, true));
        }

        addIconRow(info, R.drawable.msg_discussion, LocaleController.getString(R.string.DogiDetailChatId),
                formatApiId(mc, message.getDialogId()));

        if (message.isForwarded() && message.messageOwner.fwd_from != null) {
            String fwdName = message.messageOwner.fwd_from.from_name;
            if (TextUtils.isEmpty(fwdName)) {
                fwdName = resolveName(mc, DialogObject.getPeerDialogId(message.messageOwner.fwd_from.from_id));
            }
            if (!TextUtils.isEmpty(fwdName)) {
                addIconRow(info, R.drawable.msg_forward, LocaleController.getString(R.string.DogiDetailForwardedFrom), fwdName);
            }
        }

        if (message.messageOwner.views != 0) {
            addIconRow(info, R.drawable.msg_views, LocaleController.getString(R.string.DogiDetailViews),
                    LocaleController.formatNumber(message.messageOwner.views, ','));
        }
        if (message.messageOwner.forwards != 0) {
            addIconRow(info, R.drawable.msg_shareout, LocaleController.getString(R.string.DogiDetailForwards),
                    LocaleController.formatNumber(message.messageOwner.forwards, ','));
        }

        addIconRow(info, R.drawable.msg_media, LocaleController.getString(R.string.DogiDetailType), String.valueOf(message.type));
        stripLastDivider(info);

        // --- file card ---
        TLRPC.Document document = message.getDocument();
        if (document != null) {
            LinearLayout file = newCard(context, content);
            String fileName = FileLoader.getDocumentFileName(document);
            if (!TextUtils.isEmpty(fileName)) {
                addIconRow(file, R.drawable.msg_view_file, LocaleController.getString(R.string.DogiDetailFileName), fileName);
            }
            if (!TextUtils.isEmpty(document.mime_type)) {
                addIconRow(file, R.drawable.msg_sendfile, LocaleController.getString(R.string.DogiDetailMimeType), document.mime_type);
            }
            if (document.size != 0) {
                addIconRow(file, R.drawable.msg_filehq, LocaleController.getString(R.string.DogiDetailFileSize), AndroidUtilities.formatFileSize(document.size));
            }
            addIconRow(file, R.drawable.msg_satellite, LocaleController.getString(R.string.DogiDetailDcId), String.valueOf(document.dc_id));
            stripLastDivider(file);
        }

        // --- "Full details" button → opens the full JSON view ---
        LinearLayout buttonCard = newCard(context, content);
        TextView fullButton = new TextView(context);
        fullButton.setText(LocaleController.getString(R.string.DogiFullMessageDetails));
        fullButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        fullButton.setTypeface(AndroidUtilities.bold());
        fullButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        fullButton.setGravity(Gravity.CENTER);
        fullButton.setPadding(dp(16), dp(14), dp(16), dp(14));
        fullButton.setBackground(Theme.getSelectorDrawable(false));
        fullButton.setOnClickListener(v -> presentFragment(new MessageDetailsActivity(account, message, true)));
        buttonCard.addView(fullButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    // Avatar + name header for the sender; tapping copies the sender id.
    private void buildSenderCard(Context context, LinearLayout content, TLRPC.User user, TLRPC.Chat chat, long senderId) {
        final MessagesController mc = MessagesController.getInstance(account);
        LinearLayout card = newCard(context, content);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(Theme.getSelectorDrawable(false));

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(23));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (user != null) {
            avatarDrawable.setInfo(account, user);
            avatarView.setForUserOrChat(user, avatarDrawable);
        } else {
            avatarDrawable.setInfo(account, chat);
            avatarView.setForUserOrChat(chat, avatarDrawable);
        }
        row.addView(avatarView, LayoutHelper.createLinear(46, 46));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 14, 0, 0, 0));

        TextView nameView = new TextView(context);
        String name = user != null ? UserObject.getUserName(user) : chat.title;
        nameView.setText(name);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        StringBuilder sub = new StringBuilder();
        String username = user != null ? UserObject.getPublicUsername(user) : ChatObject.getPublicUsername(chat);
        if (!TextUtils.isEmpty(username)) {
            sub.append('@').append(username).append("  •  ");
        }
        sub.append(formatApiId(mc, senderId));
        if (user != null && user.bot) {
            sub.append("  •  ").append(LocaleController.getString(R.string.Bot));
        }
        TextView subView = new TextView(context);
        subView.setText(sub);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subView.setSingleLine(true);
        subView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        subView.setPadding(0, dp(2), 0, 0);
        texts.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final String copyText = formatApiId(mc, senderId);
        row.setOnClickListener(v -> copy(copyText));
        card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    // DogiGram: ids in bot-API form — channels and supergroups get the -100 prefix
    // (-2562129839 -> -1002562129839); users and basic groups stay as-is.
    private String formatApiId(MessagesController mc, long dialogId) {
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = mc.getChat(-dialogId);
            if (ChatObject.isChannel(chat)) {
                return String.valueOf(-1000000000000L - (-dialogId));
            }
        }
        return String.valueOf(dialogId);
    }

    private void buildJsonCard(Context context, LinearLayout content) {
        String json = FileLog.toJsonPretty(message.messageOwner);
        if (TextUtils.isEmpty(json)) {
            return;
        }
        json = cleanJson(json);
        LinearLayout jsonCard = newCard(context, content);

        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        TextView jsonView = new TextView(context);
        jsonView.setTypeface(Typeface.MONOSPACE);
        jsonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        jsonView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        jsonView.setPadding(dp(16), dp(12), dp(16), dp(12));
        jsonView.setText(highlightJson(json));
        final String jsonCopy = json;
        jsonView.setOnClickListener(v -> copy(jsonCopy));
        hsv.addView(jsonView, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        jsonCard.addView(hsv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    // DogiGram: make the raw TLRPC dump read more like a high-level object: drop the "TLRPC$"/"TL_"
    // prefixes from the "_" type names and capitalise them (e.g. "TLRPC$TL_message" -> "Message").
    private String cleanJson(String json) {
        try {
            Matcher m = Pattern.compile("\"_\": \"([^\"]+)\"").matcher(json);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String type = m.group(1);
                type = type.replace("TLRPC$", "");
                if (type.startsWith("TL_")) {
                    type = type.substring(3);
                }
                if (!type.isEmpty()) {
                    type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement("\"_\": \"" + type + "\""));
            }
            m.appendTail(sb);
            return sb.toString().replace("TLRPC$", "");
        } catch (Exception e) {
            return json.replace("TLRPC$", "");
        }
    }

    private LinearLayout newCard(Context context, LinearLayout parent) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        lp.leftMargin = dp(12);
        lp.rightMargin = dp(12);
        lp.topMargin = dp(8);
        parent.addView(card, lp);
        return card;
    }

    // Profile-style info row: leading icon, value on top, small grey label under it. Tap to copy.
    private void addIconRow(LinearLayout card, int iconRes, String label, CharSequence value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setBackground(Theme.getSelectorDrawable(false));

        ImageView iconView = new ImageView(getContext());
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        row.addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.TOP, 0, 3, 0, 0));

        LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.TOP, 16, 0, 0, 0));

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        texts.addView(valueView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        labelView.setPadding(0, dp(2), 0, 0);
        texts.addView(labelView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final String copyText = value.toString();
        row.setOnClickListener(v -> copy(copyText));

        card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        View d = new View(getContext());
        d.setBackgroundColor(Theme.getColor(Theme.key_divider));
        d.setTag("divider");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 1);
        lp.leftMargin = dp(56);
        card.addView(d, lp);
    }

    // Remove the divider trailing the card's last row so the rounded corner stays clean.
    private void stripLastDivider(LinearLayout card) {
        int count = card.getChildCount();
        if (count > 0 && "divider".equals(card.getChildAt(count - 1).getTag())) {
            card.removeViewAt(count - 1);
        }
    }

    private void copy(String text) {
        AndroidUtilities.addToClipboard(text);
        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
    }

    private String resolveName(MessagesController mc, long peerId) {
        if (peerId > 0) {
            TLRPC.User user = mc.getUser(peerId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (peerId < 0) {
            TLRPC.Chat chat = mc.getChat(-peerId);
            if (chat != null) {
                return chat.title;
            }
        }
        return null;
    }

    // DogiGram: lightweight JSON syntax highlighting with our own colour palette.
    private CharSequence highlightJson(String json) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(json);
        final int keyColor = 0xFF2D9CDB;   // blue
        final int strColor = 0xFF27AE60;   // green
        final int numColor = 0xFF8E5BD9;   // purple
        final int boolColor = 0xFFE0533D;  // red/orange

        Matcher m = STRING_PATTERN.matcher(json);
        while (m.find()) {
            int s = m.start(), e = m.end();
            int i = e;
            while (i < json.length() && json.charAt(i) == ' ') {
                i++;
            }
            boolean isKey = i < json.length() && json.charAt(i) == ':';
            ssb.setSpan(new ForegroundColorSpan(isKey ? keyColor : strColor), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        Matcher mb = BOOL_PATTERN.matcher(json);
        while (mb.find()) {
            ssb.setSpan(new ForegroundColorSpan(boolColor), mb.start(), mb.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        Matcher mn = NUMBER_PATTERN.matcher(json);
        while (mn.find()) {
            ssb.setSpan(new ForegroundColorSpan(numColor), mn.start(1), mn.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }
}
