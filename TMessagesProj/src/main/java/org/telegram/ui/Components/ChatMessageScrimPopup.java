package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.Rect;
import android.graphics.Point;
import android.icu.util.Measure;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class ChatMessageScrimPopup extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private ChatMessageReactionList reactionButtonList;

    private FrameLayout container;

    private FrameLayout reactionsButtonContainer;
    private FrameLayout reactionsButton;
    private ImageView reactionsButtonIcon;
    private TextView reactionsButtonText;
    private FrameLayout reactionsButtonGap;
    private LinearLayout menu;

    public int currentAccount;
    private MessageObject message;
    private Runnable onDismiss;

    public ChatMessageScrimPopup(
            Context context,
            int currentAccount,
            ActionBarPopupWindow.ActionBarPopupWindowLayout buttons,
            MessageObject message,
            Runnable onDismiss,
            ReactionCallback onReactionClick
    ) {
        super(context);

        this.currentAccount = currentAccount;
        this.message = message;
        this.onDismiss = onDismiss;

        this.setOnTouchListener(new OnTouchListener() {
            private Rect containerRect = new Rect(),
                         reactionsListRect = new Rect();
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    boolean containsInContainer = false;
                    if (container != null) {
                        container.getGlobalVisibleRect(containerRect);
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

        container = new FrameLayout(context);

        menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.VERTICAL);

        reactionsButtonContainer = new FrameLayout(context);

        reactionsButton = new FrameLayout(context);
        reactionsButton.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 6, 0));
        reactionsButton.setClickable(true);

        reactionsButtonIcon = new ImageView(context);
        reactionsButtonIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
        reactionsButtonIcon.setImageResource(R.drawable.msg_reactions);
        reactionsButton.addView(reactionsButtonIcon, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.FILL_VERTICAL, 11, 10, 6, 9));

        reactionsButtonText = new TextView(context);
        reactionsButtonText.setText("4 Reactions"); // TODO(dkaraush): text!
        reactionsButtonText.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        reactionsButtonText.setTextSize(16);
        reactionsButton.addView(reactionsButtonText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 42, 12, 70, 12));

        reactionsButtonContainer.addView(reactionsButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 8));

        reactionsButtonGap = new FrameLayout(context);
        reactionsButtonGap.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray)); // TODO(dkaraush): shadow
        reactionsButtonContainer.addView(reactionsButtonGap, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 0));

        menu.addView(reactionsButtonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        buttons.setTopButtonSelector(false);
        buttons.updateRadialSelectors();
        menu.addView(buttons);

        container.addView(menu, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        Drawable shadowDrawable = getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        container.setBackgroundDrawable(shadowDrawable);

        reactionButtonList = new ChatMessageReactionList(context);
        reactionButtonList.onReactionClick(reaction -> {
            if (onReactionClick != null)
                onReactionClick.run(reaction);
            // TODO(dkaraush): animation!
            dismiss();
        });
        this.addView(reactionButtonList, LayoutHelper.createFrame(100, 61 + 6, Gravity.TOP | Gravity.RIGHT, 0, 0, 0, 0));
        reactionButtonList.show(false, false);
        postDelayed(this::updateReactionsSelect, 50);

        this.addView(container, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 0, 48, 40, 0));
        reactionButtonList.updateBackground();

        this.setClipChildren(false);
        this.setClipToPadding(false);
    }

    public void dismiss() {
        if (onDismiss != null)
            onDismiss.run();
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

    public interface ReactionCallback {
        void run(TLRPC.TL_availableReaction reaction);
    }

    public class ChatMessageReactionList extends FrameLayout {
        private RecyclerListView listView;
        private ChatMessageReactionListAdapter adapter;
        private LinearLayoutManager layoutManager;

        public void updateBackground() {
            Drawable reactionsContainerDrawable = getResources().getDrawable(adapter.getItemCount() > 5 ? R.drawable.popup_reactions : R.drawable.popup_reactions_small).mutate();
            reactionsContainerDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
            setBackground(reactionsContainerDrawable);

            MarginLayoutParams containerLayoutParams = (MarginLayoutParams) container.getLayoutParams();
            if (containerLayoutParams != null)
                containerLayoutParams.rightMargin = AndroidUtilities.dp(adapter.getItemCount() > 5 ? 40 : 32);
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
                            (int) (Math.max(0f, Math.min(1f, offset / (float) AndroidUtilities.dp(8))) * 255)
                    );
                    canvas.drawArc(first,   90, 180, true, leftGradientPaint);
                    rightGradientPaint.setShader(new LinearGradient(w - h / 2f, 0f, w, 0f, Color.TRANSPARENT, Theme.getColor(Theme.key_dialogBackground), Shader.TileMode.CLAMP));
                    rightGradientPaint.setAlpha(
                            (int) (Math.max(0f, Math.min(1f, (range - offset - extent) / (float) AndroidUtilities.dp(8))) * 255)
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

                ((MarginLayoutParams) imageView.getLayoutParams()).leftMargin = AndroidUtilities.dp(isFirst ? 12 : 4);
                ((MarginLayoutParams) imageView.getLayoutParams()).rightMargin = AndroidUtilities.dp(isLast ? 12 : 4);
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
                        float t = Math.min(1f, Math.max(0f, 1f - (float) Math.max(Math.max(0, l - point2.x), Math.max(0, point2.x + button.getMeasuredWidth() - r)) / (float) AndroidUtilities.dp(32))) * 0.5f + 0.5f;
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
            if (shown == value) {
                animated = false;
            }

            if (!animated) {
                if (showAnimator != null) {
                    showAnimator.cancel();
                    showAnimator = null;
                }
                this.setAlpha(value ? 1f : 0f);
                resize(value ? 0f : 1f);
            } else {
                this.setAlpha(value ? 0f : 1f);
                resize(value ? 0f : 1f);
                if (showAnimator != null)
                    showAnimator.cancel();
                float from = showAnimator != null ? ((float) showAnimator.getAnimatedValue()) : 0f;
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
                        shown = value;
                    }
                });
                showAnimator.setDuration(250);
                showAnimator.start();
            }
        }

        private int computeRange() {
            return this.computeRange(adapter.getItemCount());
        }
        private int computeRange(int itemCount) {
            return AndroidUtilities.dp(8 + (4 + 32 + 4) * itemCount + 8 + 8);
        }

        private void resize(float t) {
            int minWidth = computeRange(1) + AndroidUtilities.dp(8);
            int maxWidth = Math.max(minWidth, Math.min(computeRange(), AndroidUtilities.dp(250)));
            ViewGroup.LayoutParams layoutParams = getLayoutParams();
            if (layoutParams != null) {
                layoutParams.width = (int) (minWidth + (float) (maxWidth - minWidth) * t);
                setLayoutParams(layoutParams);
                invalidate();
                adapter.updateItemsScaling();
            }
        }
    }

    private TLRPC.ChatFull chatInfo;
    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        this.chatInfo = chatInfo;
        reactionButtonList.setChatReactions(this.chatInfo != null ? this.chatInfo.available_reactions : null);
        updateReactionsSelect();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.availableReactionsUpdate) {
            ArrayList<TLRPC.TL_availableReaction> reactions = (ArrayList) args[0];
            reactionButtonList.setAllReactions(reactions);
            updateReactionsSelect();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            // TODO(dkaraush): dismiss
        }
        return super.dispatchKeyEvent(event);
    }

    public boolean shouldShowReactionsButton() {
        return true;
    }
    public boolean shouldShowReactionsSelect() {
        return (message != null && DialogObject.isUserDialog(message.getDialogId()) && !message.isOutOwner()) || (chatInfo != null && chatInfo.available_reactions != null && chatInfo.available_reactions.size() > 0);
    }

    public void updateReactionsButton() {

    }

    public void updateReactionsSelect() {
        reactionButtonList.show(shouldShowReactionsSelect(), true);
    }

}
