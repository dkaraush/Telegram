package org.telegram.ui;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.InviteLinkBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkActionView;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class ChatEditReactionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private long chatId;
    private boolean isChannel;

    private boolean set = false;
    private boolean reactionsEnabled = false;
    private ArrayList<String> selectedReactions = new ArrayList<>();

    public ChatEditReactionsActivity(long id, TLRPC.ChatFull chatFull) {
        chatId = id;
        info = chatFull;
        if (info != null && !set) {
            set = true;
            reactionsEnabled = info.available_reactions != null && info.available_reactions.size() > 0;
            if (info.available_reactions != null) {
                selectedReactions = (ArrayList) info.available_reactions.clone();
                changed = false;
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(chatId);
        if (currentChat == null) {
            currentChat = getMessagesStorage().getChatSync(chatId);
            if (currentChat != null) {
                getMessagesController().putChat(currentChat, true);
            } else {
                return false;
            }
            if (info == null) {
                info = getMessagesStorage().loadChatInfo(chatId, ChatObject.isChannel(currentChat), new CountDownLatch(1), false, false);
                if (info == null) {
                    return false;
                }
            }
        }
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        if (info == null) {
            getMessagesController().loadFullChat(chatId, classGuid, true);
        }
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.availableReactionsUpdate);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.availableReactionsUpdate);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    class ReactionCheckCell extends FrameLayout {
        private BackupImageView imageView;
        private TextView textView;
        private Switch checkBox;

        public ReactionCheckCell(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            addView(imageView, LayoutHelper.createFrame(28, 28, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 64 : 21, 0, LocaleController.isRTL ? 21 : 64, 0));

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 21 : 64, 0, LocaleController.isRTL ? 64 : 21, 0));

            checkBox = new Switch(context);
            checkBox.setDrawIconType(2);
            addView(checkBox, LayoutHelper.createFrame(37, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));
        }

        private boolean needDivider = false;
        public void setDivider(boolean needDivider) {
            this.needDivider = needDivider;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(
                        LocaleController.isRTL ? 0 : AndroidUtilities.dp(63),
                        getMeasuredHeight() - 1,
                        getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(63) : 0),
                        getMeasuredHeight() - 1,
                        Theme.dividerPaint
                );
            }
        }

        public void setReaction(TLRPC.TL_availableReaction reaction) {
            imageView.setImage(ImageLocation.getForDocument(reaction.static_icon), null, "webp", null, this);
            textView.setText(reaction.title);
        }

        public void setChecked(boolean checked) {
            setChecked(checked, true);
        }
        public void setChecked(boolean checked, boolean animated) {
            checkBox.setChecked(checked, animated);
        }
    }

    private void updateAvailableReactions() {
        updateAvailableReactions(getMessagesController().getAvailableReactions());
    }
    private ArrayList<TLRPC.TL_availableReaction> availableReactions;
    private void updateAvailableReactions(ArrayList<TLRPC.TL_availableReaction> reactions) {
        availableReactions = reactions;
        if (this.reactionsList == null)
            return;

        for (ReactionCheckCell cell : this.reactionCells)
            reactionsList.removeView(cell);

        this.reactionCells.clear();
        // TODO(dkaraush): use list adapter
        for (int i = 0; i < reactions.size(); ++i) {
            TLRPC.TL_availableReaction reaction = reactions.get(i);

            ReactionCheckCell reactionCell = new ReactionCheckCell(fragmentView.getContext());
            reactionCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            reactionCell.setReaction(reaction);
            reactionCell.setChecked(selectedReactions.contains(reaction.reaction), false);
            reactionCell.setDivider(i < reactions.size() - 1);
            reactionCell.setOnClickListener(e -> {
                if (selectedReactions.contains(reaction.reaction)) {
                    selectedReactions.remove(reaction.reaction);
                    reactionCell.setChecked(false);
                } else {
                    selectedReactions.add(reaction.reaction);
                    reactionCell.setChecked(true);
                }
                changed = true;
            });

            reactionsList.addView(reactionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            this.reactionCells.add(reactionCell);
        }
    }

    private ViewPropertyAnimator linearLayoutReactionsContainerFadeAnimation;
    private ValueAnimator reactionsEnabledAnimator;
    private float reactionsEnabledT = 0f;
    private void updateEnabled() {
        if (reactionsCheckSwitch != null)
            reactionsCheckSwitch.setChecked(reactionsEnabled, true);

        if (reactionsEnabledAnimator != null)
            reactionsEnabledAnimator.cancel();
        reactionsEnabledAnimator = ValueAnimator.ofFloat(0f, 1f);
        reactionsEnabledAnimator.addUpdateListener((a) -> {
            float t = (float) a.getAnimatedValue();
            reactionsEnabledT = reactionsEnabled ? t : 1f - t;
            if (reactionsCheck != null)
                reactionsCheck.invalidate();
        });
        reactionsEnabledAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        reactionsEnabledAnimator.setDuration(150);
        reactionsEnabledAnimator.start();

        if (linearLayoutReactionsContainer != null) {
            if (linearLayoutReactionsContainerFadeAnimation != null)
                linearLayoutReactionsContainerFadeAnimation.cancel();
            linearLayoutReactionsContainer.setVisibility(View.VISIBLE);
            linearLayoutReactionsContainerFadeAnimation = linearLayoutReactionsContainer.animate().alpha(reactionsEnabled ? 1f : 0f).setDuration(150).withEndAction(() -> {
                if (!reactionsEnabled)
                    linearLayoutReactionsContainer.setVisibility(View.GONE);
            });
            linearLayoutReactionsContainerFadeAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            linearLayoutReactionsContainerFadeAnimation.start();
        }
    }

    private LinearLayout linearLayout;
    private LinearLayout linearLayoutReactionsContainer;
    private TextInfoPrivacyCell reactionsInfoCell;
    private FrameLayout reactionsCheck;
    private TextView reactionsCheckText;
    private Switch reactionsCheckSwitch;
    private HeaderCell headerCell;
    private LinearLayout reactionsList;
    private ArrayList<ReactionCheckCell> reactionCells = new ArrayList<>();
//    private ActionBarMenuItem doneButton;
//
//    private final static int done_button = 1;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
//                    finishFragment();
                    processDone();
                }// else if (id == done_button) {
