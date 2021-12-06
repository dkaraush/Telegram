package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.ArrayList;

public class ReactionAnimationView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public boolean quick = false;

    private Point point = new Point();
    private Rect rect = new Rect();

    private int currentAccount;
    private TLRPC.TL_availableReaction reaction;
    private String reactionString;

    private RectF fromCoordinates = new RectF();
    private Rect centerCoordinates = new Rect();

    private float mainImageSize = 181;
    private int mainImageSizePx = dp(mainImageSize);

    private float effectsImageSize = 350;
    private int effectsImageSizePx = dp(effectsImageSize);

    private View fromImage = null;
    private BackupImageView mainImage = null;
    private boolean mainImageAnimationReady = false;
    private BackupImageView effectsImage = null;
    private boolean effectsImageAnimationReady = false;
    private BackupImageView staticImage = null;

    private Runnable onDismiss = null;

    public ReactionAnimationView(Context context, TLRPC.TL_availableReaction reaction, Runnable onDismiss) {
        super(context);

        this.reaction = reaction;

        this.onDismiss = onDismiss;
        setOnClickListener(e -> abort());
    }

    public ReactionAnimationView(Context context, String reaction, int currentAccount, Runnable onDismiss) {
        super(context);

        this.currentAccount = currentAccount;
        this.reactionString = reaction;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.availableReactionsUpdate);

        ArrayList<TLRPC.TL_availableReaction> availableReactions = MessagesController.getInstance(currentAccount).getAvailableReactions();
        if (availableReactions != null) {
            for (TLRPC.TL_availableReaction availableReaction : availableReactions) {
                if (availableReaction != null && availableReaction.reaction != null && availableReaction.reaction.equals(reactionString)) {
                    this.reaction = availableReaction;
                    break;
                }
            }
        }

        this.onDismiss = onDismiss;
        setOnClickListener(e -> abort());
    }

    private Runnable onAbort = null;
    public void setOnAbort(Runnable onAbort) {
        this.onAbort = onAbort;
    }

    private ChatMessageCell messageCell;

    private View fromSelectToSet = null;
    private RectF fromStaticToSet = null;

    private long start = -1;
    private ValueAnimator animator = null;
    public void initFromSelect(View reactionImageView, ChatMessageCell messageCell) {
        this.messageCell = messageCell;

        if (this.reaction == null) {
            fromSelectToSet = reactionImageView;
            return;
        }

        this.fromImage = reactionImageView;
        if (fromImage != null) {
            fromImage.getGlobalVisibleRect(rect, point);
            int[] location = new int[2];
            fromImage.getLocationOnScreen(location);
            fromCoordinates.set(location[0], location[1], location[0] + fromImage.getWidth(), location[1] + fromImage.getHeight());

            ViewGroup reactionImageViewParent = (ViewGroup) fromImage.getParent();
            if (reactionImageViewParent != null)
                reactionImageViewParent.removeView(fromImage);
            fromImage.setLayoutParams(LayoutHelper.createFrame(0, 0, Gravity.NO_GRAVITY, 0, 0, 0, 0));

            fromImage.setAlpha(0);
            this.addView(fromImage);
        }

        staticImage = new BackupImageView(getContext());
        staticImage.setAlpha(0);
        TLRPC.Document staticImageDocument = reaction.static_icon;
        staticImage.setImage(ImageLocation.getForDocument(staticImageDocument), null, null, "webp", mainImageSizePx, null);
        addView(staticImage, LayoutHelper.createFrame(0, 0, Gravity.NO_GRAVITY, 0, 0, 0, 0));

        mainImage = new BackupImageView(getContext());
        mainImage.setAlpha(0);
        TLRPC.Document mainImageDocument = reaction.activate_animation;
        mainImage.setImage(ImageLocation.getForDocument(mainImageDocument), null, null, null, this);
        mainImage.imageReceiver.setAllowStartAnimation(false);
        if (mainImage.imageReceiver.getLottieAnimation() != null)
            mainImage.imageReceiver.getLottieAnimation().setProgress(0f);
        mainImage.imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
            }

            @Override
            public void onAnimationReady(ImageReceiver imageReceiver) {
//                mainImageAnimationReady = true;
//                if (effectsImageAnimationReady)
                start();
            }
        });
        addView(mainImage, LayoutHelper.createFrame(0, 0, Gravity.NO_GRAVITY, 0, 0, 0, 0));
        mainImage.bringToFront();

        effectsImage = new BackupImageView(getContext());
        effectsImage.setAlpha(0);
        effectsImage.setWillNotDraw(false);
        TLRPC.Document effectsImageDocument = reaction.effect_animation;
        effectsImage.setImage(ImageLocation.getForDocument(effectsImageDocument), null, null, null, this);
        effectsImage.imageReceiver.setAllowStartAnimation(false);
        if (effectsImage.imageReceiver.getLottieAnimation() != null)
            effectsImage.imageReceiver.getLottieAnimation().setProgress(0f);
        effectsImage.imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
            }

            @Override
            public void onAnimationReady(ImageReceiver imageReceiver) {
                effectsImageAnimationReady = true;
                if (mainImageAnimationReady)
                    start();
            }
        });
        addView(effectsImage, LayoutHelper.createFrame(0, 0, Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        effectsImage.bringToFront();

        if (reaction.reaction != null && reaction.reaction.equals("\uD83D\uDE2D"))
            mainImage.bringToFront();

        start();
    }
    public void initFromStatic(RectF fromCoordinatesX, ChatMessageCell messageCell) {
        this.messageCell = messageCell;

        if (this.reaction == null) {
            fromStaticToSet = fromCoordinates;
            return;
        }

        staticImage = new BackupImageView(getContext());
        staticImage.setAlpha(0);
        TLRPC.Document staticImageDocument = reaction.static_icon;
        staticImage.setImage(ImageLocation.getForDocument(staticImageDocument), null, null, "webp", mainImageSizePx, null);
        addView(staticImage, LayoutHelper.createFrame(0, 0, Gravity.NO_GRAVITY, 0, 0, 0, 0));

        this.fromImage = staticImage;
        this.fromCoordinates.set(fromCoordinatesX);

        mainImage = new BackupImageView(getContext());
        mainImage.setAlpha(0);
        TLRPC.Document mainImageDocument = reaction.activate_animation;
        mainImage.setImage(ImageLocation.getForDocument(mainImageDocument), null, null, null, this);
        mainImage.imageReceiver.setAllowStartAnimation(false);
        if (mainImage.imageReceiver.getLottieAnimation() != null)
            mainImage.imageReceiver.getLottieAnimation().setProgress(0f);
        mainImage.imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
            }

            @Override
            public void onAnimationReady(ImageReceiver imageReceiver) {
//                mainImageAnimationReady = true;
//                if (effectsImageAnimationReady)
                start();
            }
        });
        addView(mainImage, LayoutHelper.createFrame(0, 0, Gravity.NO_GRAVITY, 0, 0, 0, 0));
        mainImage.bringToFront();

        effectsImage = new BackupImageView(getContext());
        effectsImage.setAlpha(0);
        effectsImage.setWillNotDraw(false);
        TLRPC.Document effectsImageDocument = reaction.effect_animation;
        effectsImage.setImage(ImageLocation.getForDocument(effectsImageDocument), null, null, null, this);
        effectsImage.imageReceiver.setAllowStartAnimation(false);
        if (effectsImage.imageReceiver.getLottieAnimation() != null)
            effectsImage.imageReceiver.getLottieAnimation().setProgress(0f);
        effectsImage.imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
            }

            @Override
            public void onAnimationReady(ImageReceiver imageReceiver) {
                effectsImageAnimationReady = true;
                if (mainImageAnimationReady)
                    start();
            }
        });
        addView(effectsImage, LayoutHelper.createFrame(0, 0, Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        effectsImage.bringToFront();

        if (reaction.reaction != null && reaction.reaction.equals("\uD83D\uDE2D"))
            mainImage.bringToFront();

        start();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.availableReactionsUpdate) {
            ArrayList<TLRPC.TL_availableReaction> availableReactions = (ArrayList) args[0];

            for (TLRPC.TL_availableReaction availableReaction : availableReactions) {
                if (availableReaction != null && availableReaction.reaction != null && availableReaction.reaction.equals(reactionString)) {
                    reaction = availableReaction;
                    break;
                }
            }

            if (reaction != null) {
                if (fromSelectToSet != null) {
                    initFromSelect(fromSelectToSet, messageCell);
                    fromSelectToSet = null;
                } else if (fromStaticToSet != null) {
                    initFromStatic(fromStaticToSet, messageCell);
                    fromStaticToSet = null;
                }
            }
        }
    }

    private void effectsCenter(String reaction, PointF outPoint) {
        switch (reaction) {
            case "❤":
                outPoint.set(.75f, .425f);
                break;
            case "\uD83D\uDE02":
            case "\uD83C\uDF89":
                outPoint.set(.84f, .5f);
                break;
            case "\uD83E\uDD2E":
            case "\uD83D\uDCA9":
                outPoint.set(.79f, .53f);
                break;
            case "\uD83D\uDD25":
                outPoint.set(.85f, .7f);
                break;
            case "\uD83D\uDC4D":
            case "\uD83D\uDC4E":
            case "\uD83E\uDD29":
            case "\uD83D\uDE31":
            case "\uD83D\uDE2D":
            default:
                outPoint.set(.75f, .45f);
                break;
        }
    }

    public void start() {
        if (start > 0)
            return;

        start = System.currentTimeMillis();
//        update();
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(a -> update());
            animator.setDuration(Long.MAX_VALUE);
            animator.start();
        }

        if (fromImage instanceof BackupImageView && ((BackupImageView) fromImage).imageReceiver.getLottieAnimation() != null)
            ((BackupImageView) fromImage).imageReceiver.getLottieAnimation().stop();

        if (mainImage.imageReceiver.getLottieAnimation() != null)
            mainImage.imageReceiver.getLottieAnimation().setProgress(0f);
        mainImage.imageReceiver.setAutoRepeat(0);
        mainImage.imageReceiver.startAnimation();

        if (effectsImage.imageReceiver.getLottieAnimation() != null)
            effectsImage.imageReceiver.getLottieAnimation().setProgress(0f);
        effectsImage.imageReceiver.setAutoRepeat(0);
        effectsImage.imageReceiver.startAnimation();

