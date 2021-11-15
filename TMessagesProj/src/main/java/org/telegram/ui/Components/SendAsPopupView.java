package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class SendAsPopupView extends FrameLayout {

    private class SendAsPeerView extends LinearLayout {

        private BackupImageView avatar;
        private BackupImageView newAvatar;
        private FrameLayout avatarContainer;
        private LinearLayout container;
        private AvatarDrawable avatarDrawable = new AvatarDrawable();
        private float checkProgress = 0f;
        private ValueAnimator animator;
        private Paint paint;
        private LinearLayout infoLayout;
        private TextView title;
        private TextView subtitle;

        public BackupImageView takeAvatar() {
            BackupImageView oldAvatar = avatar;
            this.avatar = makeAvatar(getContext(), true);
            return oldAvatar;
        }

        private BackupImageView makeAvatar(Context context, boolean animated) {
            return makeAvatar(context, animated, 350);
        }
        private BackupImageView makeAvatar(Context context, boolean animated, int startDelay) {
            BackupImageView avatar = new BackupImageView(context);
            avatar.setRoundRadius(AndroidUtilities.dp(20));
            if (animated) {
                avatar.setScaleX(0f);
                avatar.setScaleY(0f);
                avatar.setAlpha(0f);
                avatar.animate().alpha(1f).scaleX(isChecked ? disabledAvatarScale : 1.0f).scaleY(isChecked ? disabledAvatarScale : 1.0f).setStartDelay(startDelay).setDuration(250).start();
            }
            if (this.avatarContainer != null)
                this.avatarContainer.addView(avatar, LayoutHelper.createFrame((int) avatarSize, (int) avatarSize));

            avatarDrawable.setInfo(lastChat != null ? lastChat : lastUser);
            avatar.setForUserOrChat(lastChat != null ? lastChat : lastUser, avatarDrawable);
            return avatar;
        }

        public SendAsPeerView(Context context) {
            super(context);

            setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            container = new LinearLayout(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);

                    if (paint != null) {
                        paint.setColor(Theme.getColor(Theme.key_chat_sendAsPanelProfileSelection));
                        float cx = avatarContainer.getLeft() + avatarContainer.getMeasuredWidth() / 2f;
                        float cy = avatarContainer.getTop() + avatarContainer.getMeasuredHeight() / 2f;
                        paint.setAlpha((int) (checkProgress * 255));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(avatarSize / 2f - 5f) + AndroidUtilities.dp(4) * checkProgress, paint);
                    }
                }
            };
            container.setMinimumWidth(AndroidUtilities.dp(240));
            if (Build.VERSION.SDK_INT >= 21) {
                container.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 3));
            }

            avatarContainer = new FrameLayout(getContext());
            avatar = makeAvatar(context,false);
            container.addView(avatarContainer, LayoutHelper.createLinear((int) avatarSize, (int) avatarSize, Gravity.CENTER_VERTICAL | Gravity.LEFT, 14, 0, 13, 0));

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));

            infoLayout = new LinearLayout(context);
            infoLayout.setOrientation(LinearLayout.VERTICAL);

            title = new TextView(context);
            title.setGravity(Gravity.LEFT);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTextColor(Theme.getColor(Theme.key_chat_sendAsPanelProfileTitle));
            title.setLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            infoLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT | Gravity.RIGHT, 0, 0, 0, 0));

            subtitle = new TextView(context);
            subtitle.setGravity(Gravity.LEFT);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(Theme.getColor(Theme.key_chat_sendAsPanelProfileSubtitle));
            subtitle.setLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            infoLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM | Gravity.RIGHT, 0, 1, 0, 0));

            container.addView(infoLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1,Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 9, 18, 9));

            addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        public void updateColors() {
            title.setTextColor(Theme.getColor(Theme.key_chat_sendAsPanelProfileTitle));
            subtitle.setTextColor(Theme.getColor(Theme.key_chat_sendAsPanelProfileSubtitle));
            invalidate();
            container.invalidate();
        }

        private final float avatarSize = 38f;
        private final float disabledAvatarScale = (avatarSize - 8f) / avatarSize;
        public boolean isChecked = false;
        public void setChecked(boolean checked, boolean animated) {
            if (!animated) {
                isChecked = checked;
                checkProgress = checked ? 1f : 0f;
                if (avatar != null) {
                    avatar.setScaleX(isChecked ? disabledAvatarScale : 1.0f);
                    avatar.setScaleY(isChecked ? disabledAvatarScale : 1.0f);
                }
            } else  {
                if (isChecked == checked) {
                    return;
                }
                isChecked = checked;

                if (animator != null) {
                    animator.cancel();
                }
                animator = ValueAnimator.ofFloat(0.0f, 1.0f);
                animator.addUpdateListener(animation -> {
                    float t = (float) animation.getAnimatedValue();
                    checkProgress = isChecked ? t : 1f - t;
                    container.invalidate();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animator = null;
                    }
                });
                animator.setDuration(180);
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                animator.start();

                if (avatar != null) {
                    if (avatar.getAnimation() != null)
                        avatar.getAnimation().cancel();
                    avatar.animate()
                        .scaleX(isChecked ? disabledAvatarScale : 1f)
                        .scaleY(isChecked ? disabledAvatarScale : 1f)
                        .alpha(1f)
                        .setDuration(180)
                        .start();
                }
            }
        }

        private TLRPC.User lastUser = null;
        private TLRPC.Chat lastChat = null;
        private TLRPC.Peer currentPeer = null;
        public void setFromPeer(TLRPC.Peer peer) {
            if (MessageObject.getPeerId(peer) != MessageObject.getPeerId(currentPeer)) {
                currentPeer = peer;
                if (peer instanceof TLRPC.TL_peerUser) {
                    TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(peer.user_id);
                    if (user != null) {
                        title.setText(ContactsController.formatName(user.first_name, user.last_name));
                        subtitle.setText(LocaleController.getString("MessageSendAsPersonalAccount", R.string.MessageSendAsPersonalAccount));
                        avatarDrawable.setInfo(user);
                        avatar.setForUserOrChat(user, avatarDrawable);

                        lastUser = user;
                        lastChat = null;
                    }
                } else {
                    long id = (
                            peer instanceof TLRPC.TL_peerChat ?
                                    peer.chat_id : peer.channel_id
                    );
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(id);
                    if (chat != null) {
                        title.setText(chat.title);
                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                            subtitle.setText(LocaleController.formatPluralString("Subscribers", chat.participants_count));
                        } else {
                            subtitle.setText(LocaleController.formatPluralString("Members", chat.participants_count));
                        }
                        avatarDrawable.setInfo(chat);
                        avatar.setForUserOrChat(chat, avatarDrawable);

                        lastUser = null;
                        lastChat = chat;
                    }
                }
                invalidate();
            }
        }
    }

    private class PeersAdapter extends RecyclerListView.SelectionAdapter {
        private Context context;
        private ArrayList<TLRPC.Peer> peers = new ArrayList<TLRPC.Peer>();
        public PeersAdapter(Context context) {
            this.context = context;
        }

        public void setPeers(ArrayList<TLRPC.Peer> peers) {
            if (this.peers == peers)
                return;
            this.peers = peers;
            this.notifyDataSetChanged();
        }

        private long selectedPeerId = 0;
        public void selectPeer(TLRPC.Peer peer) {
            if (peers == null)
                return;
            for (int i = 0; i < peers.size(); ++i) {
                boolean shouldHaveBeenSelected = MessageObject.getPeerId(peers.get(i)) == selectedPeerId,
                        shouldBeSelectedNow = MessageObject.getPeerId(peers.get(i)) == MessageObject.getPeerId(peer);
                if (shouldBeSelectedNow != shouldHaveBeenSelected)
                    notifyItemChanged(i);
            }
            selectedPeerId = MessageObject.getPeerId(peer);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new SendAsPeerView(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SendAsPeerView view = (SendAsPeerView) holder.itemView;
            TLRPC.Peer peer = this.peers.get(position);
            view.setFromPeer(peer);
            view.setChecked(MessageObject.getPeerId(peer) == selectedPeerId, true);

            ((MarginLayoutParams) view.getLayoutParams()).topMargin = position == 0 ? AndroidUtilities.dp(-2) : 0;
            ((MarginLayoutParams) view.getLayoutParams()).bottomMargin = position == getItemCount() - 1 ? AndroidUtilities.dp(10) : 0;
        }

        @Override
        public int getItemCount() {
            return peers.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return MessageObject.getPeerId(peers.get(holder.getAdapterPosition())) == selectedPeerId;
        }
    }

    public LinearLayout popup;
    public FrameLayout popupContainer;
    public TextView header;
    public ScrollView scrollView;
    public RecyclerListView listView;
    public PeersAdapter adapter;
    public LinearLayoutManager layoutManager;

    float popupBackgroundScaleX = 1f,
          popupBackgroundScaleY = 1f,
          popupBackgroundOffset = 0f;

    public interface SendAsPopupViewDelegate {
        default void onClose() {}
        default void onSelect(TLRPC.Peer peer) {}
    }
    Rect boxPadding = new Rect(
            AndroidUtilities.dp(6),
            AndroidUtilities.dp(6),
            AndroidUtilities.dp(6),
            AndroidUtilities.dp(6)
    );
    Rect boxClipPadding = new Rect(
            AndroidUtilities.dp(8),
            AndroidUtilities.dp(8),
            AndroidUtilities.dp(8),
            AndroidUtilities.dp(8)
    );
    Point boxOffset = new Point(
            AndroidUtilities.dp(-2f),
            AndroidUtilities.dp(0)
    );

    FrameLayout globalAnimationContainer;
    SendAsAvatarButton sendAsButton;
    public SendAsPopupView(Context context, FrameLayout sendAsPopupAnimationContainer, SendAsAvatarButton sendAsButton, SendAsPopupViewDelegate delegate) {
        super(context);

        this.sendAsButton = sendAsButton;
        this.globalAnimationContainer = sendAsPopupAnimationContainer;

        setVisibility(View.GONE);
        setBackgroundColor(0x33000000);
        setAlpha(0);
        setOnClickListener((v) -> {
            this.show(false);
            if (delegate != null) {
                delegate.onClose();
            }
        });

        Drawable shadowDrawable2 = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert).mutate();
        shadowDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));

        popupContainer = new FrameLayout(context)
        {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int heightMode = MeasureSpec.getMode(heightMeasureSpec),
                    height = MeasureSpec.getSize(heightMeasureSpec);
                if (heightMode == MeasureSpec.EXACTLY)
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(height, AndroidUtilities.dp(420)), MeasureSpec.EXACTLY);
                else
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(420), MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                int w = this.getMeasuredWidth(),
                    h = this.getMeasuredHeight(),
                    sw = (int) (w * popupBackgroundScaleX),
                    sh = (int) (h * popupBackgroundScaleY);

                shadowDrawable2.setBounds(
                        (int) (0 + boxOffset.x - popupBackgroundOffset),
                        (int) ((h - sh) + boxOffset.y + popupBackgroundOffset),
                        (int) (sw + boxOffset.x - popupBackgroundOffset),
                        (int) (h + boxOffset.y + popupBackgroundOffset)
                );
                shadowDrawable2.draw(canvas);

                canvas.clipRect(
                        0 + boxClipPadding.left + boxOffset.x - popupBackgroundOffset,
                        (h - sh) + boxClipPadding.top + boxOffset.y + popupBackgroundOffset,
                        sw - boxClipPadding.right + boxOffset.x - popupBackgroundOffset,
                        h - boxClipPadding.bottom + boxOffset.y + popupBackgroundOffset
                );
            }
