package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.Pair;
import android.view.Gravity;
import android.view.MotionEvent;
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
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatMessageScrimPopup;
import org.telegram.ui.Components.LayoutHelper;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

public class ChatMessageCellReactions extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int OUTSIDE = 0;
    public static final int INSIDE = 1;
    public static final int INSIDE_OWNER = 2;

    private Paint chipBackground = new Paint();
    private Paint chipSelector = new Paint();
    private TextPaint countPaint = new TextPaint();

    private int currentAccount;
    public ChatMessageCellReactions(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;

        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setBackgroundColor(0x01ff0000);

        chipBackground.setFlags(Paint.ANTI_ALIAS_FLAG);
        countPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        countPaint.setTextSize(dp(14));
        countPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        chipSelector.setStyle(Paint.Style.STROKE);
        chipSelector.setStrokeWidth(CHIP_BORDER());
        chipSelector.setFlags(Paint.ANTI_ALIAS_FLAG);
        setLook(OUTSIDE);
    }

    public int look = OUTSIDE;
    public void setLook(int look) {
        this.look = look;

        countPaint.setColor((new int[]{ 0xffffffff, 0xff378DD1, 0xff53AC50 })[look]); // TODO(dkaraush): color!
        chipBackground.setColor((new int[]{ 0x42214119, 0x19378DD1, 0x1e5BA756 })[look]); // TODO(dkaraush): color!
        chipSelector.setColor((new int[]{ 0xffffffff, 0xff378DD1, 0xff53AC50 })[look]); // TODO(dkaraush): color!
    }

    private MessagesStorage.StringCallback onReactionClick = null;
    public void setOnReactionClick(MessagesStorage.StringCallback onReactionClick) {
        this.onReactionClick = onReactionClick;
    }

    private boolean active = false;
    public void on() {
        if (active)
            return;
        active = true;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.availableReactionsUpdate);
    }
    public void off() {
        if (!active)
            return;
        active = false;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.availableReactionsUpdate);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.availableReactionsUpdate) {
            for (Pair<ReactionChip, ReactionChip> pair : state) {
                if (pair.first != null && pair.first.reaction != null)
                    tryToMakeReactionImage(pair.first.reaction);
                if (pair.second != null && pair.second.reaction != null)
                    tryToMakeReactionImage(pair.second.reaction);
            }
            invalidate();
        }
    }

    private int CHIP_HEIGHT() { return dp(26); }
    private int CHIP_BORDER() { return dp(4f / 3f); }
    private int CHIP_PADDING_HORIZONTAL() { return dp(7); }
    private int CHIP_INNER_MARGIN() { return dp(11f / 3f); }
    private int CHIP_REACTION_SIZE() { return dp(20); }
    private int CHIP_MARGIN() { return dp(6); }
    private int CHIP_TEXT_MOVE() { return dp(8); }

    private TLRPC.TL_messageReactions scheduledUpdate = null;

    public int updateReactions(TLRPC.TL_messageReactions messageReactions, boolean animated) {
        if (animated) {
            if (System.currentTimeMillis() - lastUpdate < updateAnimationDuration) {
                boolean wasPosted = scheduledUpdate != null;
                scheduledUpdate = messageReactions;
                if (!wasPosted) {
                    postDelayed(() -> {
                        applyUpdateAnimated(scheduledUpdate);
                        scheduledUpdate = null;
                    }, System.currentTimeMillis() - lastUpdate);
                }
            } else {
                applyUpdateAnimated(messageReactions);
            }
        } else {
            state = layout(messageReactions, null);
        }
        return height;
    }

    private void applyUpdateAnimated(TLRPC.TL_messageReactions messageReactions) {
        state = layout(messageReactions, state);
        lastUpdate = System.currentTimeMillis();
        postDelayed(() -> invalidateForNextMs(updateAnimationDuration), 16);
    }

    private ValueAnimator a;
    private void invalidateForNextMs(long ms) {
        if (a != null)
            a.cancel();
        a = ValueAnimator.ofFloat(0f, 1f);
        a.addUpdateListener(a -> invalidate());
        a.setDuration(ms);
        a.start();
    }

    private HashMap<String, ImageReceiver> reactionImages = new HashMap<>();
    private ImageReceiver makeReactionImage(String reactionString) {
        ArrayList<TLRPC.TL_availableReaction> allReactions = MessagesController.getInstance(currentAccount).getAvailableReactions();
        if (allReactions == null)
            return null;
        TLRPC.TL_availableReaction reaction = null;
        for (TLRPC.TL_availableReaction r : allReactions) {
            if (r != null && r.reaction != null && r.reaction.equals(reactionString)) {
                reaction = r;
                break;
            }
        }
        if (reaction == null)
            return null;

        TLRPC.Document reactionDocument = reaction.static_icon;
        ImageReceiver ig = new ImageReceiver();
        ig.setImage(ImageLocation.getForDocument(reactionDocument), null, null, "webp", null, CHIP_REACTION_SIZE());
        return ig;
    }
    private void tryToMakeReactionImage(String reactionString) {
        if (reactionImages.containsKey(reactionString))
            return;
        ImageReceiver ig = makeReactionImage(reactionString);
        if (ig == null)
            return;
        reactionImages.put(reactionString, ig);
    }

    class ReactionChip {
        String reaction;
        int x, y, width;
        ImageReceiver[] avatars;
        String count;
        boolean chosen;
    }
    private int height = 0;
    private ArrayList<Pair<ReactionChip, ReactionChip>> state = new ArrayList<>();
    private long lastUpdate = 0;
    private long updateAnimationDuration = 200;

    public boolean checkTouchEvent(MotionEvent event) {

        int ex = (int) event.getX(),
            ey = (int) event.getY();

        ReactionChip chip = null;
        float t = easeInOutQuad(Math.max(0, Math.min(1, (System.currentTimeMillis() - lastUpdate) / (float) updateAnimationDuration)));
        for (Pair<ReactionChip, ReactionChip> pair : state) {
            ReactionChip newChip = pair.first;
            ReactionChip oldChip = pair.second;
            if (newChip == null && oldChip == null)
                continue;

            int x = lerp(oldChip == null ? null : oldChip.x, newChip == null ? null : newChip.x, t),
                y = lerp(oldChip == null ? null : oldChip.y, newChip == null ? null : newChip.y, t),
                width = lerp(oldChip == null ? null : oldChip.width, newChip == null ? null : newChip.width, t);

            if (ex >= x && ex <= x + width && ey >= y && ey <= y + CHIP_HEIGHT()) {
                chip = newChip == null ? oldChip : newChip;
            }
        }

        if (chip != null && chip.reaction != null) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (onReactionClick != null) {
                    onReactionClick.run(chip.reaction);
                }
            }
            return true;
        }

        return super.onTouchEvent(event);
    }

    private ArrayList<Pair<ReactionChip, ReactionChip>> layout(TLRPC.TL_messageReactions reactions, ArrayList<Pair<ReactionChip, ReactionChip>> prevState) {
        ArrayList<Pair<ReactionChip, ReactionChip>> newState = new ArrayList<>();
        int x = 0, y = CHIP_MARGIN();
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
                    tryToMakeReactionImage(newChip.reaction);
                    newChip.count = countToString(reactionCount.count);
                    newChip.width = CHIP_PADDING_HORIZONTAL() * 2 + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + (int) countPaint.measureText(newChip.count);

                    if (x > 0 && x + newChip.width + CHIP_MARGIN() > getWidth() - CHIP_BORDER() / 2) {
                        x = 0;
                        y += CHIP_HEIGHT() + CHIP_MARGIN();
                    }
                    newChip.x = x + CHIP_BORDER();
                    newChip.y = y;
                    x += newChip.width + CHIP_MARGIN();

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
                tryToMakeReactionImage(newChip.reaction);
                newChip.count = countToString(reactionCount.count);
                newChip.width = CHIP_PADDING_HORIZONTAL() * 2 + CHIP_REACTION_SIZE() + CHIP_INNER_MARGIN() + (int) countPaint.measureText(newChip.count);

                if (x > 0 && x + newChip.width + CHIP_MARGIN() > getWidth() - CHIP_BORDER() / 2) {
                    x = 0;
                    y += CHIP_HEIGHT() + CHIP_MARGIN();
                }
                newChip.x = x + CHIP_BORDER();
                newChip.y = y;
                x += newChip.width + CHIP_MARGIN();

                newState.add(new Pair<>(newChip, null));
            }
        }
        height = y + CHIP_HEIGHT() + CHIP_MARGIN() + CHIP_BORDER();
        return newState;
    }
    private float easeInOutQuad(float x) {
        return x < 0.5 ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
    }

    private Path path = new Path();
    private RectF r1 = new RectF(),
                  r2 = new RectF();
    private Rect tRect = new Rect();
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

            boolean oldChosen = oldChip != null ? oldChip.chosen : false,
                    newChosen = newChip != null ? newChip.chosen : false;
            if (newChosen || oldChosen) {
                float s = newChosen && oldChosen ? 1f : (newChosen ? t : 1f - t);
                if (s > 0f) {
                    chipSelector.setStrokeWidth(s * (float) CHIP_BORDER());
                    canvas.drawPath(path, chipSelector);
                }
            }

            ImageReceiver reactionImage = newChip == null ? (oldChip == null ? null : reactionImages.get(oldChip.reaction)) : reactionImages.get(newChip.reaction);
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
        count = (int) (Math.random() * 10000000);
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
