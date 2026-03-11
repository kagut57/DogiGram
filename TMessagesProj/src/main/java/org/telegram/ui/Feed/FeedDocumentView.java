package org.telegram.ui.Feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.List;

@SuppressLint("ViewConstructor")
public class FeedDocumentView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final Theme.ResourcesProvider resourceProvider;

    private final ImageView iconView;
    private final TextView nameView;
    private final TextView sizeView;
    private final ProgressBar progressBar;

    private TLRPC.Document currentDocument;
    private MessageObject currentDocumentMessage;
    private String currentFileKey;
    private FeedController.FeedItem currentItem;

    public FeedDocumentView(Context context, int account, Theme.ResourcesProvider resourceProvider) {
        super(context);
        this.currentAccount = account;
        this.resourceProvider = resourceProvider;

        int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourceProvider);
        int grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourceProvider);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(0, dp(8), 0, dp(4));
        setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector, resourceProvider), 2));
        setOnClickListener(v -> onDocumentClick());

        iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.msg_round_file_s);
        iconView.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
        addView(iconView, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(VERTICAL);
        textCol.setPadding(dp(10), 0, 0, 0);

        nameView = new TextView(context);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(accentColor);
        nameView.setMaxLines(1);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(nameView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sizeView = new TextView(context);
        sizeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sizeView.setTextColor(grayColor);
        textCol.addView(sizeView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(GONE);
        progressBar.setScaleY(0.5f);
        textCol.addView(progressBar, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 6, 0, 2, 0, 0));

        addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
    }

    @SuppressLint("SetTextI18n")
    public void setData(FeedController.FeedItem item) {
        currentItem = item;
        currentDocument = null;
        currentDocumentMessage = null;
        currentFileKey = null;
        progressBar.setProgress(0);
        progressBar.setVisibility(GONE);

        if (item == null) {
            setVisibility(GONE);
            return;
        }

        List<TLRPC.Document> docs = FeedUtils.getDocuments(item);
        if (docs.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        TLRPC.Document doc = docs.get(0);
        currentDocument = doc;
        currentFileKey = FileLoader.getAttachFileName(doc);

        for (MessageObject msg : item.messages) {
            TLRPC.MessageMedia media = msg.messageOwner.media;
            if (media instanceof TLRPC.TL_messageMediaDocument
                    && media.document != null
                    && media.document.id == doc.id) {
                currentDocumentMessage = msg;
                break;
            }
        }

        String fileName = null;
        for (TLRPC.DocumentAttribute attr : doc.attributes) {
            if (attr instanceof TLRPC.TL_documentAttributeFilename) {
                fileName = attr.file_name;
                break;
            }
        }
        if (fileName == null || fileName.isEmpty()) fileName = "Document";

        nameView.setText(fileName);
        sizeView.setText(FeedUtils.formatFileSize(doc.size));

        if (docs.size() > 1) {
            sizeView.setText(FeedUtils.formatFileSize(doc.size)
                    + " · +" + (docs.size() - 1) + " more");
        }

        updateState();
        setVisibility(VISIBLE);
    }

    public void clear() {
        currentDocument = null;
        currentDocumentMessage = null;
        currentFileKey = null;
        currentItem = null;
        progressBar.setProgress(0);
        progressBar.setVisibility(GONE);
        setVisibility(GONE);
    }

    private void onDocumentClick() {
        if (currentDocument == null) return;

        FileLoader fl = FileLoader.getInstance(currentAccount);
        boolean isLoading = fl.isLoadingFile(currentFileKey);
        File path = getDocumentPath(fl);

        if (path.exists() && !isLoading) {
            openDocument(path);
        } else if (isLoading) {
            fl.cancelLoadFile(currentDocument);
            updateState();
        } else {
            fl.loadFile(currentDocument, currentDocumentMessage,
                    FileLoader.PRIORITY_HIGH, 0);
            updateState();
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateState() {
        if (currentDocument == null) return;

        FileLoader fl = FileLoader.getInstance(currentAccount);
        boolean isLoading = fl.isLoadingFile(currentFileKey);
        File path = getDocumentPath(fl);
        boolean exists = path.exists() && !isLoading;

        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourceProvider);

        if (exists) {
            iconView.setImageResource(R.drawable.msg_round_file_s);
            iconView.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            progressBar.setVisibility(GONE);
            String sizeText = FeedUtils.formatFileSize(currentDocument.size);
            if (currentItem != null) {
                List<TLRPC.Document> docs = FeedUtils.getDocuments(currentItem);
                if (docs.size() > 1) sizeText += " · +" + (docs.size() - 1) + " more";
            }
            sizeView.setText(sizeText);
        } else if (isLoading) {
            iconView.setImageResource(R.drawable.msg_round_cancel_m);
            iconView.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            progressBar.setVisibility(VISIBLE);
            sizeView.setText(LocaleController.getString("Loading", R.string.Loading)
                    + "… · " + FeedUtils.formatFileSize(currentDocument.size));
        } else {
            iconView.setImageResource(R.drawable.msg_round_load_m);
            iconView.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            progressBar.setVisibility(GONE);
            progressBar.setProgress(0);
        }
    }

    private File getDocumentPath(FileLoader fl) {
        File path = fl.getPathToAttach(currentDocument, false);
        if (!path.exists()) path = fl.getPathToAttach(currentDocument, true);
        return path;
    }

    private void openDocument(File file) {
        if (file == null || !file.exists() || currentDocument == null) return;

        String docName = FileLoader.getDocumentFileName(currentDocument);
        if (TextUtils.isEmpty(docName)) docName = file.getName();
        String mime = currentDocument.mime_type;
        if (TextUtils.isEmpty(mime)) mime = "application/octet-stream";

        Activity activity = FeedUtils.getActivity(getContext());
        if (activity == null) return;

        try {
            AndroidUtilities.openForView(file, docName, mime, activity, resourceProvider, false);
        } catch (Exception e) {
            FeedUtils.openFileFallback(getContext(), file, mime);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.addObserver(this, NotificationCenter.fileLoaded);
        nc.addObserver(this, NotificationCenter.fileLoadFailed);
        nc.addObserver(this, NotificationCenter.fileLoadProgressChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.removeObserver(this, NotificationCenter.fileLoaded);
        nc.removeObserver(this, NotificationCenter.fileLoadFailed);
        nc.removeObserver(this, NotificationCenter.fileLoadProgressChanged);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount || currentDocument == null || currentFileKey == null) return;

        if (id == NotificationCenter.fileLoaded) {
            if (currentFileKey.equals(args[0])) updateState();
        } else if (id == NotificationCenter.fileLoadFailed) {
            if (currentFileKey.equals(args[0])) updateState();
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            if (currentFileKey.equals(args[0])) {
                long loaded = (Long) args[1];
                long total = (Long) args[2];
                if (total > 0) {
                    int pct = (int) (loaded * 100 / total);
                    progressBar.setProgress(pct);
                    sizeView.setText(pct + "% · " + FeedUtils.formatFileSize(currentDocument.size));
                }
            }
        }
    }
}