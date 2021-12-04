package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.vision.Frame;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ChatMessageScrimPopup extends FrameLayout {

    interface OnReactionChange {
        void run(String reaction);
    }
    public interface UserCallback {
        void run(TLRPC.User user);
    }
    interface OnAppendData {
        void run(ArrayList<ReactionUserData> newData, int newCount);
    }
    private static class ReactionFilterData {
        public ReactionFilterData(String reaction, int count) {
            this.reaction = reaction;
            this.count = count;
        }
        String reaction = null;
        int count = 0;
    }

    private ChatMessageReactionList reactionButtonList;

    private FrameLayout container;

    public MainMenu menu;
    private FrameLayout menuDim;
    private ReactionsMenu reactionsMenu;
//    private ReactionUserList reactionsMenu;
    private FrameLayout reactionsMenuContainer;

    public int currentAccount;
    public MessageObject message;
    private Runnable onDismiss;

    int reactionsCount = 0;

    private class Gap extends FrameLayout {
//        private Paint shadow = new Paint();
        public Gap(Context context) {
            super(context);

            // TODO(dkaraush): shadow!

            setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
            setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
//            canvas.drawRect(0, 0, getWidth(), getHeight(), shadow);
        }
    }

    private class ReactionImage extends BackupImageView implements NotificationCenter.NotificationCenterDelegate {
        private int size;
        public ReactionImage(Context context, int size) {
            super(context);
            this.size = size;
        }

        private String reactionString;
        public void setReaction(String reaction) {
            reactionString = reaction;

            ArrayList<TLRPC.TL_availableReaction> allReactions = MessagesController.getInstance(currentAccount).getAvailableReactions();
            TLRPC.TL_availableReaction myReaction = null;
            if (allReactions != null) {
                for (TLRPC.TL_availableReaction r : allReactions) {
                    if (r != null && r.reaction != null && r.reaction.equals(reaction)) {
                        myReaction = r;
                        break;
                    }
                }
            }
            if (myReaction != null && myReaction.static_icon != null) {
                setImage(ImageLocation.getForDocument(myReaction.static_icon), null, null, "webp", size, null);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.availableReactionsUpdate);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.availableReactionsUpdate);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.availableReactionsUpdate) {
                setReaction(this.reactionString);
            }
        }
    }

    public int maxPossibleHeight = dp(1000);

    public class MainMenu extends LinearLayout {
        private FrameLayout topButtonContainer;
        private FrameLayout topButton;
        private ImageView topButtonIcon;
        private BackupImageView topButtonReaction;
        private AvatarsImageView topAvatarsImageView;
        private TextView topButtonText;
        private FrameLayout topButtonGap;
        private ActionBarPopupWindow.ActionBarPopupWindowLayout buttons;

        public MainMenu(Context context, ActionBarPopupWindow.ActionBarPopupWindowLayout buttons) {
            super(context);
            this.buttons = buttons;

            setOrientation(LinearLayout.VERTICAL);
            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
            setPivotX(0f);
            setPivotY(0f);

            reactionsCount = 0;
            if (message != null && message.hasReactions()) {
                for (TLRPC.TL_reactionCount rc : message.messageOwner.reactions.results)
                    reactionsCount += rc.count;
            }

            topButtonContainer = new FrameLayout(context);
            topButtonContainer.setPivotY(0f);
            topButtonContainer.setScaleY(shouldShowTopButton() ? 1f : 0f);
            topButtonContainer.setVisibility(shouldShowTopButton() ? View.VISIBLE : View.GONE);

            topButton = new FrameLayout(context);
            topButton.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 6, 0));

            topButtonIcon = new ImageView(context);
            topButtonIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogIcon), PorterDuff.Mode.MULTIPLY));
            topButton.addView(topButtonIcon, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.FILL_VERTICAL, 11, 10, 6, 9));

            topButtonReaction = new BackupImageView(context);
            topButton.addView(topButtonReaction, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.FILL_VERTICAL, 11, 10, 6, 9));

            topAvatarsImageView = new AvatarsImageView(context, false);
            topAvatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);
            topAvatarsImageView.setAlpha(0f);
            topButton.addView(topAvatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 3, 0));

            topButtonText = new TextView(context);
            topButtonText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            topButtonText.setTextSize(16);
            topButton.addView(topButtonText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 42, 11, 70, 12));

            topButtonContainer.addView(topButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 8));

            topButtonGap = new Gap(context);
            topButtonContainer.addView(topButtonGap, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

            addView(topButtonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            buttons.setTopButtonSelector(false);
            buttons.updateRadialSelectors();
            buttons.setFitItems(false);
            try {
                buttons.scrollView.getLayoutParams().width = LayoutHelper.MATCH_PARENT;
                buttons.linearLayout.getLayoutParams().width = LayoutHelper.MATCH_PARENT;
            } catch (Exception e) {}
            addView(buttons);

            updateReactionsButton(false);
        }

        public void setOnTopButtonClick(Runnable onTopButtonClick) {
            topButton.setOnClickListener(e -> {
                if (onTopButtonClick != null)
                    onTopButtonClick.run();
            });
        }

        private ValueAnimator topButtonTextAnimation = null;
        private void animateTopButtonTextRightMargin(float rightMargin, boolean animated) {
            if (topButtonTextAnimation != null) {
                topButtonTextAnimation.cancel();
                topButtonTextAnimation = null;
            }

            if (animated) {
                topButtonTextAnimation = ValueAnimator.ofFloat(
                    ((MarginLayoutParams) topButtonText.getLayoutParams()).rightMargin,
                    dp(rightMargin)
                );
                topButtonTextAnimation.addUpdateListener(a -> {
                    float rightMarginValue = (float) a.getAnimatedValue();
                    ((MarginLayoutParams) topButtonText.getLayoutParams()).rightMargin = (int) rightMarginValue;
                    forceLayout();
                });
                topButtonTextAnimation.setDuration(220);
                topButtonTextAnimation.start();
            } else {
                ((MarginLayoutParams) topButtonText.getLayoutParams()).rightMargin = dp(rightMargin);
                forceLayout();
            }
        }

        private boolean reactionsButtonShown = true;
        public void updateReactionsButton(boolean animated) {
            ArrayList<TLRPC.User> reactionUsers = new ArrayList<>();
            String reactionString = null;
            if (shouldShowReactionsButton()) {
                reactionsCount = 0;
                if (message != null && message.hasReactions()) {
                    for (TLRPC.TL_reactionCount rc : message.messageOwner.reactions.results) {
                        if (rc.count > 0)
                            reactionString = rc.reaction;
                        reactionsCount += rc.count;
                    }
                    if (message.messageOwner.reactions.recent_reactons != null && message.messageOwner.reactions.recent_reactons.size() > 0) {
                        for (TLRPC.TL_messageUserReaction mur : message.messageOwner.reactions.recent_reactons)
                            reactionUsers.add(MessagesController.getInstance(currentAccount).getUser(mur.user_id));
                    }
                }
            }

            if (shouldShowReactionsButton() && reactionsCount == 1 && reactionUsers.size() == 1 && (!shouldShowMessageSeen() || (!messageSeenLoading && messageSeenPeerIds.size() == 1))) {

                TLRPC.User user = reactionUsers.get(0);
                topButtonText.setText(user != null ? ContactsController.formatName(user.first_name, user.last_name) : "");

                topAvatarsImageView.clearAnimation();
                topAvatarsImageView.animate().alpha(1f).translationX(dp(24)).setDuration(animated ? 220 : 0).start();
                topAvatarsImageView.setObject(0, currentAccount, user);
                topAvatarsImageView.setObject(1, currentAccount, null);
                topAvatarsImageView.setObject(2, currentAccount, null);
                topAvatarsImageView.commitTransition(animated);

                animateTopButtonTextRightMargin(33, animated);

                topButtonIcon.clearAnimation();
                topButtonIcon.animate().alpha(0f).setDuration(animated ? 220 : 0).start();

                TLRPC.TL_availableReaction reaction = null;
                ArrayList<TLRPC.TL_availableReaction> allReactions = MessagesController.getInstance(currentAccount).getAvailableReactions();
                if (allReactions != null) {
                    for (TLRPC.TL_availableReaction r : allReactions) {
                        if (r != null && r.reaction != null && r.reaction.equals(reactionString)) {
                            reaction = r;
                            break;
                        }
                    }
                }
                if (reaction != null) {
                    topButtonReaction.setImage(ImageLocation.getForDocument(reaction.static_icon), null, "webp", null, this);
                    topButtonReaction.clearAnimation();
                    topButtonReaction.animate().alpha(1f).setDuration(animated ? 220 : 0).start();
                }
            } else {
                topButtonIcon.clearAnimation();
                topButtonIcon.animate().alpha(1f).setDuration(animated ? 220 : 0).start();
                topButtonReaction.clearAnimation();
                topButtonReaction.animate().alpha(0f).setDuration(animated ? 220 : 0).start();

                ArrayList<TLRPC.User> users = new ArrayList<>();
                if (!shouldShowMessageSeen()) {
                    topButtonIcon.setImageResource(R.drawable.msg_reactions);
                    topButtonText.setText(reactionsCount + " Reactions"); // TODO(dkaraush): text!
                    users.addAll(reactionUsers);
                } else {
                    if (shouldShowReactionsButton()) {
                        topButtonIcon.setImageResource(R.drawable.msg_reactions);
                        topButtonText.setText(reactionsCount + "/" + (messageSeenPeerIds.size() + 1) + " Reactions"); // TODO(dkaraush): text!
                        users.addAll(reactionUsers);
//                        if (users.size() < 3) {
//                            for (TLRPC.User messageSeenUser : messageSeenUsers) {
//                                boolean alreadyHasThisUser = false;
//                                for (TLRPC.User u : users) {
//                                    if (u.id == messageSeenUser.id) {
//                                        alreadyHasThisUser = true;
//                                        break;
//                                    }
//                                }
//
//                                if (!alreadyHasThisUser)
//                                    users.add(messageSeenUser);
//                                if (users.size() >= 3)
//                                    break;
//                            }
//                        }
                    } else {
                        if (message != null && (message.isRoundVideo() || message.isVoice())) {
                            topButtonIcon.setImageResource(R.drawable.msg_played);
                            topButtonText.setText(messageSeenPeerIds.size() + " Played"); // TODO(dkaraush): text!
                        } else {
                            topButtonIcon.setImageResource(R.drawable.msg_seen);
                            topButtonText.setText(messageSeenPeerIds.size() + " Seen"); // TODO(dkaraush): text!
                        }
                        for (int i = 0; i < Math.min(messageSeenUsers.size(), 3); ++i)
                            users.add(messageSeenUsers.get(i));
                    }
                }

                topAvatarsImageView.clearAnimation();
                for (int i = 0; i < 3; ++i) {
                    topAvatarsImageView.setObject(i, currentAccount, i < users.size() ? users.get(i) : null);
                }
                float tx = 0;
                if (users.size() == 1) {
                    tx = dp(24);
                } else if (users.size() == 2) {
                    tx = dp(12);
                }
                topAvatarsImageView.animate().alpha(1f).translationX(tx).setDuration(animated ? 220 : 0).start();
                topAvatarsImageView.commitTransition(animated);
                animateTopButtonTextRightMargin(9 + (users.size() > 0 ? 24 + (users.size() - 1) * 14 : 0), animated);
            }

            if (reactionsButtonShown != shouldShowTopButton()) {
                reactionsButtonShown = shouldShowTopButton();
                buttons.setTopButtonSelector(!reactionsButtonShown);
                buttons.updateRadialSelectors();

                if (reactionsButtonShown)
                    reactionsMenuContainer.setVisibility(View.VISIBLE);
                topButtonContainer.animate().scaleY(reactionsButtonShown ? 1f : 0f).setDuration(animated ? 150 : 0).withEndAction(() -> {
                    if (!reactionsButtonShown) {
                        topButtonContainer.setVisibility(View.GONE);
                        reactionsMenuContainer.setVisibility(View.GONE);
                    }
                }).start();
            }
        }
    }

    public class ReactionsMenu extends LinearLayout {
        private FrameLayout backButton;
        private ImageView backButtonIcon;
        private TextView backButtonText;

        private FrameLayout filtersContainer;
        private ReactionFilters filters;
        private Gap gap;
        private FrameLayout contentContainer;

        public ArrayList<ReactionFilterData> tabs = new ArrayList<>();
        private String currentTab = null;

        private ReactionUserList tab = null;
        private ReactionUserList nextTab = null;

        public ReactionsMenu(Context context) {
            super(context);

            setOrientation(LinearLayout.VERTICAL);
            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

            backButton = new FrameLayout(context);
            backButton.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 6, 0));

            backButtonText = new TextView(context);
            backButtonText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            backButtonText.setTextSize(16);
            backButtonText.setText(LocaleController.getString("Back", R.string.Back));
            backButton.addView(backButtonText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 56, 0, 0, 0));

            backButtonIcon = new ImageView(context);
            backButtonIcon.setImageResource(R.drawable.msg_arrow_back);
            backButtonIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogIcon), PorterDuff.Mode.MULTIPLY));
            backButton.addView(backButtonIcon, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 13, 0, 0, 0));

            addView(backButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, Gravity.FILL_HORIZONTAL | Gravity.TOP));

            filtersContainer = new FrameLayout(context);
            filters = new ReactionFilters(context);
            filters.setOnReactionChange(newFilter -> {
//                changeTab(newFilter, true);
                innerScrollTo(getTabIndex(newFilter));
            });
            filtersContainer.addView(filters, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.FILL_HORIZONTAL));
            addView(filtersContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.FILL_HORIZONTAL));
            setReactionFilters();

            gap = new Gap(context);
            addView(gap);

            contentContainer = new FrameLayout(context);
            contentContainer.addView(nextTab = new ReactionUserList(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            contentContainer.addView(tab = new ReactionUserList(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            addView(contentContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1f));

            updateFiltersShow(false);

            setMinimumHeight(estimateHeight());
            messageSeenCallbacks.put(messageSeenCallbacksIndex++, () -> {
                setMinimumHeight(estimateHeight());
            });

            tab.set(currentTab, getInitialCount(currentTab));
        }
//
//        @Override
//        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//            heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(maxPossibleHeight - dp(16), Math.max(estimateHeight(), MeasureSpec.getSize(heightMeasureSpec))), MeasureSpec.getMode(heightMeasureSpec));
//            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        }

        public void init() {
            if (tab != null) {
//                tab.listView.setAdapter(tab.adapter);
            }
        }

        private int getInitialCount(String filter) {
            int initialCount = 0;
            if (filter == null) {
                initialCount = Math.max(reactionsCount, Math.max(messageSeenUsers == null ? 0 : messageSeenUsers.size(), messageSeenPeerIds == null ? 0 : messageSeenPeerIds.size()));
            } else if (message != null && message.messageOwner != null && message.messageOwner.reactions != null && message.messageOwner.reactions.results != null) {
                for (TLRPC.TL_reactionCount rc : message.messageOwner.reactions.results) {
                    if (rc != null && rc.reaction != null && rc.reaction.equals(filter)) {
                        initialCount += rc.count;
                    }
                }
            }
            return initialCount;
        }

//        public void changeTab(String newFilter, boolean fromLeft) {
//            nextTab.set(newFilter, getInitialCount(newFilter));
//
//            int width = container.getMeasuredWidth() - dp(16);
//            nextTab.setAlpha(0f);
//            nextTab.setTranslationX(fromLeft ? -width : width);
//            nextTab.bringToFront();
//            tab.animate().translationX(fromLeft ? width : -width).setDuration(150).start();
//            nextTab.animate().translationX(0f).alpha(1f).setDuration(150).withEndAction(() -> {
//                ReactionUserList prevTab = tab;
//                tab = nextTab;
//                nextTab = prevTab;
//            }).start();
//        }


        private boolean scrolling = false;
        private float scrollFromX = 0f;
        private float scrollT = 0f;
        private float scrollFromT = 0f;
        private boolean scrollingCapturing = false;
        private ValueAnimator scrollingAnimation = null;
        public boolean definitelyScrolling = false;
        public boolean handleTouchEvent(MotionEvent event, boolean capturing) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN && event.getY() > dp(48) + estimateTopHeight() + dp(8)) {
                if (scrollingAnimation != null) {
                    scrollingAnimation.cancel();
                    scrollingAnimation = null;
                }
                scrolling = true;
                scrollingCapturing = capturing;
                scrollFromX = event.getX();
                scrollFromT = scrollT;
                return true;
            } else if (scrolling) {
                float dx = (scrollFromX - event.getX()) / dp(200);
                if (scrollingCapturing)
                    dx = Math.copySign(Math.max(0, Math.abs(dx) - 0.2f), dx);

                if (action == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(dx) > 0.1f)
                        definitelyScrolling = true;
                    updateInnerScroll(scrollT = (scrollFromT + dx));
                    if (scrollT < 0f) {
                        updateScroll(1f + scrollT);
                    } else
                        updateScroll(1f);
                    return true;
                } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                    scrollT = (scrollFromT + dx);
                    if (scrollT < 0f) {
                        ChatMessageScrimPopup.this.scrollTo(scrollT <= -0.15f ? 0f : 1f);
                    }

                    float roundedScrollT = Math.round(scrollFromT) + (Math.abs(scrollFromT - scrollT) > 0.2f ? (scrollT > scrollFromT ? 1f : -1f) : 0);
                    float scrollTo = Math.min(Math.max(0, roundedScrollT), tabs == null ? 0 : tabs.size() - 1);
                    innerScrollTo(scrollTo);

                    scrolling = false;
                    definitelyScrolling = false;
                    return true;
                }
            }
            return false;
        }
        public void innerScrollTo(float scrollTo) {
            if (scrollingAnimation != null) {
                scrollingAnimation.cancel();
                scrollingAnimation = null;
            }

            int lower = (int) Math.floor(Math.min(Math.max(0, scrollT), scrollTo)),
                upper = (int)  Math.ceil(Math.max(Math.max(0, scrollT), scrollTo));
            if (lower == upper) {
                updateInnerScroll(scrollT = Math.max(0, scrollT));
                return;
            }

            scrollingAnimation = ValueAnimator.ofFloat(Math.max(0, scrollT), scrollTo);
            scrollingAnimation.addUpdateListener(a -> {
                updateInnerScroll(scrollT = Math.max(0, (float) a.getAnimatedValue()), lower, upper);
            });
            scrollingAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
            scrollingAnimation.setDuration((long) (Math.min(1, Math.abs(scrollTo - scrollT)) * 250));
            scrollingAnimation.start();

            setTabIndex((int) Math.round(scrollTo));
        }

        public int getTabIndex(String filter) {
            int currentTabIndex = 0;
            if (tabs != null) {
                for (int i = 0; i < tabs.size(); ++i) {
                    ReactionFilterData f = tabs.get(i);
                    if (f != null && ((f.reaction == null && filter == null) || (f.reaction != null && f.reaction.equals(filter)))) {
                        currentTabIndex = i;
                        break;
                    }
                }
            }
            return currentTabIndex;
        }
        public void setTabIndex(int index) {
            if (index < 0)
                return;
            String tabFilter = null;
            if (tabs != null) {
                ReactionFilterData f = index < tabs.size() ? tabs.get(index) : null;
                if (f != null)
                    tabFilter = f.reaction;
            }
            if (filters != null && !((currentTab == null && tabFilter == null) || (currentTab != null && currentTab.equals(tabFilter))))
                filters.select(tabFilter);
            currentTab = tabFilter;
        }
        public void updateInnerScroll(float t) {
            t = Math.min(Math.max(t, 0), tabs == null ? 1f : (float) tabs.size() - 1);
            int lower = (int) t,
                upper = (int) (t + 1);
            updateInnerScroll(t, lower, upper);
        }
        public void updateInnerScroll(float t, int lower, int upper) {
            t = Math.min(Math.max(t, 0), tabs == null ? 1f : (float) tabs.size() - 1);
            float T = 1f - (t - (float) lower) / (float) (upper - lower);

            int width = container.getMeasuredWidth() - dp(16);

            int tabIndex = getTabIndex(tab.currentFilter),
                nextTabIndex = getTabIndex(nextTab.currentFilter);
            if (tabIndex != lower && tabIndex != upper) {
                tabIndex = nextTabIndex == upper ? lower : upper;
                if (tabs != null) {
                    String filter = tabIndex < tabs.size() ? tabs.get(tabIndex).reaction : null;
                    tab.set(filter, getInitialCount(filter));
                }
            }
            if (!nextTab.inited || (nextTabIndex != lower && nextTabIndex != upper)) {
                nextTabIndex = tabIndex == upper ? lower : upper;
                if (tabs != null) {
                    String filter = nextTabIndex < tabs.size() ? tabs.get(nextTabIndex).reaction : null;
                    nextTab.set(filter, getInitialCount(filter));
                }
            }

            tab.setTranslationX(width * T - (tabIndex == lower ? width : 0));
            nextTab.setTranslationX(width * T - (nextTabIndex == lower ? width : 0));
        }

        public int estimateHeight() {
            return estimateTopHeight() + estimateContentHeight();
        }
        public int estimateTopHeight() {
            return (int) (dp(44) + (shouldShowFilters() ? dp(40 + 1) : dp(8)));
        }
        public int estimateContentHeight() {
            return (int) (Math.min((float) Math.max(messageSeenUsers.size(), reactionsCount), 7.5f) * dp(48));
        }

        public void setReactionFilters() {
            ArrayList<ReactionFilterData> newTabs = new ArrayList<>();
            boolean changed = false;
            newTabs.add(new ReactionFilterData(null, reactionsCount));
            if (shouldShowFilters()) {
                if (message != null && message.messageOwner != null && message.messageOwner.reactions != null && message.messageOwner.reactions.results != null) {
                    for (TLRPC.TL_reactionCount rc : message.messageOwner.reactions.results) {

                        if (!changed) {
                            for (ReactionFilterData t : tabs) {
                                if (t != null && t.reaction != null && t.reaction.equals(rc.reaction)) {
                                    if (rc.count != t.count)
                                        changed = true;
                                    break;
                                }
                            }
                        }

                        newTabs.add(new ReactionFilterData(rc.reaction, rc.count));
                    }
                }
            }

            if (tabs.size() != newTabs.size() || changed) {
                tabs = newTabs;
                filters.setReactions(tabs);
            }
        }

        public void setOnBackClick(Runnable onBackClick) {
            backButton.setOnClickListener(e -> {
                if (onBackClick != null)
                    onBackClick.run();
            });
        }

        private boolean shouldShowFilters() {
            return reactionsCount >= 10;
        }
        private ValueAnimator filtersShowAnimation1 = null,
                              filtersShowAnimation2 = null;
        public void updateFiltersShow(boolean animated) {
            if (filters != null)
                setReactionFilters();

            boolean shown = shouldShowFilters();
            if (filtersContainer != null) {
                filtersContainer.setVisibility(shown ? View.VISIBLE : View.GONE);
            }
            if (gap != null) {
                int wasHeight = ((MarginLayoutParams) gap.getLayoutParams()).height;
                ((MarginLayoutParams) gap.getLayoutParams()).height = shown ? dp(1) : dp(8);
                if (((MarginLayoutParams) gap.getLayoutParams()).height != wasHeight)
                    forceLayout();
            }


//            if (filtersShowAnimation1 != null)
//                filtersShowAnimation1.cancel();
//            if (filtersShowAnimation2 != null)
//                filtersShowAnimation2.cancel();
//            clearAnimation();
//            filtersContainer.animate().scaleY(shown ? 1f : 0f).alpha(shown ? 1f : 0f).setDuration(animated ? 220 : 0).start();
//            if (animated) {
//                if (filters != null) {
//                    filtersShowAnimation1 = ValueAnimator.ofFloat(
//                            ((MarginLayoutParams) filtersContainer.getLayoutParams()).height,
//                            shown ? dp(36) : 0
//                    );
//                    filtersShowAnimation1.addUpdateListener(a -> {
//                        ((MarginLayoutParams) filtersContainer.getLayoutParams()).height = (int) (float) a.getAnimatedValue();
//                        forceLayout();
//                    });
//                    filtersShowAnimation1.setDuration(220);
//                    filtersShowAnimation1.start();
//                }
//
//                if (gap != null) {
//                    filtersShowAnimation2 = ValueAnimator.ofFloat(
//                            ((MarginLayoutParams) gap.getLayoutParams()).height,
//                            shown ? dp(1) : dp(8)
//                    );
//                    filtersShowAnimation2.addUpdateListener(a -> {
//                        ((MarginLayoutParams) gap.getLayoutParams()).height = (int) (float) a.getAnimatedValue();
//                        forceLayout();
//                    });
//                    filtersShowAnimation2.setDuration(220);
//                    filtersShowAnimation2.start();
//                }
//            } else {
//                if (filters != null)
//                    ((MarginLayoutParams) filtersContainer.getLayoutParams()).height = shown ? dp(36) : 0;
//                if (gap != null)
//                    ((MarginLayoutParams) gap.getLayoutParams()).height = shown ? dp(1) : dp(8);
//                forceLayout();
//            }
        }


    }

    private class ReactionFilters extends FrameLayout {
        private RecyclerListView list;
        private LinearLayoutManager layoutManager;
        private ReactionFiltersAdapter adapter;

        public ReactionFilters(Context context) {
            super(context);

            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
            setPivotY(0f);

            list = new RecyclerListView(context);
            list.setLayoutManager(layoutManager = new LinearLayoutManager(context));
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            list.setClipChildren(true);
            list.setAdapter(adapter = new ReactionFiltersAdapter(context));

            addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        private OnReactionChange onReactionChange = null;
        public void setOnReactionChange(OnReactionChange onReactionChange) {
            this.onReactionChange = onReactionChange;
        }

        public void setReactions(ArrayList<ReactionFilterData> reactions) {
            if (adapter != null)
                adapter.setData(reactions);
        }

        public void select(String reaction) {
            if (adapter != null) {
                int position = adapter.select(reaction);
                list.smoothScrollToPosition(position);
            }
        }

        private class ReactionFiltersAdapter extends RecyclerView.Adapter {
            Context context;
            public ReactionFiltersAdapter(Context context) {
                this.context = context;
            }

            private ArrayList<ReactionFilterData> data;
            public void setData(ArrayList<ReactionFilterData> data) {
                this.data = data;
                notifyDataSetChanged();
            }
            private String selectedReaction = null;
            public int select(String reaction) {
                int selectedIndex = -1, newSelectedIndex = -1;
                if (data != null) {
                    for (int i = 0; i < data.size(); ++i) {
                        ReactionFilterData f = data.get(i);
                        if ((f.reaction == null && selectedReaction == null) ||
                                (f.reaction != null && f.reaction.equals(selectedReaction))) {
                            selectedIndex = i;
                        }
                        if ((f.reaction == null && reaction == null) ||
                                (f.reaction != null && f.reaction.equals(reaction))) {
                            newSelectedIndex = i;
                        }
                        if (newSelectedIndex != -1 && selectedIndex != -1)
                            break;
                    }
                }

                selectedReaction = reaction;
                if (newSelectedIndex != selectedIndex && selectedIndex != -1)
                    notifyItemChanged(selectedIndex);
                if (newSelectedIndex != -1)
                    notifyItemChanged(newSelectedIndex);

                return newSelectedIndex;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerListView.Holder(new ReactionFilter(context));
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ReactionFilterData f = data == null ? null : data.get(position);
                ReactionFilter view = ((ReactionFilter) holder.itemView);
                view.setSelected((f != null && f.reaction == null && selectedReaction == null) || (f != null && f.reaction != null && f.reaction.equals(selectedReaction)), view.position == position);
                view.set(f, position == 0, position == getItemCount() - 1, position);
            }

            @Override
            public int getItemCount() {
                return this.data == null ? 0 : this.data.size();
            }
        }

        private class ReactionFilter extends FrameLayout {
            private ImageView iconView;
            private ReactionImage reactionView;
            private TextView textView;
            private FrameLayout container;

            private Paint backgroundPaint = new Paint();
            private Paint borderPaint = new Paint();

            private int borderStroke = dp(4f / 3f);

            public ReactionFilter(Context context) {
                super(context);

                setPadding(borderStroke, borderStroke, borderStroke, borderStroke);
                setLayoutParams(LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 26, 0, 0, 6f, 10f));

                container = new FrameLayout(context);

                iconView = new ImageView(context);
                iconView.setImageResource(R.drawable.msg_reactions_filled);
                iconView.setColorFilter(new PorterDuffColorFilter(0xff378dd1, PorterDuff.Mode.MULTIPLY)); // TODO(dkaraush): color!
                container.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 5.33f, -1f, 0, 0));

                reactionView = new ReactionImage(context, dp(20));
                container.addView(reactionView, LayoutHelper.createFrame(20, 20, Gravity.LEFT | Gravity.CENTER_VERTICAL, 9, 0, 0, 0));

                textView = new TextView(context);
                textView.setTextColor(0xff378dd1); // TODO(dkaraush): color!
                textView.setTextSize(12);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                container.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 32, 0, 10, 0));

                addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
                container.setBackground(Theme.createRadSelectorDrawable(0x22378dd1, 0, 0)); // TODO(dkaraush): color!

                backgroundPaint.setAntiAlias(true);
                backgroundPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
                backgroundPaint.setColor(0x19378dd1); // TODO(dkaraush): color!
                borderPaint.setAntiAlias(true);
                borderPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setColor(0xcc378dd1); // TODO(dkaraush): color!
            }

            private float borderT = 0f;
            private RectF rect = new RectF();
            private Path path = new Path();
            @Override
            protected void dispatchDraw(Canvas canvas) {
                int w = getWidth(), h = getHeight();
                path.reset();
                rect.set(borderStroke, borderStroke, w - borderStroke, h - borderStroke);
                path.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW);
                canvas.drawPath(path, backgroundPaint);

                if (borderT > 0f) {
                    borderPaint.setStrokeWidth(borderStroke * borderT);
                    canvas.drawPath(path, borderPaint);
                }

                canvas.clipPath(path);
                super.dispatchDraw(canvas);
            }

            public int position = -1;
            public void set(ReactionFilterData data, boolean isFirst, boolean isLast, int newPosition) {
                position = newPosition;
                container.setOnClickListener(e -> {
                    if (data == null)
                        return;

                    adapter.select(data.reaction);
                    if (onReactionChange != null)
                        onReactionChange.run(data.reaction);
                });
                setLayoutParams(LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 26, isFirst ? 9f : 0f, 0, isLast ? 9f : 6f, 10f));
                if (data == null || data.reaction == null) {
                    iconView.setVisibility(View.VISIBLE);
                    reactionView.setVisibility(View.GONE);
                } else {
                    iconView.setVisibility(View.GONE);
                    reactionView.setVisibility(View.VISIBLE);
                    reactionView.setReaction(data.reaction);
                }
                if (data != null)
                    textView.setText(data.count + "");
            }
            private ValueAnimator selectionAnimation = null;
            public void setSelected(boolean selected, boolean animated) {
                if (selectionAnimation != null) {
                    selectionAnimation.cancel();
                    selectionAnimation = null;
                }
                if (animated) {
                    selectionAnimation = ValueAnimator.ofFloat(borderT, selected ? 1f : 0f);
                    selectionAnimation.addUpdateListener(a -> {
                        borderT = (float) a.getAnimatedValue();
                        invalidate();
                    });
                    selectionAnimation.setDuration((long) (Math.abs(borderT - (selected ? 1f : 0f)) * 100));
                    selectionAnimation.start();
                } else {
                    borderT = selected ? 1f : 0f;
                    invalidate();
                }
            }
        }
    }
    public class ReactionUserList extends FrameLayout {
        public boolean inited = false;
        public String currentFilter = null;
        private RecyclerListView listView = null;
        private ReactionUserListAdapter adapter;
        private LinearLayoutManager layoutManager;

        public ReactionUserList(Context context) {
            super(context);

            listView = new RecyclerListView(getContext());
            listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext()));
            listView.setAdapter(this.adapter = new ReactionUserListAdapter(getContext(), 0));
            layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        }

        private ReactionUserListLoader loader;
        public void set(String filter, int initialCount) {
            inited = true;
            currentFilter = filter;

            loader = loaders.get(filter);
            if (loader == null) {
                loaders.put(filter, loader = new ReactionUserListLoader(filter));
            }
            loader.setOnAppendData((newData, newCount) -> {
                if (loader == null)
                    return;

                if (this.adapter.loader == null) {
                    this.adapter.setLoader(loader);
                } else {
                    if (loader != null && loader.loaderData != null && newData != null)
                        this.adapter.notifyItemRangeChanged(loader.loaderData.size() - newData.size(), newData.size());
                    else
                        this.adapter.notifyDataSetChanged();
                }

                if (newCount > 0 && newCount != adapter.initialCount) {
                    adapter.initialCount = newCount;
                    this.adapter.notifyDataSetChanged();
                }
            });
            loader.load();

            this.adapter.initialCount = initialCount;
            this.adapter.setLoader(loader);
            listView.setAdapter(this.adapter);
        }
        public void reset() {
            if (loader != null) {
                loader.setOnAppendData(null);
                loader = null;
                this.adapter.setLoader(null);
            }
        }
        public void load() {
            if (loader != null)
                loader.load();
        }


    }
    public class ReactionUserData {
        public ReactionUserData(TLRPC.User user, String reaction) {
            this.user = user;
            this.reaction = reaction;
        }
        TLRPC.User user;
        String reaction;
    }
    private HashMap<String, ReactionUserListLoader> loaders = new HashMap<>();
    public class ReactionUserListLoader {
        public String filter;
        public ReactionUserListLoader(String filter) {
            this.filter = filter;
        }

        public ArrayList<ReactionUserData> loaderData = new ArrayList<>();
        private OnAppendData onAppendData;
        public void setOnAppendData(OnAppendData onAppendData) {
            this.onAppendData = onAppendData;
        }

        private String offset = null;
        private boolean hasMore = true;
        public boolean loading = false;
        public int count = -1;
        private ArrayList<Integer> listeningForMessageSeen = new ArrayList<>();
        public void load() {
            if (message == null || message.messageOwner == null || this.loading || !hasMore)
                return;

            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
            req.peer = messagesController.getInputPeer(message.messageOwner.peer_id);
            req.id = message.messageOwner.id;
            if (filter != null) {
                req.reaction = filter;
                req.flags |= 1;
            }
            req.limit = filter == null ? 100 : 50;
            if (offset != null) {
                req.offset = offset;
                req.flags |= 2;
            }
            this.loading = true;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null && response instanceof TLRPC.TL_messages_messageReactionsList) {
                    TLRPC.TL_messages_messageReactionsList res = (TLRPC.TL_messages_messageReactionsList) response;

                    for (TLRPC.User u : res.users) {
                        messagesController.putUser(u, false);
                    }

                    ArrayList<ReactionUserData> newData = new ArrayList<>();
                    if (res.reactions != null) {
                        for (TLRPC.TL_messageUserReaction r : res.reactions) {
                            String reaction = r.reaction != null && r.reaction.length() > 0 ? r.reaction : null;
                            newData.add(new ReactionUserData(messagesController.getUser(r.user_id), reaction));
                        }
                    }

                    offset = res.next_offset;
                    hasMore = res.next_offset != null;
                    if (filter != null)
                        count = res.count;
                    AndroidUtilities.runOnUIThread(() -> {
                        loaderData.addAll(newData);
                        if (onAppendData != null)
                            onAppendData.run(newData, count);

                        if (!hasMore && filter == null) {
                            appendMessageSeen();
                        }
                    });
                } else {

                }
                this.loading = false;
            });
        }

        private void appendMessageSeen() {
            if (!messageSeenLoading) {
                ArrayList<ReactionUserData> messageSeenData = new ArrayList<>();
                for (TLRPC.User user : messageSeenUsers) {
                    boolean alreadyHasThatUser = false;
                    if (loaderData != null) {
                        for (ReactionUserData d : loaderData) {
                            if (d != null && d.user != null && user != null && d.user.id == user.id) {
                                alreadyHasThatUser = true;
                                break;
                            }
                        }
                    }
                    if (!alreadyHasThatUser)
                        messageSeenData.add(new ReactionUserData(user, null));
                }
                loaderData.addAll(messageSeenData);
                if (onAppendData != null)
                    onAppendData.run(messageSeenData, loaderData.size());
            } else {
                int index;
                messageSeenCallbacks.put(index = messageSeenCallbacksIndex++, () -> {
                    ArrayList<ReactionUserData> messageSeenData = new ArrayList<>();
                    for (TLRPC.User user : messageSeenUsers) {
                        boolean alreadyHasThatUser = false;
                        if (loaderData != null) {
                            for (ReactionUserData d : loaderData) {
                                if (d != null && d.user != null && user != null && d.user.id == user.id) {
                                    alreadyHasThatUser = true;
                                    break;
                                }
                            }
                        }
                        if (!alreadyHasThatUser)
                            messageSeenData.add(new ReactionUserData(user, null));
                    }
                    loaderData.addAll(messageSeenData);
                    if (onAppendData != null)
                        onAppendData.run(messageSeenData, loaderData.size());
                    listeningForMessageSeen.remove(Integer.valueOf(index));
                });
                listeningForMessageSeen.add(index);
            }
        }
    }
    private class ReactionUserListAdapter extends RecyclerView.Adapter {
        private Context context;
        private int initialCount = 0;
        public ReactionUserListAdapter(Context context, int initialCount) {
            this.context = context;
            this.initialCount = initialCount;
        }
        private ReactionUserListLoader loader = null;
        public void setLoader(ReactionUserListLoader loader) {
            this.loader = loader;
            notifyDataSetChanged();
        }

//                public ArrayList<ReactionUserData> data = new ArrayList<>();
//                public void setData(ArrayList<ReactionUserData> newData) {
//                    int wasCount = data.size();
//                    data.clear();
//                    if (newData != null)
//                        data.addAll(newData);
//                    int count = Math.max(wasCount, newData == null ? 0 : newData.size());
//                    if (count > 0) {
//                        notifyItemRangeChanged(0, count);
//                    }
//                }
//                public void appendData(ArrayList<ReactionUserData> newData) {
//                    if (newData == null)
//                        return;
//                    int wasCount = data.size(), count = newData.size();
//                    data.addAll(newData);
//                    if (count > 0) {
//                        notifyItemRangeChanged(wasCount, count);
//                    }
//                }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ReactionUser(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ReactionUser view = (ReactionUser) holder.itemView;
            ReactionUserData itemData = loader == null || position >= loader.loaderData.size() ? null : loader.loaderData.get(position);
            view.set(itemData == null ? null : itemData.user, itemData == null ? null : itemData.reaction, position);
            view.setLoading(itemData == null);

            if (itemData == null && loader != null && !loader.loading && loader.hasMore)
                loader.load();
        }

        @Override
        public int getItemCount() {
            return Math.max(initialCount, loader == null ? 0 : loader.loaderData.size());
        }
    }
    private static long globalStart = -1;
    public class ReactionUser extends FrameLayout {
        private BackupImageView imageView;
        private AvatarDrawable avatarDrawable;
        private TextView textView;
        private ReactionImage reactionImage;
        private FrameLayout container;
        private TLRPC.User user;

        public ReactionUser(Context context) {
            super(context);

            setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

            if (globalStart < 0)
                globalStart = System.currentTimeMillis();

            float gradientWidth = dp(350f);
            int c1 = Theme.getColor(Theme.key_dialogBackground),
                c2 = Theme.getColor(Theme.key_dialogBackgroundGray);
            Paint loadingPaint = new Paint();
            LinearGradient gradient = new LinearGradient(0, 0, gradientWidth, 0, new int[]{ c1, c2, c1 }, new float[] { 0, 0.67f, 1f }, Shader.TileMode.REPEAT);
            loadingPaint.setShader(gradient);
            float w1 = (float) (dp(30) + Math.random() * dp(20)),
                  w2 = (float) (dp(30) + Math.random() * dp(40));
            container = new FrameLayout(context) {
                Path clipPath = new Path(),
                     tempPath = new Path(),
                     shadePath = new Path(),
                     shapeBorderPath = new Path();
                RectF rect = new RectF();
                @Override
                protected void onDraw(Canvas canvas) {
                    if (loading) {
                        float h = getHeight();

                        clipPath.reset();
                        clipPath.addCircle(dp(27f), dp(24f), dp(17), Path.Direction.CW);
                        rect.set((float) dp(57), (h - dp(8)) / 2f, dp(57) + w1, (h - dp(8)) / 2f + dp(8));
                        clipPath.addRoundRect(rect, dp(4), dp(4), Path.Direction.CW);
                        rect.set((float) dp(57 + 4) + w1, (h - dp(8)) / 2f, dp(57 + 4) + w1 + w2, (h - dp(8)) / 2f + dp(8));
                        clipPath.addRoundRect(rect, dp(4), dp(4), Path.Direction.CW);
                        canvas.clipPath(clipPath);

                        float dx = ((System.currentTimeMillis() - globalStart + position * 150f) / 1500f * gradientWidth) % gradientWidth;

                        shadePath.reset();
                        shadePath.addRect(0, 0, getWidth(), h, Path.Direction.CW);
                        shapeBorderPath.reset();
                        shapeBorderPath.addRect(0, 0, getWidth(), h, Path.Direction.CW);

                        canvas.save();
                        canvas.translate(-dx, 0);
                        shadePath.offset(dx, 0f, tempPath);
                        canvas.drawPath(tempPath, loadingPaint);
                        canvas.restore();
                    }
                    super.onDraw(canvas);
                }
            };

            avatarDrawable = new AvatarDrawable();
            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(34));
            container.addView(imageView, LayoutHelper.createFrame(34, 34, Gravity.LEFT | Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            textView.setTextSize(16);
            textView.setLines(1);
            textView.setSingleLine();
            textView.setEllipsize(TextUtils.TruncateAt.END);
            container.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 57, 0, 41, 0));

            reactionImage = new ReactionImage(context, dp(22));
            container.addView(reactionImage, LayoutHelper.createFrame(22, 22, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 13, 0));

            addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

            loadingPaint.setAntiAlias(true);
            loadingPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        }

        private int position = -1;
        public void set(TLRPC.User user, String reaction, int position) {
            this.position = position;
            this.user = user;
            container.setOnClickListener(e -> {
                if (user != null) {
                    if (onUserClick != null)
                        onUserClick.run(user);
                    ChatMessageScrimPopup.this.dismiss();
                }
            });

            if (user == null) {
                imageView.setVisibility(View.GONE);
            } else {
                imageView.setVisibility(View.VISIBLE);
                avatarDrawable.setInfo(user);
                imageView.setForUserOrChat(user, avatarDrawable);
            }

            if (user == null) {
                textView.setVisibility(View.GONE);
            } else {
                textView.setVisibility(View.VISIBLE);
                textView.setText(ContactsController.formatName(user.first_name, user.last_name));
            }

            if (reaction != null) {
                reactionImage.setVisibility(View.VISIBLE);
                reactionImage.setReaction(reaction);
            } else {
                reactionImage.setVisibility(View.GONE);
            }
            ((MarginLayoutParams) textView.getLayoutParams()).rightMargin = dp(reaction != null ? 41 : 16);
        }

        private Drawable selector = Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0, 0);
        private ValueAnimator loadingAnimator = null;
        private boolean loading = false;
        public void setLoading(boolean loading) {
            this.loading = loading;

            container.setWillNotDraw(!loading);
            if (loading) {
                if (loadingAnimator != null) {
                    loadingAnimator.cancel();
                }
                loadingAnimator = ValueAnimator.ofFloat(0f, 1f);
                loadingAnimator.addUpdateListener(a -> {
                    container.invalidate();
                });
                loadingAnimator.setDuration(Long.MAX_VALUE);
                loadingAnimator.start();
                container.setBackground(null);
            } else {
                if (loadingAnimator != null)
                    loadingAnimator.cancel();
                loadingAnimator = null;
                container.setBackground(selector);
            }
        }

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (shouldShowReactionsButton())
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(dp(280), MeasureSpec.getMode(widthMeasureSpec));
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(maxPossibleHeight, MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.getMode(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private UserCallback onUserClick;
    public int containerHeight = 0;
    public ChatMessageScrimPopup(
        Context context,
        int currentAccount,
        ActionBarPopupWindow.ActionBarPopupWindowLayout buttons,
        MessageObject message,
        TLRPC.Chat currentChat,
        TLRPC.ChatFull chatInfo,
        Runnable onDismiss,
        ReactionCallback onReactionClick,
        UserCallback onUserClick
    ) {
        super(context);

        this.currentAccount = currentAccount;
        this.message = message;
        this.chatInfo = chatInfo;
        this.currentChat = currentChat;
        this.onDismiss = onDismiss;
        this.onUserClick = onUserClick;

        if (shouldShowMessageSeen()) {
            loadMessageSeen();
        }

        this.setOnTouchListener(new OnTouchListener() {
            private Rect containerRect = new Rect(),
                         reactionsListRect = new Rect();
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    boolean containsInContainer = false;
                    if (container != null) {
                        container.getGlobalVisibleRect(containerRect);
                        containerRect.set(containerRect.left, containerRect.top, containerRect.right, containerRect.top + containerHeight);
                        containsInContainer = containerRect.contains((int) event.getX(), (int) event.getY());
                    }
                    boolean containsInReactionsList = false;
                    if (reactionButtonList != null) {
                        reactionButtonList.getGlobalVisibleRect(reactionsListRect);
                        containsInReactionsList = reactionsListRect.contains((int) event.getX(), (int) event.getY());
                    }
                    if (!containsInContainer && !containsInReactionsList) {
                        dismiss();
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    dismiss();
                }
                return false;
            }
        });


        Drawable containerBackground = getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        containerBackground.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        container = new FrameLayout(context) {
            private Path path = new Path();
            private Rect rect = new Rect();
            private RectF r = new RectF();
            @Override
            protected void dispatchDraw(Canvas canvas) {
                int h = containerHeight;
                if (h == 0)
                    h = getHeight();

                rect.set(0, 0, getWidth(), h);
                containerBackground.setBounds(rect);
                containerBackground.draw(canvas);

                r.set(dp(8), dp(8), getWidth() - dp(8), (h - dp(8)));
                path.reset();
                path.addRoundRect(r, dp(6), dp(6), Path.Direction.CW);
                canvas.clipPath(path);

                super.dispatchDraw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(dp(250), MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.getMode(widthMeasureSpec));
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        container.setPadding(dp(8), dp(8), dp(8), dp(8));
//        container.setBackgroundDrawable(containerBackground);

        menu = new MainMenu(context, buttons);
        menu.setOnTopButtonClick(() -> scrollTo(1f));
        menuDim = new FrameLayout(context);
        menuDim.setBackgroundColor(0x33000000);
        menuDim.setAlpha(0f);

        container.addView(menu, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(menuDim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        reactionsMenu = new ReactionsMenu(context);
        reactionsMenu.setOnBackClick(() -> scrollTo(0f));
//        reactionsMenu = new ReactionUserList(context);
//        reactionsMenu.set(null, 10);

        reactionsMenuContainer = new FrameLayout(context);
        reactionsMenuContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        reactionsMenuContainer.addView(reactionsMenu, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(reactionsMenuContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        reactionButtonList = new ChatMessageReactionList(context);
        reactionButtonList.onReactionClick(reaction -> {
            if (onReactionClick != null)
                onReactionClick.run(reaction);
            // TODO(dkaraush): animation!
            dismiss();
        });
        this.addView(reactionButtonList, LayoutHelper.createFrame(100, 61 + 6, Gravity.TOP | Gravity.RIGHT, 0, 0, 0, 0));
        reactionButtonList.show(false, false);
        postDelayed(this::updateReactionsSelect, 100);

        this.addView(container, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 0, 48, 40, 0));
        reactionButtonList.updateBackground();

        this.setClipChildren(false);
        this.setClipToPadding(false);

        updateScroll(0f);
        menu.updateReactionsButton(false);
        reactionsMenu.updateFiltersShow(false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (scrollAnimator == null && scrollT <= 0f) {
            int maxHeight = this.getMeasuredHeight() - dp(48 + 16);
            int menuHeight = Math.min(maxHeight, menu.getMeasuredHeight());
            if (menuHeight != 0)
                containerHeight = Math.min(maxPossibleHeight, menuHeight + dp(16));
        }
    }

    private float scrollT = 0f;
    private void updateScroll(float t) {
        scrollT = t;
        if (t >= 1f) {
            menu.setVisibility(View.INVISIBLE);
            menuDim.setVisibility(View.INVISIBLE);
            reactionsMenuContainer.setVisibility(View.VISIBLE);
        } else if (t <= 0f) {
            menu.setVisibility(View.VISIBLE);
            menuDim.setVisibility(View.INVISIBLE);
            reactionsMenuContainer.setVisibility(View.INVISIBLE);
        } else {
            menu.setVisibility(View.VISIBLE);
            menuDim.setVisibility(View.VISIBLE);
            reactionsMenuContainer.setVisibility(View.VISIBLE);
        }

        float T = Math.min(Math.max(t, 0), 1);
        float width = container.getMeasuredWidth();
        menu.setTranslationX(-width / 2f * T);
        menu.setScaleY(0.9f + 0.1f * (1f - T));
        menu.setScaleX(0.9f + 0.1f * (1f - T));
        menuDim.setTranslationX(-width / 2f * T);
        menuDim.setAlpha(T);
        reactionsMenuContainer.setTranslationX(width * (1f - T));

        updateContainerHeight();
        updateReactionsSelect();

        if (scrollT > 0f) {
            reactionsMenu.init();
        }
    }

    private int maxExistingReactionsMenuHeight = 0;
    private void updateContainerHeight() {
        int maxHeight = Math.min(maxPossibleHeight, this.getMeasuredHeight() - dp(48 + 16));
        int menuHeight = Math.min(maxHeight, menu.getMeasuredHeight());
        int reactionsMenuHeight = Math.min(maxHeight, Math.max(maxExistingReactionsMenuHeight, Math.max(reactionsMenu.estimateHeight(), reactionsMenu.getMeasuredHeight())));
        if (maxExistingReactionsMenuHeight < reactionsMenuHeight)
            maxExistingReactionsMenuHeight = reactionsMenuHeight;
        if (maxHeight > 0 && menuHeight != 0 && reactionsMenuHeight != 0) {
            containerHeight = (int) (menuHeight + (reactionsMenuHeight - menuHeight) * Math.max(Math.min(scrollT, 1), 0)) + dp(16);
            container.invalidate();
        }
    }

    private boolean showReactionsSelectFromTab = true;
    private ValueAnimator scrollAnimator = null;
    private void scrollTo(float t) {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
        scrollAnimator = ValueAnimator.ofFloat(scrollT, t);
        scrollAnimator.addUpdateListener(a -> updateScroll((float) a.getAnimatedValue()));
        scrollAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        scrollAnimator.setDuration((long) (Math.abs(t - scrollT) * 220));
        scrollAnimator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationStart(Animator animator) {}
            @Override public void onAnimationRepeat(Animator animator) {}
            @Override public void onAnimationEnd(Animator animator) { scrollAnimator = null; }
            @Override public void onAnimationCancel(Animator animator) { scrollAnimator = null; }
        });
        scrollAnimator.start();

        showReactionsSelectFromTab = t <= 0f;
        updateReactionsSelect();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean capturing = !reactionsMenu.definitelyScrolling && super.dispatchTouchEvent(ev);
        if (reactionsMenu != null && scrollT > 0f) {
            if (reactionsMenu.handleTouchEvent(ev, capturing))
                return true;
        }
        return capturing;
    }

    public void dismiss() {
        if (onDismiss != null)
            onDismiss.run();
    }

    public interface ReactionCallback {
        void run(TLRPC.TL_availableReaction reaction);
    }

    public class ChatMessageReactionList extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
        private RecyclerListView listView;
        private ChatMessageReactionListAdapter adapter;
        private LinearLayoutManager layoutManager;

        public void updateBackground() {
            Drawable reactionsContainerDrawable = getResources().getDrawable(adapter.getItemCount() > 5 ? R.drawable.popup_reactions : R.drawable.popup_reactions_small).mutate();
            reactionsContainerDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
            setBackground(reactionsContainerDrawable);

            MarginLayoutParams containerLayoutParams = (MarginLayoutParams) container.getLayoutParams();
            if (containerLayoutParams != null)
                containerLayoutParams.rightMargin = dp(adapter.getItemCount() > 5 ? 40 : 32);
        }

        private ReactionCallback onReactionClick;
        public void onReactionClick(ReactionCallback onReactionClick) {
            this.onReactionClick = onReactionClick;
        }

        public ChatMessageReactionList(Context context) {
            super(context);

            listView = new RecyclerListView(context) {
                @Override
                protected void onMeasure(int widthSpec, int heightSpec) {
                    super.onMeasure(widthSpec, heightSpec);

                    boolean allVisible = (
                            layoutManager.findFirstCompletelyVisibleItemPosition() == 0 &&
                                    layoutManager.findLastCompletelyVisibleItemPosition() == adapter.getItemCount() - 1
                    );
                    listView.setOverScrollMode(allVisible ? View.OVER_SCROLL_NEVER : View.OVER_SCROLL_IF_CONTENT_SCROLLS);
                }

                private final Path path = new Path();
                private final RectF first = new RectF(), second = new RectF();
                private Paint leftGradientPaint = null;
                private Paint rightGradientPaint = new Paint();
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    int h = getHeight(), w = getWidth();
                    first.set(0, 0, h, h);
                    second.set(w - h, 0, w, h);

                    path.reset();
                    path.arcTo(first, 90, 180, true);
                    path.arcTo(second, 270, 180, false);
                    canvas.clipPath(path);

                    super.dispatchDraw(canvas);

                    int offset = this.computeHorizontalScrollOffset(),
                        range =  this.computeHorizontalScrollRange(),
                        extent = this.computeHorizontalScrollExtent();
                    if (leftGradientPaint == null) {
                        leftGradientPaint = new Paint();
                        leftGradientPaint.setShader(new LinearGradient(0f, 0f, h / 2f, 0f, Theme.getColor(Theme.key_dialogBackground), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    }
                    leftGradientPaint.setAlpha(
                            (int) (Math.max(0f, Math.min(1f, offset / (float) dp(8))) * 255)
                    );
                    canvas.drawArc(first,   90, 180, true, leftGradientPaint);
                    rightGradientPaint.setShader(new LinearGradient(w - h / 2f, 0f, w, 0f, Color.TRANSPARENT, Theme.getColor(Theme.key_dialogBackground), Shader.TileMode.CLAMP));
                    rightGradientPaint.setAlpha(
                            (int) (Math.max(0f, Math.min(1f, (range - offset - extent) / (float) dp(8))) * 255)
                    );
                    canvas.drawArc(second, 270, 180, true, rightGradientPaint);
                }
            };
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                }

                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    adapter.updateItemsScaling();
                }
            });
            listView.setAdapter(adapter = new ChatMessageReactionListAdapter(context));
            listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            listView.setHorizontalScrollBarEnabled(false);
            listView.setEnabled(true);
            listView.setOnItemClickListener((view, position) -> {
                if (this.onReactionClick != null)
                    this.onReactionClick.run(adapter != null && adapter.reactions != null ? adapter.reactions.get(position) : null);
            });

            this.addView(listView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
            updateBackground();

            setAllReactions();
        }
        public class ChatMessageReactionButton extends FrameLayout {
            private BackupImageView imageView;
            public ChatMessageReactionButton(Context context) {
                super(context);

                imageView = new BackupImageView(context);
                this.addView(imageView, LayoutHelper.createFrame(32, 32, Gravity.NO_GRAVITY, 4, 6, 4, 6));

//                imageView.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 8, 8));
//                imageView.setClickable(true);
            }

            public void setReaction(TLRPC.TL_availableReaction reaction, boolean isFirst, boolean isLast) {
                imageView.setVisibility(reaction == null ? View.GONE : View.VISIBLE);
                if (reaction == null)
                    return;

                TLRPC.Document document = reaction.select_animation;
                imageView.setImage(ImageLocation.getForDocument(document), "80_80", null, null, this);
                imageView.imageReceiver.setAutoRepeat(3);
                tickLoop();

                ((MarginLayoutParams) imageView.getLayoutParams()).leftMargin = dp(isFirst ? 12 : 4);
                ((MarginLayoutParams) imageView.getLayoutParams()).rightMargin = dp(isLast ? 12 : 4);
                invalidate();
            }

            private ViewPropertyAnimator animator;
            public void tickLoop() {
                if (animator != null)
                    animator.cancel();
                animator = animate().setDuration(500).withEndAction(() -> {
                    if (Math.random() <= 0.25) {
                        imageView.imageReceiver.setAutoRepeat(2);
                        imageView.imageReceiver.startAnimation();
                    } else {
                        this.tickLoop();
                    }
                });
                animator.start();
            }

            public void setScale(float scale) {
                imageView.setScaleX(scale);
                imageView.setScaleY(scale);
            }
        }
        public class ChatMessageReactionListAdapter extends RecyclerView.Adapter {
            private Context context;
            public ChatMessageReactionListAdapter(Context context) { this.context = context; }

            private ArrayList<TLRPC.TL_availableReaction> reactions;
            public void setReactions(ArrayList<TLRPC.TL_availableReaction> reactions) {
                this.reactions = reactions;
                this.notifyDataSetChanged();
                updateBackground();
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerListView.Holder(new ChatMessageReactionButton(context));
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ((ChatMessageReactionButton) holder.itemView).setReaction(reactions == null ? null : reactions.get(position), position == 0, position >= getItemCount() - 1);
            }

            @Override
            public int getItemCount() {
                return reactions == null ? 0 : reactions.size();
            }

            private Rect rect1 = new Rect(),
                    rect2 = new Rect();
            private Point point1 = new Point(),
                    point2 = new Point();
            public void updateItemsScaling() {
                if (layoutManager == null)
                    return;
                int first = layoutManager.findFirstVisibleItemPosition(),
                        last = layoutManager.findLastVisibleItemPosition();
                listView.getGlobalVisibleRect(rect1, point1);
                int l = point1.x, r = point1.x + rect1.width();
                for (int position = first - 1; position <= last + 1; position++) {
                    RecyclerView.ViewHolder viewHolder = listView.findViewHolderForLayoutPosition(position);
                    if (viewHolder == null)
                        continue;
                    ChatMessageReactionButton button = (ChatMessageReactionButton) viewHolder.itemView;
                    if (button != null) {
                        button.getGlobalVisibleRect(rect2, point2);
                        float t = Math.min(1f, Math.max(0f, 1f - (float) Math.max(Math.max(0, l - point2.x), Math.max(0, point2.x + button.getMeasuredWidth() - r)) / (float) dp(32))) * 0.5f + 0.5f;
                        button.setScale(t);
                    }
                }
            }
        }

        private ArrayList<String> chatReactions = null;
        public void setChatReactions(ArrayList<String> chatReactions) {
            this.chatReactions = chatReactions;
            updateReactions();
        }

        private ArrayList<TLRPC.TL_availableReaction> allReactions = null;
        public void setAllReactions() {
            this.setAllReactions(MessagesController.getInstance(currentAccount).getAvailableReactions());
        }
        public void setAllReactions(ArrayList<TLRPC.TL_availableReaction> allReactions) {
            this.allReactions = allReactions;
            updateReactions();
        }

        public void updateReactions() {
            ArrayList<TLRPC.TL_availableReaction> reactions;
            if (chatReactions != null && allReactions != null) {
                reactions = new ArrayList<>();
                for (String chatReaction : chatReactions) {
                    TLRPC.TL_availableReaction reaction = null;
                    for (TLRPC.TL_availableReaction availableReaction : allReactions) {
                        if (availableReaction != null && availableReaction.reaction != null && availableReaction.reaction.equals(chatReaction)) {
                            reaction = availableReaction;
                            break;
                        }
                    }
                    if (reaction == null)
                        return;
                    reactions.add(reaction);
                }
            } else if (allReactions != null) {
                reactions = allReactions;
            } else {
                reactions = new ArrayList<>();
            }

            adapter.setReactions(reactions);
            if (showAnimator == null)
                resize(shown ? 1f : 0f);
        }
        private float easeOutBack_G = 0.7f;
        private float easeOutBack(float x) {
            return 1f + (1f + easeOutBack_G) * (float) Math.pow(x - 1f, 3) + easeOutBack_G * (float) Math.pow(x - 1f, 2f);
        }
        private float easeInOutExpo(float x) {
            return x <= 0f
                    ? 0
                    : x >= 1f
                    ? 1
                    : x < 0.5 ? (float) Math.pow(2, 20 * x - 10) / 2f
                    : (2 - (float) Math.pow(2, -20 * x + 10)) / 2f;
        }
        private float easeOutQuad(float x) {
            return 1 - (1 - x) * (1 - x);
        }

        private boolean shown = false;
        private ValueAnimator showAnimator;
        public void show(boolean value, boolean animated) {
            if (!animated) {
                if (showAnimator != null) {
                    showAnimator.cancel();
                    showAnimator = null;
                }
                this.setAlpha(value ? 1f : 0f);
                resize(value ? 0f : 1f);
            } else if (shown != value) {
                float from = showAnimator != null ? (float) showAnimator.getAnimatedValue() : 0f;
                if (showAnimator != null)
                    showAnimator.cancel();
                showAnimator = ValueAnimator.ofFloat(from, 1f);
                showAnimator.addUpdateListener(a -> {
                    float t = ((float) a.getAnimatedValue());
                    t = value ? t : 1f - t;
                    t = easeOutBack(t);
                    this.setAlpha(t);
                    resize(t);
                    forceLayout();
                });
                showAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        showAnimator = null;
                        resize(value ? 1f : 0f);
                    }
                });
                showAnimator.setDuration((int) (Math.abs(from - 1f) * 200));
                showAnimator.start();
            }
            shown = value;
        }

        private int computeRange() {
            return this.computeRange(adapter.getItemCount());
        }
        private int computeRange(int itemCount) {
            return dp(8 + (4 + 32 + 4) * itemCount + 8 + 8);
        }

        private void resize(float t) {
            int minWidth = computeRange(1) + dp(8);
            int maxWidth = Math.max(minWidth, Math.min(computeRange(), dp(250)));
            ViewGroup.LayoutParams layoutParams = getLayoutParams();
            if (layoutParams != null) {
                layoutParams.width = (int) (minWidth + (float) (maxWidth - minWidth) * t);
                setLayoutParams(layoutParams);
                invalidate();
                adapter.updateItemsScaling();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.availableReactionsUpdate);
        }
        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.availableReactionsUpdate);
        }
        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.availableReactionsUpdate) {
                ArrayList<TLRPC.TL_availableReaction> reactions = (ArrayList) args[0];
                setAllReactions(reactions);
            }
        }
    }

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull chatInfo;
    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        this.chatInfo = chatInfo;
        reactionButtonList.setChatReactions(this.chatInfo != null ? this.chatInfo.available_reactions : null);
        updateReactionsSelect();
        menu.updateReactionsButton(true);
        reactionsMenu.updateFiltersShow(true);
    }

    public void setMessage(MessageObject message) {
        this.message = message;
        menu.updateReactionsButton(true);
        reactionsMenu.updateFiltersShow(true);
        updateReactionsSelect();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            // TODO(dkaraush): dismiss
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        this.loaders.clear();
        this.messageSeenCallbacks.clear();
        super.onDetachedFromWindow();
    }

    public boolean shouldShowMessageSeen() {
        return (
            currentChat != null &&
            message != null &&
            message.isOutOwner() &&
            message.isSent() &&
            !message.isEditing() &&
            !message.isSending() &&
            !message.isSendError() &&
            !message.isContentUnread() &&
            !message.isUnread() &&
            !DialogObject.isEncryptedDialog(message.getDialogId()) &&
            (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - message.messageOwner.date < 7 * 86400) &&
            (ChatObject.isMegagroup(currentChat) || !ChatObject.isChannel(currentChat)) &&
            chatInfo != null &&
            chatInfo.participants_count < 50 &&
            !(message.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest)
        );
    }
    public boolean shouldShowTopButton() {
        return shouldShowReactionsButton() || shouldShowMessageSeen();
    }
    public boolean shouldShowReactionsButton() {
        return (message != null && reactionsCount > 0 && (!ChatObject.isChannel(currentChat) || currentChat.megagroup) && !DialogObject.isEncryptedDialog(message.getDialogId()));
    }
    public boolean shouldShowReactionsSelect() {
        return ((message != null && DialogObject.isUserDialog(message.getDialogId()) && UserConfig.getInstance(currentAccount).getClientUserId() != message.getDialogId()) || (chatInfo != null && chatInfo.available_reactions != null && chatInfo.available_reactions.size() > 0)) && !DialogObject.isEncryptedDialog(message.getDialogId());
    }

    public void updateReactionsSelect() {
        reactionButtonList.show(showReactionsSelectFromTab && shouldShowReactionsSelect(), true);
    }

    private boolean messageSeenLoading = false;
    private ArrayList<Long> messageSeenPeerIds = new ArrayList<>();
    private ArrayList<TLRPC.User> messageSeenUsers = new ArrayList<>();
    private int messageSeenCallbacksIndex = 0;
    private HashMap<Integer, Runnable> messageSeenCallbacks = new HashMap<Integer, Runnable>();
    public void loadMessageSeen() {
        if (messageSeenLoading || message == null || currentChat == null)
            return;

        long fromId = 0;
        if (message.messageOwner.from_id != null) {
            fromId = message.messageOwner.from_id.user_id;
        }
        long finalFromId = fromId;

        messageSeenLoading = true;
        TLRPC.TL_messages_getMessageReadParticipants req = new TLRPC.TL_messages_getMessageReadParticipants();
        req.msg_id = message.getId();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(message.getDialogId());
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                ArrayList<Long> unknownUsers = new ArrayList<>();
                HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
                ArrayList<Long> allPeers = new ArrayList<>();
                for (int i = 0, n = vector.objects.size(); i < n; i++) {
                    Object object = vector.objects.get(i);
                    if (object instanceof Long) {
                        Long peerId = (Long) object;
                        if (finalFromId == peerId) {
                            continue;
                        }
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                        allPeers.add(peerId);
                        if (true || user == null) {
                            unknownUsers.add(peerId);
                        } else {
                            usersLocal.put(peerId, user);
                        }
                    }
                }

                if (unknownUsers.isEmpty()) {
                    for (int i = 0; i < allPeers.size(); i++) {
                        messageSeenPeerIds.add(allPeers.get(i));
                        messageSeenUsers.add(usersLocal.get(allPeers.get(i)));
                    }
                    messageSeenLoading = false;
                    menu.updateReactionsButton(true);
                    reactionsMenu.updateFiltersShow(true);
                    AndroidUtilities.runOnUIThread(() -> {
                        for (Map.Entry<Integer, Runnable> pair : messageSeenCallbacks.entrySet()) {
                            if (pair.getValue() != null)
                                pair.getValue().run();
                            messageSeenCallbacks.remove(pair.getKey());
                        }
                        messageSeenCallbacks.clear();
                    });
                } else {
                    if (ChatObject.isChannel(currentChat)) {
                        TLRPC.TL_channels_getParticipants usersReq = new TLRPC.TL_channels_getParticipants();
                        usersReq.limit = 50;
                        usersReq.offset = 0;
                        usersReq.filter = new TLRPC.TL_channelParticipantsRecent();
                        usersReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(currentChat.id);
                        ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response1 != null) {
                                TLRPC.TL_channels_channelParticipants users = (TLRPC.TL_channels_channelParticipants) response1;
                                for (int i = 0; i < users.users.size(); i++) {
                                    TLRPC.User user = users.users.get(i);
                                    MessagesController.getInstance(currentAccount).putUser(user, false);
                                    usersLocal.put(user.id, user);
                                }
                                for (int i = 0; i < allPeers.size(); i++) {
                                    messageSeenPeerIds.add(allPeers.get(i));
                                    messageSeenUsers.add(usersLocal.get(allPeers.get(i)));
                                }
                            }
                            messageSeenLoading = false;
                            menu.updateReactionsButton(true);
                            reactionsMenu.updateFiltersShow(true);
                            AndroidUtilities.runOnUIThread(() -> {
                                Set<Map.Entry<Integer, Runnable>> entries = messageSeenCallbacks.entrySet();
                                for (Map.Entry<Integer, Runnable> pair : entries) {
                                    if (pair.getValue() != null)
                                        pair.getValue().run();
                                }
                                messageSeenCallbacks.clear();
                            });
                        }));
                    } else {
                        TLRPC.TL_messages_getFullChat usersReq = new TLRPC.TL_messages_getFullChat();
                        usersReq.chat_id = currentChat.id;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response1 != null) {
                                TLRPC.TL_messages_chatFull chatFull = (TLRPC.TL_messages_chatFull) response1;
                                for (int i = 0; i < chatFull.users.size(); i++) {
                                    TLRPC.User user = chatFull.users.get(i);
                                    MessagesController.getInstance(currentAccount).putUser(user, false);
                                    usersLocal.put(user.id, user);
                                }
                                for (int i = 0; i < allPeers.size(); i++) {
                                    messageSeenPeerIds.add(allPeers.get(i));
                                    messageSeenUsers.add(usersLocal.get(allPeers.get(i)));
                                }
                            }
                            messageSeenLoading = false;
                            menu.updateReactionsButton(true);
                            reactionsMenu.updateFiltersShow(true);
                            AndroidUtilities.runOnUIThread(() -> {
                                Set<Map.Entry<Integer, Runnable>> entries = messageSeenCallbacks.entrySet();
                                for (Map.Entry<Integer, Runnable> pair : entries) {
                                    if (pair.getValue() != null)
                                        pair.getValue().run();
                                }
                                messageSeenCallbacks.clear();
                            });
                        }));
                    }
                }
            } else {
                messageSeenLoading = false;
                menu.updateReactionsButton(true);
                reactionsMenu.updateFiltersShow(true);
                AndroidUtilities.runOnUIThread(() -> {
                    Set<Map.Entry<Integer, Runnable>> entries = messageSeenCallbacks.entrySet();
                    for (Map.Entry<Integer, Runnable> pair : entries) {
                        if (pair.getValue() != null)
                            pair.getValue().run();
                    }
                    messageSeenCallbacks.clear();
                });
            }
        });
    }
}