//        if (staticImage != null)
//            staticImage.setVisibility(View.VISIBLE);
//        if (mainImage != null)
//            mainImage.setVisibility(View.VISIBLE);
//        if (effectsImage != null)
//            effectsImage.setVisibility(View.VISIBLE);
//        if (fromImage != null)
//            fromImage.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.availableReactionsUpdate);
    }

    private float easeInOutQuad(float x) {
        return x < 0.5 ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
    }
    private float easeOutBack_G = 1.7f;
    private float easeOutBack(float x) {
        return 1f + (1f + easeOutBack_G) * (float) Math.pow(x - 1f, 3) + easeOutBack_G * (float) Math.pow(x - 1f, 2f);
    }

    private void layout(View view, RectF rect) {
        if (view == null || rect == null)
            return;
        if (view.getLeft() != rect.left || view.getRight() != rect.right || view.getTop() != rect.top || view.getBottom() != rect.bottom || view.getWidth() != rect.width() || view.getHeight() != rect.height()) {
            view.layout((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
            view.measure(
                    MeasureSpec.makeMeasureSpec((int) rect.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) rect.height(), MeasureSpec.EXACTLY)
            );
        }
    }
    private void layout(View view, Rect rect) {
        if (view == null || rect == null)
            return;
        if (view.getLeft() != rect.left || view.getRight() != rect.right || view.getTop() != rect.top || view.getBottom() != rect.bottom || view.getWidth() != rect.width() || view.getHeight() != rect.height()) {
            view.layout((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
            view.measure(
                MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(rect.height(), MeasureSpec.EXACTLY)
            );
        }
    }

    public long abortStartTime = 0;
    public void abort() {
        if (abortStartTime > 0 && onDismiss != null)
            onDismiss.run();
        else {
            if (onAbort != null)
                onAbort.run();
            abortStartTime = System.currentTimeMillis();
        }
    }

    private PointF p = new PointF();
    private RectF from = new RectF(),
            middle = new RectF(),
            to = new RectF(),
            toEffects = new RectF(),
            current = new RectF(),
            boundsF = new RectF(),
            effectsImageRect = new RectF();
    public void update() {
        long NOW = System.currentTimeMillis();
        boundsF.set(getLeft(), getTop(), getLeft() + getWidth(), getTop() + getHeight());

        int[] myLocation = new int[2];
        getRootView().getLocationOnScreen(myLocation);

        float fromMovingTDuration = 200f;
        float fromMovingT = Math.max(0, Math.min(1, (NOW - start) / fromMovingTDuration));
//        fromMovingT = easeInOutQuad(fromMovingT);

        long animationDuration = Math.min(
                mainImage.imageReceiver.getLottieAnimation() == null ? 0 : Math.max(0, mainImage.imageReceiver.getLottieAnimation().getDuration() - 100),
                effectsImage.imageReceiver.getLottieAnimation() == null ? 0 :  Math.max(0, effectsImage.imageReceiver.getLottieAnimation().getDuration() - 100)
        ) / (quick ? 2 : 1);
//        float animationT = animationDuration <= 0 ? 0 : Math.max(0, Math.min(1, (NOW - start) / animationDuration));
        float toMovingTDuration = quick ? 150f : 200f, abortDuration = quick ? 125f : 200f;
        float toMovingT = Math.max(0, Math.min(1, Math.max(abortStartTime <= 0 ? 0 : (NOW - abortStartTime) / abortDuration, animationDuration <= 0 ? 0 : (NOW - (start + animationDuration)) / toMovingTDuration)));
        toMovingT = easeInOutQuad(toMovingT);

        from.set(fromCoordinates);
//        if (shouldOffsetFrom)
            from.offset(-myLocation[0], -myLocation[1]);

        to.set(boundsF.centerX(), boundsF.centerY(), boundsF.centerX(), boundsF.centerY());
        if (messageCell != null) {
            if (messageCell.getReactionImagePosition(reaction.reaction, to))
                to.offset(-myLocation[0], -myLocation[1]);
        }

        effectsCenter(reaction.reaction, p);
        effectsImageRect.set(
            lerp(from.centerX(), boundsF.centerX(), 0) - effectsImageSizePx * p.x,
            (from.centerY() - effectsImageSizePx * p.y),
            lerp(from.centerX(), boundsF.centerX(), 0) + effectsImageSizePx * (1f - p.x),
            (from.centerY() + effectsImageSizePx * (1f - p.y))
        );

        if (effectsImageRect.left < boundsF.width() * .05f) {
            effectsImage.setScaleX(-1f);
            effectsImageRect.set(
                lerp(from.centerX(), boundsF.centerX(), 0) - effectsImageSizePx * (1f - p.x),
                (from.centerY() - effectsImageSizePx * p.y),
                lerp(from.centerX(), boundsF.centerX(), 0) + effectsImageSizePx * p.x,
                (from.centerY() + effectsImageSizePx * (1f - p.y))
            );
        } else
            effectsImage.setScaleX(1f);
        layout(effectsImage, effectsImageRect);
        effectsImage.setAlpha(1f - toMovingT);

        middle.set(
            boundsF.left + (boundsF.width() - mainImageSizePx) / 2f,
            Math.max(effectsImageRect.centerY() - mainImageSizePx / 2f, 0),
            boundsF.left + (boundsF.width() + mainImageSizePx) / 2f,
            Math.max(effectsImageRect.centerY() - mainImageSizePx / 2f, 0) + mainImageSizePx
        );
        if (middle.bottom > boundsF.bottom - dp(16))
            middle.offset(0, -(middle.bottom - (boundsF.bottom - dp(16))));
        else if (middle.top < dp(16))
            middle.offset(0, (dp(16) - middle.top));

        toEffects.set(boundsF.left + (boundsF.width() - effectsImageSizePx) / 2f, boundsF.top + (boundsF.height() - effectsImageSizePx) / 2f, boundsF.left + (boundsF.width() + effectsImageSizePx) / 2f, boundsF.top + (boundsF.height() + effectsImageSizePx) / 2f);

        float centerX = from.centerX() + (middle.centerX() - from.centerX()) * fromMovingT,
              centerY = from.centerY() + (middle.centerY() - from.centerY()) * fromMovingT,
              width =  from.width() +    (middle.width()  - from.width())  * fromMovingT,
              height = from.height() +   (middle.height() - from.height()) * fromMovingT;
        centerX = centerX + (to.centerX() - centerX) * toMovingT;
        centerY = centerY + (to.centerY() - centerY) * toMovingT;
        width = width + (to.width() - width) * toMovingT;
        height = height + (to.height() - height) * toMovingT;
        current.set(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f);

        if (fromImage == staticImage) {
            layout(fromImage, current);
            fromImage.setAlpha(Math.max(mainImage.imageReceiver.getLottieAnimation() == null ? 1 : 0, Math.max(1f - fromMovingT, Math.min(1, toMovingT))));
            if (toMovingT > 0.95f)
                fromImage.setAlpha((1f - toMovingT) / .05f);
        } else {
            layout(fromImage, current);
            fromImage.setAlpha(Math.max(mainImage.imageReceiver.getLottieAnimation() == null ? 1 : 0, 1f - fromMovingT));

            layout(staticImage, current);
            staticImage.setAlpha(Math.min(1, toMovingT));
            if (toMovingT > 0.95f)
                staticImage.setAlpha((1f - toMovingT) / .05f);
        }

        layout(mainImage, current);
        mainImage.setAlpha(Math.min(fromMovingT, 1f - toMovingT));

        if (animationDuration > 0 && ((abortStartTime > 0 && NOW - abortStartTime > abortDuration) || NOW - start > fromMovingTDuration + animationDuration + toMovingTDuration)) {
            setAlpha(0f);
            if (onDismiss != null)
                onDismiss.run();
            animator.cancel();
        }
    }

//
//    private Paint RED = new Paint();
//    @Override
//    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
//
//        Rect bounds = canvas.getClipBounds();
//        boundsF.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
//
//        int[] myLocation = new int[2];
//        getRootView().getLocationOnScreen(myLocation);
//
//        from.set(fromCoordinates.left - myLocation[0], fromCoordinates.top - myLocation[1], fromCoordinates.right - myLocation[0], fromCoordinates.bottom - myLocation[1]);
//        to.set(bounds.left + (bounds.width() - mainImageSizePx) / 2f, bounds.top + (bounds.height() - mainImageSizePx) / 2f, bounds.left + (bounds.width() + mainImageSizePx) / 2f, bounds.top + (bounds.height() + mainImageSizePx) / 2f);
//        toEffects.set(bounds.left + (bounds.width() - effectsImageSizePx) / 2f, bounds.top + (bounds.height() - effectsImageSizePx) / 2f, bounds.left + (bounds.width() + effectsImageSizePx) / 2f, bounds.top + (bounds.height() + effectsImageSizePx) / 2f);
//
//        float movingT = Math.max(0, Math.min(1, (System.currentTimeMillis() - start) / 250f));
//        movingT = easeInOutQuad(movingT);
//
//        float centerX = from.centerX() + (to.centerX() - from.centerX()) * movingT,
//              centerY = from.centerY() + (to.centerY() - from.centerY()) * movingT,
//              width = from.width() + (to.width() - from.width()) * movingT,
//              height = from.height() + (to.height() - from.height()) * movingT;
//        current.set(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f);
//
//        final int saved = canvas.save();
//
//        if (child == fromImage) {
//            child.getDrawingRect(rect);
//
//            float s = width / child.getWidth();
//            canvas.scale(s, s);
//            canvas.translate(-rect.width() / 2f, -rect.height() / 2f);
//            canvas.translate(centerX / s, centerY / s);
//
//            child.setAlpha(1f - movingT);
//        } else if (child == mainImage) {
//            if (start < 0) {
//                canvas.restoreToCount(saved);
//                return false;
//            }
//            child.getDrawingRect(rect);
//
//            float s = width / child.getWidth();
//            canvas.scale(s, s);
//            canvas.translate(-rect.width() / 2f, -rect.height() / 2f);
//            canvas.translate(centerX / s, centerY / s);
//
//            child.setAlpha(Math.min(1f, movingT * 2f));
//        } else if (child == effectsImage) {
//            if (start < 0) {
//                canvas.restoreToCount(saved);
//                return false;
//            }
//            child.getDrawingRect(rect);
//
//            canvas.translate(-rect.width() / 2f, -rect.height() / 2f);
//            canvas.translate(centerX, centerY);
//
//            child.setAlpha(1f);
//        }
//
//        boolean result = super.drawChild(canvas, child, drawingTime);
//
//        canvas.restoreToCount(saved);
//
//        return result;
//    }
}