//                    processDone();
//                }
            }
        });
//        ActionBarMenu menu = actionBar.createMenu();
//        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        fragmentView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                rectangle.bottom += AndroidUtilities.dp(60);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);
        linearLayout = new LinearLayout(context);
        scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout.setOrientation(LinearLayout.VERTICAL);

        // TODO(dkaraush): text!
        actionBar.setTitle("Reactions");

        reactionsCheck = new FrameLayout(context) {
            private Paint circlePaint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                circlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundChecked));

                int width = getMeasuredWidth(),
                    height = getMeasuredHeight();
                float cx = LocaleController.isRTL ? AndroidUtilities.dp(54) : width - AndroidUtilities.dp(54),
                      cy = height / 2f,
                      l = cx, r = width - cx, t = cy, b = height - cy,
                      R  = (float) Math.sqrt(Math.max(Math.max(l*l + t*t, r*r + t*t), Math.max(l*l + b*b, r*r + b*b)));

                canvas.drawCircle(cx, cy, R * reactionsEnabledT, circlePaint);
                super.onDraw(canvas);
            }
        };
        reactionsCheck.willNotDraw();
        reactionsCheck.setOnClickListener(e -> {
            reactionsEnabled = !reactionsEnabled;
            changed = true;
            if (reactionsEnabled && selectedReactions != null && selectedReactions.size() == 0 && availableReactions != null) {
                ArrayList<String> reactionStrings = new ArrayList<>();
                for (TLRPC.TL_availableReaction reaction : availableReactions)
                    reactionStrings.add(reaction.reaction);
                selectedReactions = reactionStrings;
                updateAvailableReactions();
            }
            updateEnabled();
        });
        reactionsCheck.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundUnchecked));

        reactionsCheckText = new TextView(context);
        reactionsCheckText.setText("Enable Reactions"); // TODO(dkaraush): text!
        reactionsCheckText.setTextColor(Theme.getColor(Theme.key_windowBackgroundCheckText));
        reactionsCheckText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        reactionsCheckText.setTextSize(16);
        reactionsCheckText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        reactionsCheck.addView(reactionsCheckText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 81 : 21, 18, LocaleController.isRTL ? 21 : 81, 18));

        reactionsCheckSwitch = new Switch(context);
        reactionsCheckSwitch.setColors(Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        reactionsCheckSwitch.setChecked(reactionsEnabled, false);
        reactionsCheck.addView(reactionsCheckSwitch, LayoutHelper.createFrame(37, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));

        linearLayout.addView(reactionsCheck, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

        reactionsInfoCell = new TextInfoPrivacyCell(context);
        reactionsInfoCell.setText(isChannel ? "Allow subscribers to react to channel posts." : "Allow members to react to group messages.");
        linearLayout.addView(reactionsInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        linearLayoutReactionsContainer = new LinearLayout(context);
        linearLayoutReactionsContainer.setOrientation(LinearLayout.VERTICAL);
        linearLayoutReactionsContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        headerCell = new HeaderCell(context, 23);
        headerCell.setHeight(46);
        // TODO(dkaraush): text!
        headerCell.setText("Available reactions");
        linearLayoutReactionsContainer.addView(headerCell);

        reactionsList = new LinearLayout(context);
        reactionsList.setOrientation(LinearLayout.VERTICAL);
        reactionsList.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        linearLayoutReactionsContainer.addView(reactionsList, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        linearLayout.addView(linearLayoutReactionsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateAvailableReactions();
        updateEnabled();

        return fragmentView;
    }

    private boolean changed = false;
    private void processDone() {
        if (!changed) {
            finishFragment();
        } else {
            TLRPC.TL_messages_setChatAvailableReactions request = new TLRPC.TL_messages_setChatAvailableReactions();
            request.peer = MessagesController.getInputPeer(currentChat);
            request.available_reactions = reactionsEnabled ? selectedReactions : new ArrayList<>();
            getConnectionsManager().sendRequest(request, (response, error) -> {
                if (error != null && error.text.equals("CHAT_NOT_MODIFIED")) {
                    Toast.makeText(fragmentView.getContext(), "Error", Toast.LENGTH_SHORT).show();
                } else if (response != null) {
                    info.available_reactions = request.available_reactions;
                    getMessagesController().putChatFull(info);
                    if (response instanceof TLRPC.TL_updates)
                        getMessagesController().processUpdates((TLRPC.TL_updates) response, false);
                }

                AndroidUtilities.runOnUIThread(this::finishFragment);
            });
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                info = chatFull;

                if (info != null && !set) {
                    set = true;
                    reactionsEnabled = info.available_reactions != null && info.available_reactions.size() > 0;
                    if (info.available_reactions != null) {
                        selectedReactions = (ArrayList) info.available_reactions.clone();
                        changed = false;
                    }
                    updateAvailableReactions();
                    updateEnabled();
                }
            }
        } else if (id == NotificationCenter.availableReactionsUpdate) {
            ArrayList<TLRPC.TL_availableReaction> reactions = (ArrayList<TLRPC.TL_availableReaction>) args[0];
            if (!changed && !reactionsEnabled && selectedReactions.size() == 0) {
                ArrayList<String> reactionStrings = new ArrayList<>();
                if (reactions != null) {
                    for (TLRPC.TL_availableReaction reaction : reactions)
                        reactionStrings.add(reaction.reaction);
                }
                selectedReactions = reactionStrings;
                updateEnabled();
            }
            updateAvailableReactions(reactions);
        }
    }

}
