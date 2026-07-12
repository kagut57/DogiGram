/*
 * DogiGram: lets the user choose which entries appear in the chat message long-press menu.
 */
package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.DogiConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import android.widget.FrameLayout;

public class MessageMenuSettingsActivity extends BaseFragment {

    // A single toggleable menu entry. One entry can control several option ids that mean the same
    // thing to the user (e.g. the two "Save to Gallery" variants).
    private static class Entry {
        final int labelRes;
        final int[] options;
        Entry(int labelRes, int... options) {
            this.labelRes = labelRes;
            this.options = options;
        }
    }

    // Curated set of entries the user can hide. The first block are the everyday options that show
    // for almost any message; the rest only appear for certain messages (Telegram decides that), so
    // toggling them off simply removes them when they would otherwise show.
    private static final Entry[] ENTRIES = new Entry[]{
            new Entry(R.string.Reply, ChatActivity.OPTION_REPLY),
            new Entry(R.string.Copy, ChatActivity.OPTION_COPY),
            new Entry(R.string.SaveToGallery, ChatActivity.OPTION_SAVE_TO_GALLERY, ChatActivity.OPTION_SAVE_TO_GALLERY2),
            new Entry(R.string.Forward, ChatActivity.OPTION_FORWARD),
            new Entry(R.string.DogiNoForwardTag, ChatActivity.OPTION_DOGI_NO_FORWARD_TAG),
            new Entry(R.string.PinMessage, ChatActivity.OPTION_PIN, ChatActivity.OPTION_UNPIN),
            new Entry(R.string.ReportChat, ChatActivity.OPTION_REPORT_CHAT),
            new Entry(R.string.Delete, ChatActivity.OPTION_DELETE),
            new Entry(R.string.DogiMessageDetails, ChatActivity.OPTION_DOGI_DETAILS),
            new Entry(R.string.Edit, ChatActivity.OPTION_EDIT),
            new Entry(R.string.TranslateMessage, ChatActivity.OPTION_TRANSLATE),
            new Entry(R.string.CopyLink, ChatActivity.OPTION_COPY_LINK),
            new Entry(R.string.SaveToDownloads, ChatActivity.OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC),
            new Entry(R.string.ShareFile, ChatActivity.OPTION_SHARE),
            new Entry(R.string.AddToFavorites, ChatActivity.OPTION_ADD_STICKER_TO_FAVORITES),
    };

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int rowCount;
    private int headerRow;
    private int entriesStartRow;
    private int entriesEndRow;
    private int infoRow;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        rowCount = 0;
        headerRow = rowCount++;
        entriesStartRow = rowCount;
        rowCount += ENTRIES.length;
        entriesEndRow = rowCount - 1;
        infoRow = rowCount++;
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Message Menu");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position >= entriesStartRow && position <= entriesEndRow) {
                Entry entry = ENTRIES[position - entriesStartRow];
                boolean enabled = !DogiConfig.isMessageMenuOptionEnabled(entry.options[0]);
                for (int option : entry.options) {
                    DogiConfig.setMessageMenuOptionEnabled(option, enabled);
                }
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            }
        });

        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position >= entriesStartRow && position <= entriesEndRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 1:
                default:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((TextCheckCell) view).setColors(Theme.key_windowBackgroundWhiteBlackText, Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText("Message Menu Options");
                    break;
                }
                case 1: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    Entry entry = ENTRIES[position - entriesStartRow];
                    boolean last = position != entriesEndRow;
                    checkCell.setTextAndCheck(LocaleController.getString(entry.labelRes), DogiConfig.isMessageMenuOptionEnabled(entry.options[0]), last);
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                    infoCell.setText("Turn off the entries you don't want in the message menu (the popup shown when you tap a message). Some entries — like Edit, Translate, Copy Link or Save to downloads — only appear for certain messages, so they show up only when Telegram makes them available.");
                    infoCell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return 0;
            } else if (position == infoRow) {
                return 2;
            }
            return 1;
        }
    }
}
