/*
 * DogiGram custom settings screen (Night Mode, Show Phone Number, Screenshot Mode).
 */
package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DogiConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class DogiGramSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final String PREFS = "dogigramconfig";

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int rowCount;
    private int headerRow;
    private int drawerUiRow;
    private int showPhoneRow;
    private int screenshotRow;
    private int sectionRow;
    private int featuresHeaderRow;
    private int boostRow;
    private int showIdRow;
    private int showDcIdRow;
    private int doubleTapRow;
    private int serverStatusRow;
    private int featuresSectionRow;
    private int extraHeaderRow;
    private int disableRoundingRow;
    private int hideKeyboardRow;
    private int autoPauseVideoRow;
    private int maxRecentStickersRow;
    private int extraSectionRow;
    private int aboutHeaderRow;
    private int versionRow;
    private int groupRow;
    private int channelRow;
    private int aboutSectionRow;

    private static final String GROUP_URL = "https://t.me/dogigram_support";
    private static final String CHANNEL_URL = "https://t.me/dogigram_app";

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // DogiGram: true = old hamburger drawer UI, false = new bottom-tab UI. Applied on app restart.
    public static boolean isDrawerUi() {
        return prefs().getBoolean("drawer_ui", true);
    }

    // DogiGram: build the main screen (post-login or per-account) honouring the UI-style toggle.
    public static BaseFragment createMainFragment(Bundle args) {
        if (isDrawerUi()) {
            return new DialogsActivity(args);
        }
        MainTabsActivity mainTabsActivity = new MainTabsActivity();
        mainTabsActivity.prepareDialogsActivity(args);
        return mainTabsActivity;
    }

    public static boolean isShowPhoneNumber() {
        return prefs().getBoolean("show_phone", true);
    }

    public static boolean isScreenshotMode() {
        return prefs().getBoolean("screenshot_mode", false);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        rowCount = 0;
        headerRow = rowCount++;
        drawerUiRow = rowCount++;
        showPhoneRow = rowCount++;
        screenshotRow = rowCount++;
        sectionRow = rowCount++;
        featuresHeaderRow = rowCount++;
        boostRow = rowCount++;
        showIdRow = rowCount++;
        showDcIdRow = rowCount++;
        doubleTapRow = rowCount++;
        serverStatusRow = rowCount++;
        featuresSectionRow = rowCount++;
        extraHeaderRow = rowCount++;
        disableRoundingRow = rowCount++;
        hideKeyboardRow = rowCount++;
        autoPauseVideoRow = rowCount++;
        maxRecentStickersRow = rowCount++;
        extraSectionRow = rowCount++;
        aboutHeaderRow = rowCount++;
        versionRow = rowCount++;
        groupRow = rowCount++;
        channelRow = rowCount++;
        aboutSectionRow = rowCount++;
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("DogiGram Settings");
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
            SharedPreferences preferences = prefs();
            if (position == drawerUiRow) {
                boolean enabled = !preferences.getBoolean("drawer_ui", true);
                preferences.edit().putBoolean("drawer_ui", enabled).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), "Restart DogiGram to apply the interface change", Toast.LENGTH_LONG).show();
                }
            } else if (position == showPhoneRow) {
                boolean enabled = !preferences.getBoolean("show_phone", true);
                preferences.edit().putBoolean("show_phone", enabled).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == screenshotRow) {
                boolean enabled = !preferences.getBoolean("screenshot_mode", false);
                preferences.edit().putBoolean("screenshot_mode", enabled).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
                applyScreenshotMode(enabled);
            } else if (position == boostRow) {
                DogiConfig.toggleUploadDownloadBoost();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(DogiConfig.isUploadDownloadBoost());
                }
            } else if (position == showIdRow) {
                DogiConfig.toggleShowId();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(DogiConfig.isShowId());
                }
            } else if (position == showDcIdRow) {
                DogiConfig.toggleShowDcId();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(DogiConfig.isShowDcId());
                }
            } else if (position == doubleTapRow) {
                showDoubleTapDialog();
            } else if (position == serverStatusRow) {
                presentFragment(new DatacenterStatusActivity());
            } else if (position == disableRoundingRow) {
                DogiConfig.toggleDisableNumberRounding();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(DogiConfig.isDisableNumberRounding());
                }
            } else if (position == hideKeyboardRow) {
                DogiConfig.toggleHideKeyboardOnScroll();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(DogiConfig.isHideKeyboardOnScroll());
                }
            } else if (position == autoPauseVideoRow) {
                DogiConfig.toggleAutoPauseVideo();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(DogiConfig.isAutoPauseVideo());
                }
            } else if (position == maxRecentStickersRow) {
                showMaxRecentStickersDialog();
            } else if (position == versionRow) {
                AndroidUtilities.addToClipboard(BuildVars.BUILD_VERSION_STRING);
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), LocaleController.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
                }
            } else if (position == groupRow) {
                Browser.openUrl(getParentActivity(), GROUP_URL);
            } else if (position == channelRow) {
                Browser.openUrl(getParentActivity(), CHANNEL_URL);
            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            if (listView != null) {
                listView.invalidateViews();
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void applyScreenshotMode(boolean enabled) {
        if (getParentActivity() != null) {
            if (enabled) {
                getParentActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private static String doubleTapLabel(int action) {
        switch (action) {
            case DogiConfig.DOUBLE_TAP_NONE:
                return LocaleController.getString(R.string.DogiDoubleTapNone);
            case DogiConfig.DOUBLE_TAP_REPLY:
                return LocaleController.getString(R.string.DogiDoubleTapReply);
            case DogiConfig.DOUBLE_TAP_SAVE:
                return LocaleController.getString(R.string.DogiDoubleTapSave);
            case DogiConfig.DOUBLE_TAP_EDIT:
                return LocaleController.getString(R.string.DogiDoubleTapEdit);
            case DogiConfig.DOUBLE_TAP_TRANSLATE:
                return LocaleController.getString(R.string.DogiDoubleTapTranslate);
            case DogiConfig.DOUBLE_TAP_REACTION:
            default:
                return LocaleController.getString(R.string.DogiDoubleTapReaction);
        }
    }

    private void showMaxRecentStickersDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final EditText editText = new EditText(getParentActivity());
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint("0 = default, max " + DogiConfig.MAX_RECENT_STICKERS_LIMIT);
        int current = DogiConfig.getMaxRecentStickers();
        if (current > 0) {
            editText.setText(String.valueOf(current));
            editText.setSelection(editText.getText().length());
        }
        FrameLayout container = new FrameLayout(getParentActivity());
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 6, 24, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Max recent stickers (max " + DogiConfig.MAX_RECENT_STICKERS_LIMIT + ")");
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            int value = 0;
            try {
                value = Integer.parseInt(editText.getText().toString().trim());
            } catch (Exception ignore) {
            }
            // DogiConfig clamps to [0, 200]; show a note if the entry was above the cap.
            boolean clamped = value > DogiConfig.MAX_RECENT_STICKERS_LIMIT;
            DogiConfig.setMaxRecentStickers(value);
            if (clamped && getParentActivity() != null) {
                Toast.makeText(getParentActivity(), "Limited to " + DogiConfig.MAX_RECENT_STICKERS_LIMIT, Toast.LENGTH_SHORT).show();
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showDoubleTapDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final int[] actions = new int[]{
                DogiConfig.DOUBLE_TAP_NONE,
                DogiConfig.DOUBLE_TAP_REACTION,
                DogiConfig.DOUBLE_TAP_REPLY,
                DogiConfig.DOUBLE_TAP_SAVE,
                DogiConfig.DOUBLE_TAP_EDIT,
                DogiConfig.DOUBLE_TAP_TRANSLATE
        };
        final int current = DogiConfig.getDoubleTapAction();
        final CharSequence[] labels = new CharSequence[actions.length];
        for (int i = 0; i < actions.length; i++) {
            String label = doubleTapLabel(actions[i]);
            labels[i] = actions[i] == current ? "✓ " + label : label;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.DogiDoubleTapAction));
        builder.setItems(labels, (dialog, which) -> {
            DogiConfig.setDoubleTapAction(actions[which]);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == drawerUiRow || position == showPhoneRow || position == screenshotRow
                    || position == boostRow || position == showIdRow || position == showDcIdRow
                    || position == doubleTapRow || position == serverStatusRow
                    || position == disableRoundingRow || position == hideKeyboardRow
                    || position == autoPauseVideoRow || position == maxRecentStickersRow
                    || position == versionRow || position == groupRow
                    || position == channelRow;
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
                    view = new ShadowSectionCell(mContext);
                    break;
                case 3:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
            SharedPreferences preferences = prefs();
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText("DogiGram");
                    } else if (position == featuresHeaderRow) {
                        headerCell.setText("Features");
                    } else if (position == extraHeaderRow) {
                        headerCell.setText("Extra");
                    } else if (position == aboutHeaderRow) {
                        headerCell.setText(LocaleController.getString(R.string.DogiAbout));
                    }
                    break;
                }
                case 1: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == drawerUiRow) {
                        checkCell.setTextAndCheck("Old drawer UI", preferences.getBoolean("drawer_ui", true), true);
                    } else if (position == showPhoneRow) {
                        checkCell.setTextAndCheck("Show Phone Number", preferences.getBoolean("show_phone", true), true);
                    } else if (position == screenshotRow) {
                        checkCell.setTextAndCheck("Screenshot Mode", preferences.getBoolean("screenshot_mode", false), false);
                    } else if (position == boostRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.DogiUploadDownloadBoost), DogiConfig.isUploadDownloadBoost(), true);
                    } else if (position == showIdRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.DogiShowId), DogiConfig.isShowId(), true);
                    } else if (position == showDcIdRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.DogiShowDcId), DogiConfig.isShowDcId(), true);
                    } else if (position == disableRoundingRow) {
                        checkCell.setTextAndValueAndCheck("Disable number rounding", "4.8K -> 4777", DogiConfig.isDisableNumberRounding(), true, true);
                    } else if (position == hideKeyboardRow) {
                        checkCell.setTextAndCheck("Hide Keyboard on Scroll", DogiConfig.isHideKeyboardOnScroll(), true);
                    } else if (position == autoPauseVideoRow) {
                        checkCell.setTextAndValueAndCheck("Auto pause video", "When lock screen/switch to background.", DogiConfig.isAutoPauseVideo(), true, true);
                    }
                    break;
                }
                case 3: {
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    if (position == doubleTapRow) {
                        settingsCell.setTextAndValue(LocaleController.getString(R.string.DogiDoubleTapAction), doubleTapLabel(DogiConfig.getDoubleTapAction()), true);
                    } else if (position == serverStatusRow) {
                        settingsCell.setText(LocaleController.getString(R.string.DogiServerStatus), false);
                    } else if (position == maxRecentStickersRow) {
                        int value = DogiConfig.getMaxRecentStickers();
                        settingsCell.setTextAndValue("Max recent stickers", value > 0 ? String.valueOf(value) : "Default", false);
                    } else if (position == versionRow) {
                        settingsCell.setTextAndValue(LocaleController.getString(R.string.DogiVersion), BuildVars.BUILD_VERSION_STRING, true);
                    } else if (position == groupRow) {
                        settingsCell.setTextAndValue(LocaleController.getString(R.string.DogiDiscussionGroup), "@dogigram_support", true);
                    } else if (position == channelRow) {
                        settingsCell.setTextAndValue(LocaleController.getString(R.string.DogiChannel), "@dogigram_app", false);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow || position == featuresHeaderRow || position == extraHeaderRow || position == aboutHeaderRow) {
                return 0;
            } else if (position == sectionRow || position == featuresSectionRow || position == extraSectionRow || position == aboutSectionRow) {
                return 2;
            } else if (position == doubleTapRow || position == serverStatusRow || position == maxRecentStickersRow
                    || position == versionRow || position == groupRow || position == channelRow) {
                return 3;
            }
            return 1;
        }
    }
}