//
//            @Override
//            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
//                int w = this.getMeasuredWidth(),
//                        h = this.getMeasuredHeight(),
//                        sw = (int) (w * popupBackgroundScaleX),
//                        sh = (int) (h * popupBackgroundScaleY);
//                return super.drawChild(canvas, child, drawingTime);
//            }
        };
        popupContainer.setWillNotDraw(false);
        popupContainer.setClipChildren(true);

        popup = new LinearLayout(context);
        popup.setOrientation(LinearLayout.VERTICAL);
        popup.setPadding(boxPadding.left, boxPadding.top, boxPadding.right, boxPadding.bottom);
//        popup.setBackground(shadowDrawable2);
//        popup.setTranslationY(24);
        popup.setClickable(true);
        popupContainer.addView(popup, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));

        header = new TextView(context);
        header.setGravity(Gravity.LEFT);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        header.setTextColor(Theme.getColor(Theme.key_chat_sendAsPanelTitle));
        header.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        header.setText(LocaleController.getString("MessageSendAs", R.string.MessageSendAs));
        header.setMaxLines(1);
        popup.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16 - 2, 16, 16 - 2, 8));

        headerShadowContainer = new FrameLayout(context);
        headerShadowContainer.setAlpha(0f);
        headerShadowContainer.setBackground(getResources().getDrawable(R.drawable.header_shadow));
        popup.addView(headerShadowContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

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
        };
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateHeaderShadowShow(recyclerView.computeVerticalScrollOffset() > 10f);
            }
        });
        listView.setAdapter(adapter = new PeersAdapter(context));
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setOnItemClickListener((view, position) -> {
            SendAsPeerView item = (SendAsPeerView) view;
            TLRPC.Peer peer = adapter.peers.get(position);
            if (peer != null) {
//                if (item != null)
//                    item.setChecked(true, true);
//                for (int a = 0, N = listView.getChildCount(); a < N; a++) {
//                    View child = listView.getChildAt(a);
//                    if (child != view) {
//                        ((SendAsPeerView) child).setChecked(false, true);
//                    }
//                }

                this.setSelected(peer, item);
                if (delegate != null) {
                    delegate.onSelect(peer);
                }
            }
        });
        popup.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, -4, 0, 0));

        addView(popupContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
    }

    private ValueAnimator avatarAnimator = null;
    private float avatarT = -1f;
    private Bitmap avatar = null;
    private float avatarFromX = 0f, avatarFromY = 0f, avatarFromRadius = 0f;
    private float avatarToX = 0f, avatarToY = 0f, avatarToRadius = 0f;
    private Paint avatarPaint = new Paint();
    public boolean onGlobalDraw(Canvas canvas) {

        if (avatarT >= 0f && avatar != null) {
            float x = avatarFromX + (avatarToX - avatarFromX) * avatarT,
                  y = avatarFromY + (avatarToY - avatarFromY) * avatarT,
                  r = avatarFromRadius + (avatarToRadius - avatarFromRadius) * avatarT;
            canvas.drawBitmap(
                    avatar,
                    null,
                    new Rect(
                            (int) (x - globalAnimationContainer.getLeft()),
                            (int) (y - globalAnimationContainer.getTop()),
                            (int) (x - globalAnimationContainer.getLeft() + r * 2f),
                            (int) (y - globalAnimationContainer.getTop() + r * 2f)
                    ),
                    avatarPaint
            );
        }

        return false;
    }
    public void moveAvatar(BackupImageView avatar, SendAsAvatarButton sendAsAvatarButton) {
        if (this.globalAnimationContainer == null)
            return;

        if (avatarAnimator != null)
            avatarAnimator.cancel();

        globalAnimationContainer.setVisibility(View.VISIBLE);

        Rect avatarGlobalRect = new Rect();
        Point avatarGlobalOffset = new Point();

        avatar.getGlobalVisibleRect(avatarGlobalRect, avatarGlobalOffset);

        avatarPaint.setColor(0xffffffff);
        avatarFromRadius = Math.max(avatar.getWidth(), avatar.getHeight()) / 2f;
        avatarFromX = avatarGlobalOffset.x + avatar.getWidth() / 2f;
        avatarFromY = avatarGlobalOffset.y + avatar.getHeight() / 2f;

        Bitmap bitmap = Bitmap.createBitmap((int) (avatarFromRadius * 2), (int) (avatarFromRadius * 2), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        avatar.draw(canvas);
        this.avatar = bitmap;

        try {
            ((ViewGroup) avatar.getParent()).removeView(avatar);
        } catch (Exception e) {}



        sendAsAvatarButton.getGlobalVisibleRect(avatarGlobalRect, avatarGlobalOffset);
        avatarToRadius = sendAsAvatarButton.getAvatarRadius();
        avatarToX = avatarGlobalOffset.x + sendAsAvatarButton.getWidth() / 2f - avatarToRadius;
        avatarToY = avatarGlobalOffset.y + sendAsAvatarButton.getHeight() / 2f - avatarToRadius;

        avatarAnimator = ObjectAnimator.ofFloat(0f, 1f);
        avatarAnimator.addUpdateListener(valueAnimator -> {
            float t = (float) valueAnimator.getAnimatedValue();
            avatarT = easeOutBack(t);
            if (globalAnimationContainer != null)
                globalAnimationContainer.invalidate();
        });
        sendAsAvatarButton.avatar.setVisibility(View.INVISIBLE);
        avatarAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                sendAsAvatarButton.avatar.setVisibility(View.VISIBLE);
                super.onAnimationEnd(animation);
                globalAnimationContainer.setVisibility(View.GONE);
                avatarT = -1f;
            }
        });
        float dist = (float) Math.sqrt(Math.pow(avatarFromX - avatarToX, 2) + Math.pow(avatarFromY - avatarToY, 2));
        avatarAnimator.setDuration((int) (75 + dist / AndroidUtilities.dp(16) * 20));
        avatarAnimator.start();
    }

    private FrameLayout headerShadowContainer;
    private ViewPropertyAnimator headerShadowAnimator;
    private boolean headerShadowShow = false;
    private void updateHeaderShadowShow(boolean show) {
        if (headerShadowShow == show)
            return;

        headerShadowShow = show;

        if (headerShadowAnimator != null)
            headerShadowAnimator.cancel();

        headerShadowAnimator = headerShadowContainer.animate().alpha(show ? 1f : 0f).setDuration(150);
    }

    public void updateColors() {
        if (listView != null && layoutManager != null) {
            final int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
            final int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();
            if (firstVisibleItemPosition != RecyclerView.NO_POSITION &&
                lastVisibleItemPosition != RecyclerView.NO_POSITION) {
                for (int i = firstVisibleItemPosition; i <= lastVisibleItemPosition; ++i) {
                    try {
                        RecyclerView.ViewHolder holder = (RecyclerView.ViewHolder) listView.findViewHolderForAdapterPosition(i);
                        if (holder != null) {
                            SendAsPeerView view = (SendAsPeerView) holder.itemView;
                            if (view != null) {
                                view.updateColors();
                            }
                        }
                    } catch (Exception e) {}
                }
            }
        }
        header.setTextColor(Theme.getColor(Theme.key_chat_sendAsPanelTitle));
        invalidate();
    }

    private ValueAnimator animator;

    private float easeInOut(float t) {
        float sqt = t * t;
        return sqt / (2f * (sqt - t) + 1f);
    }
    private final float easeOutBack_G = 1.15f; // 1.70158f;
    private float easeOutBack(float t) {
        return 1f + (1f + easeOutBack_G) * ((float) Math.pow(t - 1f, 3f)) + easeOutBack_G * ((float) Math.pow(t - 1f, 2f));
    }
    private float easeOutBackInverse(float t) {
        return 1f - easeOutBack(1f - t);
    }
    private float easeOutQuint(float t) {
        return 1f - (float) Math.pow(1 - t, 5);
    }
    private float easeOutSine(float t) {
        return (float) Math.sin((t * Math.PI) / 2f);
    }
    private float easeOutCirc(float t) {
        return (float) Math.sqrt(1 - Math.pow(t - 1, 2));
    }

    private boolean shown = false;
    public void show(boolean show) {
        this.show(show, null);
    }
    public void show(boolean show, SendAsPeerView item) {
        if (shown != show) {
            if (show && !hasPeers) {
                shouldHadBeenShown = true;
                return;
            }

            shown = show;
            if (animator != null)
                animator.cancel();

            animator = ObjectAnimator.ofFloat(0f, 1f);
            if (!show) {
                if (item != null) {
                    this.moveAvatar(item.takeAvatar(), sendAsButton);
                }
                this.postDelayed(() -> {
                    if (animator != null)
                        animator.cancel();

                    SendAsPopupView me = this;
                    animator.setInterpolator(new DecelerateInterpolator(2f));
                    animator.addUpdateListener(valueAnimator -> {
                        float traw = (float) valueAnimator.getAnimatedValue();
                        float t = easeOutSine(traw);

                        popupBackgroundScaleX = 0.4f + 0.6f * (1f - t);
                        popupBackgroundScaleY = 0.2f + 0.8f * (1f - t);
//                        popupBackgroundOffset = easeOutBack(t) * AndroidUtilities.dp(4);

                        int outerHeight = popupContainer.getMeasuredHeight(),
                            innerHeight = outerHeight - popup.getPaddingTop();

                        this.setAlpha(1f - traw);
    //                    popup.setTranslationY(24f * t);
                        popup.setTranslationY(popupContainer.getMeasuredHeight() - (popupContainer.getMeasuredHeight() * popupBackgroundScaleY));
                        popupContainer.invalidate();
                    });
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            me.setVisibility(View.GONE);
                        }
                    });
                    animator.setDuration(250);
                    animator.start();
                }, avatar == null ? 0 : 95);
            } else {
                this.setVisibility(View.VISIBLE);
                animator.setInterpolator(new DecelerateInterpolator(2f));
                animator.addUpdateListener(valueAnimator -> {
                    float traw = (float) valueAnimator.getAnimatedValue();
                    float t = easeOutSine(traw);

                    popupBackgroundScaleX = 0.4f + 0.6f * t;
                    popupBackgroundScaleY = 0.2f + 0.8f * t;
//                    popupBackgroundOffset = easeOutBack(t) * AndroidUtilities.dp(4);

                    this.setAlpha(traw);
//                    popup.setTranslationY(24f * (1f - t));
                    popup.setTranslationY(popupContainer.getMeasuredHeight() - (popupContainer.getMeasuredHeight() * popupBackgroundScaleY));
                    popupContainer.invalidate();
                });
                animator.setDuration(300);
                animator.start();
            }
        }
    }

    public void setSelected(TLRPC.Peer peer) {
        setSelected(peer, null);
    }
    public void setSelected(TLRPC.Peer peer, SendAsPeerView item) {
        adapter.selectPeer(peer);

        if (item != null) {
            this.postDelayed(() -> this.show(false, item), 45);
        } else if (peer == null)
            this.show(false);
    }

    private boolean shouldHadBeenShown = false;
    private boolean hasPeers = false;
    public void setPeers(ArrayList<TLRPC.Peer> peers) {
        hasPeers = peers != null;
        if (hasPeers) {
            adapter.setPeers(peers);
            if (shouldHadBeenShown) {
                shouldHadBeenShown = false;
                this.show(true);
            }
        } else {
            if (shown) {
                this.show(false);
                shouldHadBeenShown = true;
            }
        }
    }
}
