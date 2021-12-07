package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatMessageScrimPopup;
import org.telegram.ui.Components.LayoutHelper;

import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ChatMessageCellReactions extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int OUTSIDE = 0;
    public static final int INSIDE = 1;
    public static final int INSIDE_OWNER = 2;

    private Paint chipBackground = new Paint();
    private Paint chipSelector = new Paint();
    private Paint chipAvatarLoading = new Paint();
    private Paint chipAvatarBorder = new Paint();
    private TextPaint countPaint = new TextPaint();

    private int currentAccount;
    public ChatMessageCellReactions(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;

        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setBackgroundColor(0x0000000);

        chipBackground.setFlags(Paint.ANTI_ALIAS_FLAG);
        chipBackground.setAntiAlias(true);
        countPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        countPaint.setTextSize(dp(14));
        countPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        countPaint.setAntiAlias(true);
        chipSelector.setStyle(Paint.Style.STROKE);
        chipSelector.setStrokeWidth(CHIP_BORDER());
        chipSelector.setFlags(Paint.ANTI_ALIAS_FLAG);
        chipSelector.setAntiAlias(true);
        chipAvatarLoading.setFlags(Paint.ANTI_ALIAS_FLAG);
        chipAvatarBorder.setFlags(Paint.ANTI_ALIAS_FLAG);
        chipAvatarBorder.setStyle(Paint.Style.STROKE);
        chipAvatarBorder.setStrokeWidth(CHIP_AVATAR_BORDER());
        chipAvatarBorder.setAntiAlias(true);

        setLook(OUTSIDE);
    }

    private Runnable invalidate;
    public void setInvalidate(Runnable newInvalidate) {
        invalidate = newInvalidate;
    }

    public boolean rtl = false;

    private String[] chipBackgroundColorKeys = new String[] { Theme.key_chat_outsideReactionBackground, Theme.key_chat_insideReactionBackground, Theme.key_chat_insideOwnerReactionBackground };
    private String[] countPaintColorKeys = new String[] { Theme.key_chat_outsideReactionCount, Theme.key_chat_insideReactionCount, Theme.key_chat_insideOwnerReactionCount };
    private String[] chipSelectorColorKeys = new String[] { Theme.key_chat_outsideReactionBorder, Theme.key_chat_insideReactionBorder, Theme.key_chat_insideOwnerReactionBorder };
    private String[] chipAvatarLoadingColorKeys = new String[] { Theme.key_chat_outsideReactionAvatarBackground, Theme.key_chat_insideReactionAvatarBackground, Theme.key_chat_insideOwnerReactionAvatarBackground };

    public int look = OUTSIDE;
    public void setLook(int look) {
        this.look = look;

        countPaint.setColor(Theme.getColor(countPaintColorKeys[look]));
        chipBackground.setColor(Theme.getColor(chipBackgroundColorKeys[look]));
        chipSelector.setColor(Theme.getColor(chipSelectorColorKeys[look]));
        chipAvatarLoading.setColor(Theme.getColor(chipAvatarLoadingColorKeys[look]));

        if (Theme.isCurrentThemeDark() || Theme.isCurrentThemeNight())
            chipAvatarBorder.setColor((new int[] { 0x00000000, 0xff253749, 0xff426A84 })[look]); // TODO(dkaraush): color!
        else
            chipAvatarBorder.setColor((new int[] { 0x00000000, 0xffebf4fa, 0xffddf4cd })[look]); // TODO(dkaraush): color!

        invalidate();
    }

    public boolean findReactionPos(String reaction, RectF outRect) {
        for (Pair<ReactionChip, ReactionChip> pair : state) {
            if (pair.first != null && pair.first.reaction != null && pair.first.reaction.equals(reaction)) {
                int paddingVertical = (CHIP_HEIGHT() - CHIP_REACTION_SIZE()) / 2;
                outRect.set(
                    pair.first.x + CHIP_PADDING_HORIZONTAL(),
                    pair.first.y + paddingVertical,
                   pair.first.x + CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE(),
                 pair.first.y + paddingVertical + CHIP_REACTION_SIZE()
                );
                return true;
            }
        }
        return false;
    }

    private MessagesStorage.StringCallback onReactionClick = null;
    public void setOnReactionClick(MessagesStorage.StringCallback onReactionClick) {
        this.onReactionClick = onReactionClick;
    }
    private MessagesStorage.StringCallback onReactionHold = null;
    public void setOnReactionHold(MessagesStorage.StringCallback onReactionHold) {
        this.onReactionHold = onReactionHold;
    }

    private boolean active = false;
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active)
            return;
        active = true;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.availableReactionsUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.fileLoaded);
    }
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!active)
            return;
        active = false;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.availableReactionsUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.fileLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.availableReactionsUpdate) {
            for (Pair<ReactionChip, ReactionChip> pair : state) {
                if (pair.first != null && pair.first.reaction != null)
                    makeReactionImage(pair.first.reaction);
                if (pair.second != null && pair.second.reaction != null)
                    makeReactionImage(pair.second.reaction);
            }
            invalidateForNextMs(350);
        } else if (id == NotificationCenter.fileLoaded) {
            invalidateForNextMs(500);
        }
    }

    private int CHIP_HEIGHT() { return dp(26); }
    private int CHIP_BORDER() { return dp(4f / 3f); }
    private int CHIP_PADDING_HORIZONTAL() { return dp(7); }
    private int CHIP_INNER_MARGIN() { return dp(11f / 3f); }
    private int CHIP_REACTION_SIZE() { return dp(20); }
    private int CHIP_AVATAR_SIZE() { return dp(21); }
    private int CHIP_AVATAR_INDENT() { return dp(13); }
    private int CHIP_AVATAR_BORDER() { return dp(1.5f); }
    private int CHIP_MARGIN() { return dp(6); }
    private int CHIP_TEXT_MOVE() { return dp(8); }

    private int lastLineIndent = 0;

    public int updateReactions(TLRPC.TL_messageReactions messageReactions, boolean animated) {
        return updateReactions(messageReactions, animated, 0, lastLineIndent);
    }
    public int updateReactions(TLRPC.TL_messageReactions messageReactions, boolean animated, int width, int lastLineIndent) {
        this.lastLineIndent = lastLineIndent;

        if (animated) {
            applyUpdateAnimatedScheduled(messageReactions, width);
        } else {
            if (getWidth() == 0 && prevWidth == 0 && width == 0) {
                waitForLayout = true;
                scheduledUpdate = messageReactions;
            } else {
                setState(messageReactions, null, width == 0 ? (prevWidth != 0 ? prevWidth : getWidth()) : width, false);
            }
        }
        return height;
    }

    private int prevWidth = 0;
    private boolean waitForLayout = false;
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        if (width > 0) {
            if (waitForLayout && scheduledUpdate != null) {
                state = layout(scheduledUpdate, null, width);
                scheduledUpdate = null;
            } else {
//                updateWidth(width);
            }
            waitForLayout = false;
        }

        super.onLayout(changed, left, top, right, top + height);
    }

    public int updateWidth(int width) {
//        if (prevWidth == width)
//            return height;
        state = relayout(state, width);
//        lastUpdate = System.currentTimeMillis();
        invalidate();
//        postDelayed(() -> invalidateForNextMs(updateAnimationDuration), 16);
        prevWidth = width;
        return height;
    }

    private TLRPC.TL_messageReactions scheduledUpdate = null;
    private void applyUpdateAnimatedScheduled(TLRPC.TL_messageReactions messageReactions) {
        applyUpdateAnimatedScheduled(messageReactions, 0);
    }
    private void applyUpdateAnimatedScheduled(TLRPC.TL_messageReactions messageReactions, int width) {
        if (getWidth() == 0 && width == 0) {
            waitForLayout = true;
            scheduledUpdate = messageReactions;
        } else if (System.currentTimeMillis() - lastUpdate < updateAnimationDuration) {
            boolean wasntPosted = scheduledUpdate == null;
            scheduledUpdate = messageReactions;
            if (wasntPosted) {
                postDelayed(() -> {
                    setState(scheduledUpdate, state, width == 0 ? getWidth() : width, true);
//                    applyUpdateAnimated(scheduledUpdate, width);
                    scheduledUpdate = null;
                }, System.currentTimeMillis() - lastUpdate);
            }
        } else {
            setState(messageReactions, state, width == 0 ? getWidth() : width, true);
//            applyUpdateAnimated(messageReactions, width);
        }
    }
    private void applyUpdateAnimated(TLRPC.TL_messageReactions messageReactions, int width) {
        state = layout(messageReactions, state, width == 0 ? getWidth() : width);
        lastUpdate = System.currentTimeMillis();
        invalidateForNextMs(updateAnimationDuration);
    }

    private ValueAnimator a;
    private void invalidateForNextMs(long ms) {
        post(() -> {
            long wasLeft = 0;
            if (a != null) {
                wasLeft = (long) (a.getDuration() * (float) a.getAnimatedValue());
                a.cancel();
            }
            a = ValueAnimator.ofFloat(0f, 1f);
            a.addUpdateListener(a -> {
                invalidate();
                if (invalidate != null)
                    invalidate.run();
            });
            a.setDuration(Math.max(ms, wasLeft));
            a.start();
        });
    }

    private HashMap<String, ImageReceiver> reactionImages = new HashMap<>();
    private void makeReactionImage(String reactionString) {
        if (reactionImages.containsKey(reactionString))
            return;
        ArrayList<TLRPC.TL_availableReaction> allReactions = MessagesController.getInstance(currentAccount).getAvailableReactions();
        if (allReactions == null)
            return;
        TLRPC.TL_availableReaction reaction = null;
        for (TLRPC.TL_availableReaction r : allReactions) {
            if (r != null && r.reaction != null && r.reaction.equals(reactionString)) {
                reaction = r;
                break;
            }
        }
        if (reaction == null)
            return;

        TLRPC.Document reactionDocument = reaction.static_icon;
        ImageReceiver ig = new ImageReceiver();
        ig.setImage(ImageLocation.getForDocument(reactionDocument), null, null, "webp", null, CHIP_REACTION_SIZE());
        ig.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override
            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
                invalidateForNextMs(300);
            }
        });

        reactionImages.put(reactionString, ig);
    }
    private HashMap<Long, ImageReceiver> avatarImages = new HashMap<>();
    private void makeAvatarImage(long userId) {
        if (avatarImages.containsKey(userId))
            return;
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
        if (user == null)
            return;

        AvatarDrawable ad = new AvatarDrawable();
        ad.setInfo(user);
        ImageReceiver ig = new ImageReceiver();
        ig.setForUserOrChat(user, ad);
        ig.setRoundRadius(CHIP_AVATAR_SIZE() / 2);
        ig.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override
            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
                invalidateForNextMs(300);
            }
        });

        avatarImages.put(userId, ig);
    }

    class ReactionChip {
        String reaction;
        int x, y, width;
        ArrayList<Long> userIds = null;
        String count;
        boolean chosen;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReactionChip that = (ReactionChip) o;
            return x == that.x && y == that.y && width == that.width && chosen == that.chosen && Objects.equals(reaction, that.reaction) && Objects.equals(userIds, that.userIds) && Objects.equals(count, that.count);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reaction, x, y, width, userIds, count, chosen);
        }
    }
    public int height = 0;
    private ArrayList<Pair<ReactionChip, ReactionChip>> state = new ArrayList<>();
    private long lastUpdate = 0;
    private long updateAnimationDuration = 200;

    public int totalWidth() {
        int l = -1, r = 0;
        for (Pair<ReactionChip, ReactionChip> pair : state) {
            ReactionChip newChip = pair.first;
            if (newChip == null)
                continue;
            if (l == -1)
                l = newChip.x;
            l = Math.min(l, newChip.x);
            r = Math.max(r, newChip.x + newChip.width);
        }
        return (l == -1 ? 0 : r - l) + CHIP_BORDER() * 4;
    }

    private String touchStartedChip = null;
    private long lastTouch = 0;
    private Rect r = new Rect();
    public boolean checkTouchEvent(MotionEvent event) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        int ex = (int) (event.getX() - getLeft()),
            ey = (int) (event.getY() - getTop());

        ReactionChip chip = null;
        int H = CHIP_MARGIN() / 2;
        float t = easeInOutQuad(Math.max(0, Math.min(1, (System.currentTimeMillis() - lastUpdate) / (float) updateAnimationDuration)));
        for (Pair<ReactionChip, ReactionChip> pair : state) {
            ReactionChip newChip = pair.first;
            ReactionChip oldChip = pair.second;
            if (newChip == null)
                continue;

            int x = lerp(oldChip == null ? null : oldChip.x, newChip == null ? null : newChip.x, t),
                y = lerp(oldChip == null ? null : oldChip.y, newChip == null ? null : newChip.y, t),
                width = lerp(oldChip == null ? null : oldChip.width, newChip == null ? null : newChip.width, t);

            if (ex >= x - H && ex <= x + width + H && ey >= y - H && ey <= y + CHIP_HEIGHT() + H) {
                chip = newChip == null ? oldChip : newChip;
            }
        }

        if (chip != null && chip.reaction != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                lastTouch = System.currentTimeMillis();
                touchStartedChip = chip.reaction;
                postDelayed(() -> {
                    if (System.currentTimeMillis() - lastTouch >= ViewConfiguration.getLongPressTimeout() && touchStartedChip != null) {
                        if (onReactionHold != null)
                            onReactionHold.run(touchStartedChip);
                    }
                }, ViewConfiguration.getLongPressTimeout());
            }

            if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
                if (System.currentTimeMillis() - lastTouch < ViewConfiguration.getLongPressTimeout() && onReactionClick != null) {
                    onReactionClick.run(chip.reaction);
                }

                lastTouch = System.currentTimeMillis();
                touchStartedChip = null;
            }
            return true;
        } else {
            touchStartedChip = null;
            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_UP) {
                lastTouch = System.currentTimeMillis();
            }
        }


        return super.onTouchEvent(event);
    }

    private boolean setState(TLRPC.TL_messageReactions reactions, ArrayList<Pair<ReactionChip, ReactionChip>> prevState, int width, boolean animated) {
        int wasHeight = height;
        ArrayList<Pair<ReactionChip, ReactionChip>> newState = layout(reactions, prevState, width);

        boolean changed = false;
        if (wasHeight != height || prevState == null || newState.size() != prevState.size())
            changed = true;
        if (!changed) {
            boolean hasDifferences = false;
            for (Pair<ReactionChip, ReactionChip> p : newState) {
                if (!((p.first == null && p.second == null) || (p.first != null && p.first.equals(p.second)))) {
                    hasDifferences = true;
                    break;
                }
            }
            if (hasDifferences)
                changed = true;
        }

        if (changed) {
            state = newState;
            if (animated) {
                lastUpdate = System.currentTimeMillis();
            } else {
                lastUpdate = 0;
            }
            invalidateForNextMs(updateAnimationDuration);
        }
        return changed;
    }

    private Rect lastLineIndentRect = new Rect(),
                 chipRect = new Rect();
    private ArrayList<Pair<ReactionChip, ReactionChip>> layout(TLRPC.TL_messageReactions reactions, ArrayList<Pair<ReactionChip, ReactionChip>> prevState) {
        return this.layout(reactions, prevState, getWidth());
    }
    private ArrayList<Pair<ReactionChip, ReactionChip>> layout(TLRPC.TL_messageReactions reactions, ArrayList<Pair<ReactionChip, ReactionChip>> prevState, int width) {
        ArrayList<Pair<ReactionChip, ReactionChip>> newState = new ArrayList<>();
        ArrayList<Long> userIds = new ArrayList<>();
        int x = rtl ? width - CHIP_BORDER() : 0, y = CHIP_BORDER() + (look == OUTSIDE ? 0 : CHIP_MARGIN());
        if (prevState != null) {
            for (Pair<ReactionChip, ReactionChip> pair : prevState) {
                ReactionChip oldChip = pair.first;
                if (oldChip == null || oldChip.reaction == null)
                    continue;

                TLRPC.TL_reactionCount reactionCount = null;
                if (reactions != null && reactions.results != null) {
                    for (TLRPC.TL_reactionCount rc : reactions.results) {
                        if (rc != null && rc.reaction != null && rc.reaction.equals(oldChip.reaction)) {
                            reactionCount = rc;
                            break;
                        }
                    }
                }

                if (reactionCount == null) {
                    newState.add(new Pair<>(null, oldChip));
                } else {

                    ReactionChip newChip = new ReactionChip();
                    newChip.reaction = reactionCount.reaction;
                    makeReactionImage(newChip.reaction);

                    userIds.clear();
                    if (reactions.recent_reactons != null && reactionCount.count <= 3) {
                        for (TLRPC.TL_messageUserReaction userReaction : reactions.recent_reactons) {
                            if (userReaction != null && userReaction.reaction != null && userReaction.reaction.equals(newChip.reaction)) {
                                userIds.add(userReaction.user_id);
                            }
                        }
                    }
                    newChip.userIds = (ArrayList) userIds.clone();
                    if (newChip.userIds.size() == 0 || reactionCount.count != newChip.userIds.size()) {
                        newChip.userIds.clear();
                        newChip.count = countToString(reactionCount.count);
                        newChip.width = CHIP_PADDING_HORIZONTAL() * 2 + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + (int) countPaint.measureText(newChip.count);
                    } else {
                        newChip.count = "";
                        for (long userId : userIds)
                            makeAvatarImage(userId);
                        newChip.width = CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + newChip.userIds.size() * CHIP_AVATAR_INDENT() + (newChip.userIds.size() > 0 ? CHIP_AVATAR_SIZE() - CHIP_AVATAR_INDENT() : 0) + (CHIP_HEIGHT() - CHIP_AVATAR_SIZE()) / 2;
                    }

                    if (rtl) {
                        if (x < width - CHIP_BORDER() && x - newChip.width - CHIP_MARGIN() < CHIP_BORDER()) {
                            x = width - CHIP_BORDER();
                            y += CHIP_HEIGHT() + CHIP_MARGIN();
                        }
                        x -= newChip.width + CHIP_MARGIN();
                        newChip.x = x;
                        newChip.y = y;
                    } else {
                        if (x > 0 && x + newChip.width + CHIP_MARGIN() > width - 2 * CHIP_BORDER()) {
                            x = 0;
                            y += CHIP_HEIGHT() + CHIP_MARGIN();
                        }
                        newChip.x = x + CHIP_BORDER();
                        newChip.y = y;
                        x += newChip.width + CHIP_MARGIN();
                    }

                    newChip.chosen = reactionCount.chosen;

                    newState.add(new Pair<>(newChip, oldChip));
                }
            }
        }

        if (reactions != null && reactions.results != null) {
            for (TLRPC.TL_reactionCount reactionCount : reactions.results) {
                if (reactionCount == null || reactionCount.reaction == null)
                    continue;

                boolean alreadyAdded = false;
                for (Pair<ReactionChip, ReactionChip> pair : newState) {
                    if (pair.first != null && pair.first.reaction != null && pair.first.reaction.equals(reactionCount.reaction)) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (alreadyAdded)
                    continue;

                ReactionChip newChip = new ReactionChip();
                newChip.reaction = reactionCount.reaction;
                makeReactionImage(newChip.reaction);

                userIds.clear();
                if (reactions.recent_reactons != null && reactionCount.count <= 3) {
                    for (TLRPC.TL_messageUserReaction userReaction : reactions.recent_reactons) {
                        if (userReaction != null && userReaction.reaction != null && userReaction.reaction.equals(newChip.reaction)) {
                            userIds.add(userReaction.user_id);
                        }
                    }
                }

                newChip.userIds = (ArrayList) userIds.clone();
                if (newChip.userIds.size() == 0 || reactionCount.count != newChip.userIds.size()) {
                    newChip.userIds.clear();
                    newChip.count = countToString(reactionCount.count);
                    newChip.width = CHIP_PADDING_HORIZONTAL() * 2 + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + (int) countPaint.measureText(newChip.count);
                } else {
                    newChip.count = "";
                    for (long userId : userIds)
                        makeAvatarImage(userId);
                    newChip.width = CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + newChip.userIds.size() * CHIP_AVATAR_INDENT() + (newChip.userIds.size() > 0 ? CHIP_AVATAR_SIZE() - CHIP_AVATAR_INDENT() : 0) + (CHIP_HEIGHT() - CHIP_AVATAR_SIZE()) / 2;
                }

                if (rtl) {
                    if (x < width - CHIP_BORDER() && x - newChip.width - CHIP_MARGIN() < CHIP_BORDER()) {
                        x = width - CHIP_BORDER();
                        y += CHIP_HEIGHT() + CHIP_MARGIN();
                    }
                    x -= newChip.width + CHIP_MARGIN();
                    newChip.x = x;
                    newChip.y = y;
                } else {
                    if (x > 0 && x + newChip.width + CHIP_MARGIN() > width - 2 * CHIP_BORDER()) {
                        x = 0;
                        y += CHIP_HEIGHT() + CHIP_MARGIN();
                    }
                    newChip.x = x + CHIP_BORDER();
                    newChip.y = y;
                    x += newChip.width + CHIP_MARGIN();
                }

                newChip.chosen = reactionCount.chosen;

                newState.add(new Pair<>(newChip, null));
            }
        }
        height = y + CHIP_HEIGHT() + CHIP_BORDER();
        if (look != OUTSIDE && lastLineIndent > 0) {
            boolean touchesLastLineIndent = false;
            lastLineIndentRect.set(width - (lastLineIndent + dp(7)), height - dp(12), width, height);
            for (Pair<ReactionChip, ReactionChip> pair : newState) {
                ReactionChip newChip = pair.first;
                if (newChip == null) continue;
                chipRect.set(newChip.x, newChip.y, newChip.x + newChip.width, newChip.y + CHIP_HEIGHT());
                if (lastLineIndentRect.intersect(chipRect)) {
                    touchesLastLineIndent = true;
                    break;
                }
            }

            if (touchesLastLineIndent)
                height += dp(14);
        }
        return newState;
    }
    private ArrayList<Pair<ReactionChip, ReactionChip>> relayout(ArrayList<Pair<ReactionChip, ReactionChip>> prevState, int width) {
        ArrayList<Pair<ReactionChip, ReactionChip>> newState = new ArrayList<>();
        int x = rtl ? width - CHIP_BORDER() : 0, y = CHIP_BORDER() + (look == OUTSIDE ? 0 : CHIP_MARGIN());
        if (prevState != null) {
            for (Pair<ReactionChip, ReactionChip> pair : prevState) {
                ReactionChip oldChip = pair.first;
                if (oldChip == null || oldChip.reaction == null)
                    continue;

                ReactionChip newChip = new ReactionChip();
                newChip.reaction = oldChip.reaction;
                newChip.chosen = oldChip.chosen;
                makeReactionImage(newChip.reaction);

                if (oldChip.userIds == null || oldChip.userIds.size() == 0) {
                    newChip.userIds = new ArrayList<>();
                    newChip.count = oldChip.count;
                    newChip.width = CHIP_PADDING_HORIZONTAL() * 2 + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + (int) countPaint.measureText(newChip.count);
                } else {
                    newChip.userIds = (ArrayList) oldChip.userIds.clone();
                    newChip.count = "";
                    for (long userId : userIds)
                        makeAvatarImage(userId);
                    newChip.width = CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + newChip.userIds.size() * CHIP_AVATAR_INDENT() + (newChip.userIds.size() > 0 ? CHIP_AVATAR_SIZE() - CHIP_AVATAR_INDENT() : 0) + (CHIP_HEIGHT() - CHIP_AVATAR_SIZE()) / 2;
                }

                if (rtl) {
                    if (x < width - CHIP_BORDER() && x - newChip.width - CHIP_MARGIN() < CHIP_BORDER()) {
                        x = width - CHIP_BORDER();
                        y += CHIP_HEIGHT() + CHIP_MARGIN();
                    }
                    x -= newChip.width + CHIP_MARGIN();
                    newChip.x = x;
                    newChip.y = y;
                } else {
                    if (x > 0 && x + newChip.width + CHIP_MARGIN() > width - 2 * CHIP_BORDER()) {
                        x = 0;
                        y += CHIP_HEIGHT() + CHIP_MARGIN();
                    }
                    newChip.x = x + CHIP_BORDER();
                    newChip.y = y;
                    x += newChip.width + CHIP_MARGIN();
                }

                newState.add(new Pair<>(newChip, oldChip));
            }
        }
        height = y + CHIP_HEIGHT() + CHIP_BORDER();
        if (look != OUTSIDE && lastLineIndent > 0) {
            boolean touchesLastLineIndent = false;
            lastLineIndentRect.set(width - (lastLineIndent + dp(4)), height - dp(12), width, height);
            for (Pair<ReactionChip, ReactionChip> pair : newState) {
                ReactionChip newChip = pair.first;
                if (newChip == null) continue;
                chipRect.set(newChip.x, newChip.y, newChip.x + newChip.width, newChip.y + CHIP_HEIGHT());
                if (lastLineIndentRect.intersect(chipRect)) {
                    touchesLastLineIndent = true;
                    break;
                }
            }

            if (touchesLastLineIndent)
                height += dp(14);
        }
        return newState;
    }
    private float easeInOutQuad(float x) {
        return x < 0.5 ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
    }

    private Path path = new Path();
    private RectF r1 = new RectF(),
                  r2 = new RectF();
    private Rect tRect = new Rect();
    private Set<Long> allUserIds = new HashSet<>();
    private ArrayList<Long> userIds = new ArrayList<>();
    private ArrayList<Long> userIds2 = new ArrayList<>();
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int D = CHIP_HEIGHT(), R = D / 2;
        int paddingVertical = (CHIP_HEIGHT() - CHIP_REACTION_SIZE()) / 2;

        float t = easeInOutQuad(Math.max(0, Math.min(1, (System.currentTimeMillis() - lastUpdate) / (float) updateAnimationDuration)));
        for (Pair<ReactionChip, ReactionChip> pair : state) {
            ReactionChip newChip = pair.first;
            ReactionChip oldChip = pair.second;

            if (newChip == null && oldChip == null)
                continue; // wtf?

            float alpha = oldChip == null ? t : (newChip == null ? 1f - t : 1f);
            int x = lerp(oldChip == null ? null : oldChip.x, newChip == null ? null : newChip.x, t),
                y = lerp(oldChip == null ? null : oldChip.y, newChip == null ? null : newChip.y, t),
                width = lerp(oldChip == null ? null : oldChip.width, newChip == null ? null : newChip.width, t);

            path.reset();
            r1.set(x, y, x + D, y + D);
            path.arcTo(r1, 90, 180, true);
            r2.set(x + width - D, y, x + width, y + D);
            path.arcTo(r2, 270, 180, false);
            path.close();

            int wasAlpha = chipBackground.getAlpha();
            chipBackground.setAlpha((int) (wasAlpha * alpha));
            canvas.drawPath(path, chipBackground);
            chipBackground.setAlpha(wasAlpha);

            boolean oldChosen = oldChip != null && oldChip.chosen,
                    newChosen = newChip != null && newChip.chosen;
            if (newChosen || oldChosen) {
                float s = newChosen && oldChosen ? 1f : (newChosen ? t : 1f - t);
                if (s > 0f) {
                    chipSelector.setStrokeWidth(s * (float) CHIP_BORDER());
                    chipSelector.setAlpha((int) (255 * alpha));
                    canvas.drawPath(path, chipSelector);
                }
            }

            ImageReceiver reactionImage = newChip == null ? reactionImages.get(oldChip.reaction) : reactionImages.get(newChip.reaction);
            if (reactionImage != null) {
                canvas.save();
                canvas.translate(x + CHIP_PADDING_HORIZONTAL(), y + paddingVertical);
                int sz = CHIP_REACTION_SIZE();
                reactionImage.setImageCoords(sz * (1 - alpha) / 2, sz * (1 - alpha) / 2, sz * alpha, sz * alpha);
                reactionImage.draw(canvas);
                canvas.restore();
            }

            String oldCount = oldChip == null ? null : oldChip.count;
            String newCount = newChip == null ? null : newChip.count;
            drawTextSmartTransition(canvas, x + CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN(), y + CHIP_HEIGHT() / 2, oldCount, newCount, t);

            int oldChipUsersCount = oldChip == null ? 0 : (oldChip.userIds == null ? 0 : oldChip.userIds.size()),
                newChipUsersCount = newChip == null ? 0 : (newChip.userIds == null ? 0 : newChip.userIds.size());
            int maxUsers = Math.max(oldChipUsersCount, newChipUsersCount);

            userIds.clear();
            if (newChip != null && newChip.userIds != null)
                userIds.addAll(newChip.userIds);
            for (int i = 0; i < oldChipUsersCount; ++i) {
                if (!userIds.contains(oldChip.userIds.get(i)))
                    userIds.add(oldChip.userIds.get(i));
            }
            Collections.sort(userIds, new Comparator<Long>() {
                @Override
                public int compare(Long a, Long b) {
                    if (newChip != null && newChip.userIds != null) {
                        return newChip.userIds.indexOf(b) - newChip.userIds.indexOf(a);
                    }
                    return 0;
                }
            });
            for (long userId : userIds) {
                int posInOldChip = -1, posInNewChip = -1;
                if (oldChip != null && oldChip.userIds != null)
                    posInOldChip = oldChip.userIds.indexOf(userId);
                if (newChip != null && newChip.userIds != null)
                    posInNewChip = newChip.userIds.indexOf(userId);
                int pos = posInNewChip != -1 ? posInNewChip : posInOldChip;

                ImageReceiver userAvatar = avatarImages.get(userId);

                int iy = y + CHIP_HEIGHT() / 2 - CHIP_AVATAR_SIZE() / 2,
                    ix = lerp(
                            posInOldChip == -1 ? null : (x + CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + posInOldChip * CHIP_AVATAR_INDENT()),
                            posInNewChip == -1 ? null : (x + CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + posInNewChip * CHIP_AVATAR_INDENT()),
                            t
                    );
                float sz = 1f;
                if (posInOldChip == -1)
                    sz = t;
                else if (posInNewChip == -1)
                    sz = 1f - t;

                chipAvatarLoading.setAlpha((int) (255 * sz));
                canvas.drawCircle(ix + CHIP_AVATAR_SIZE() / 2f, iy + CHIP_AVATAR_SIZE() / 2f, CHIP_AVATAR_SIZE() / 2f * sz, chipAvatarLoading);
                if (userAvatar != null) {
                    userAvatar.setImageCoords(ix + CHIP_AVATAR_SIZE() * (1f - sz) / 2, iy + CHIP_AVATAR_SIZE() * (1f - sz) / 2, CHIP_AVATAR_SIZE() * sz, CHIP_AVATAR_SIZE() * sz);
                    userAvatar.setAlpha(sz);
                    userAvatar.draw(canvas);
                }

                if (look != OUTSIDE) {
                    float S = (CHIP_AVATAR_SIZE() + CHIP_AVATAR_BORDER());
                    float sy = y + CHIP_HEIGHT() / 2f - S / 2f;
                    r1.set(ix + S * (1f - sz) / 2f - CHIP_AVATAR_BORDER() * .75f, sy + S * (1f - sz) / 2f, ix + S * (1f - sz) / 2f + S * sz - CHIP_AVATAR_BORDER() * .75f, sy + S * (1f - sz) / 2f + S * sz);
                    chipAvatarBorder.setAlpha((int) (255 * sz));
                    canvas.drawArc(r1, 0, 360, false, chipAvatarBorder);
                }
            }

//            if (oldCount != null && newCount != null && t < 1f) {
//                if (oldCount.equals(newCount)) {
//                    countPaint.getTextBounds(newCount, 0, newCount.length(), tRect);
//                    countPaint.setAlpha(255);
//                    canvas.drawText(newCount, x + CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN(), y + CHIP_HEIGHT() / 2 - tRect.exactCenterY(), countPaint);
//                } else
//
//            } else {
//                if (oldCount != null) {
//                    countPaint.getTextBounds(oldCount, 0, oldCount.length(), tRect);
//                    countPaint.setAlpha((int) (255 * (1f - t)));
//                    canvas.drawText(oldCount, x + CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN(), y + CHIP_HEIGHT() / 2 - tRect.exactCenterY(), countPaint);
//                }
//
//                if (newCount != null) {
//                    countPaint.getTextBounds(newCount, 0, newCount.length(), tRect);
//                    countPaint.setAlpha((int) (255 * t));
//                    canvas.drawText(newCount, x + CHIP_PADDING_HORIZONTAL() + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN(), y + CHIP_HEIGHT() / 2 - tRect.exactCenterY(), countPaint);
//                }
//            }
        }
    }

    private void drawTextSmartTransition(Canvas canvas, int x, int y, String from, String to, float t) {
        String str;
        int maxLength = Math.max(from == null ? 0 : from.length(), to == null ? 0 : to.length());
        float fromX = 0, toX = 0, w;

        float fromExactCenterY = 0, toExactCenterY = 0;
        if (from != null) {
            countPaint.getTextBounds(from, 0, from.length(), tRect);
            fromExactCenterY = tRect.exactCenterY();
        }
        if (to != null) {
            countPaint.getTextBounds(to, 0, to.length(), tRect);
            toExactCenterY = tRect.exactCenterY();
        }

        for (int i = 0; i < maxLength; ++i) {
            Character fromChar = from != null && i < from.length() ? from.charAt(i) : null;
            Character toChar = to != null && i < to.length() ? to.charAt(i) : null;

            // https://www.desmos.com/calculator/pdbhdkxyig
            float T = Math.max(0, Math.min(1, ((2 * maxLength * t) - t - i) / (float) maxLength));

            if (fromChar != null && fromChar == toChar) {
                str = String.valueOf(fromChar);
                countPaint.setAlpha(255);
                w = countPaint.measureText(str);
                canvas.drawText(str, x + toX, y - (fromExactCenterY + (toExactCenterY - fromExactCenterY) * t), countPaint);
                fromX += w;
                toX += w;
            } else {
                if (fromChar != null) {
                    str = String.valueOf(fromChar);
                    countPaint.setAlpha((int) (255 * (1f - T)));
                    w = countPaint.measureText(str);
                    canvas.drawText(str, x + fromX, y - fromExactCenterY - CHIP_TEXT_MOVE() * T, countPaint);
                    fromX += w;
                }

                if (toChar != null) {
                    str = String.valueOf(toChar);
                    countPaint.setAlpha((int) (255 * T));
                    w = countPaint.measureText(str);
                    canvas.drawText(str, x + toX, y - toExactCenterY + CHIP_TEXT_MOVE() * (1f - T), countPaint);
                    toX += w;
                }
            }
        }
    }

    private DecimalFormat df = new DecimalFormat("0.#");
    private String countToString(int count) {
//        count = (int) (Math.random() * 10000000);
        if (count > 1000000)
            return df.format(count / 1000000f) + "M";
        if (count > 1000)
            return df.format(count / 1000f) + "K";
        return count + "";
    }

    public int lerp(Integer from, Integer to, float t) {
        if (from == null)
            return to;
        if (to == null)
            return from;
        return (int) (from + (to - from) * t);
    }
}
