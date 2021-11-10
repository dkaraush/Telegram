package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.ShareDialogCell;

import java.util.ArrayList;

public class SendAsPopupView extends FrameLayout {

    private class SendAsPeerView extends LinearLayout {

        private BackupImageView avatar;
        private AvatarDrawable avatarDrawable = new AvatarDrawable();
        private float checkProgress;
        private ValueAnimator animator;
        private Paint paint;
        private LinearLayout infoLayout;
        private TextView title;
        private TextView subtitle;

        public SendAsPeerView(Context context) {
            super(context);

            setLayoutParams(
                    LayoutHelper.createLinear(
                            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
                    )
            );
            setMinimumWidth(AndroidUtilities.dp(260));

            avatar = new BackupImageView(context);
            avatar.setRoundRadius(AndroidUtilities.dp(20));
            addView(avatar, LayoutHelper.createLinear((int) avatarSize, (int) avatarSize, Gravity.CENTER_VERTICAL | Gravity.LEFT, 13, 0, 13, 0));

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));

            infoLayout = new LinearLayout(context);
            infoLayout.setOrientation(LinearLayout.VERTICAL);

            title = new TextView(context);
            title.setGravity(Gravity.LEFT);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTextColor(0xff222222); // TODO(dkaraush): color!
            title.setLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            infoLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT | Gravity.RIGHT, 0, 0, 0, 0));

            subtitle = new TextView(context);
            subtitle.setGravity(Gravity.LEFT);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(0xff8a8a8a); // TODO(dkaraush): color!
            subtitle.setLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            infoLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM | Gravity.RIGHT, 0, -2, 0, 0));

            addView(infoLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 9, 13, 9));

            if (Build.VERSION.SDK_INT >= 21) {
                setBackgroundDrawable(Theme.getSelectorDrawable(false));
            }
        }

        private final float avatarSize = 38f;
        private final float disabledAvatarScale = (avatarSize - 4f) / avatarSize;
        private boolean isChecked = false;
        public void setChecked(boolean checked, boolean animated) {
            if (!animated) {
                isChecked = checked;
                checkProgress = checked ? 1f : 0f;
                avatar.setScaleX(isChecked ? disabledAvatarScale : 1.0f);
                avatar.setScaleY(isChecked ? disabledAvatarScale : 1.0f);
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
                    avatar.setScaleX(disabledAvatarScale + (1f - checkProgress) * (1.0f - disabledAvatarScale));
                    avatar.setScaleY(disabledAvatarScale + (1f - checkProgress) * (1.0f - disabledAvatarScale));
                    invalidate();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animator = null;
                    }
                });
                animator.setDuration(100);
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                animator.start();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            paint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
            float cx = avatar.getLeft() + avatar.getMeasuredWidth() / 2;
            float cy = avatar.getTop() + avatar.getMeasuredHeight() / 2;

            canvas.drawCircle(cx, cy, AndroidUtilities.dp(avatarSize / 2f - 3f) + AndroidUtilities.dp(4) * checkProgress, paint);
        }

        public void setFromPeer(TLRPC.Peer peer, boolean checked) {
            setChecked(checked, false);
            if (peer instanceof TLRPC.TL_peerUser) {
                TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(peer.user_id);
                if (user != null) {
                    title.setText(ContactsController.formatName(user.first_name, user.last_name));
                    subtitle.setText(LocaleController.getString("MessageSendAsPersonalAccount", R.string.MessageSendAsPersonalAccount));
                    avatarDrawable.setInfo(user);
                    avatar.setForUserOrChat(user, avatarDrawable);
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
                }
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
            this.peers = peers;
            this.notifyDataSetChanged();
        }

        private long selectedPeerId = 0;
        public void selectPeer(TLRPC.Peer peer) {
            selectedPeerId = MessageObject.getPeerId(peer);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            SendAsPeerView view = new SendAsPeerView(context);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SendAsPeerView view = (SendAsPeerView) holder.itemView;
            TLRPC.Peer peer = this.peers.get(position);
            view.setFromPeer(peer, MessageObject.getPeerId(peer) == selectedPeerId);

            ((MarginLayoutParams) view.getLayoutParams()).topMargin = position == 0 ? AndroidUtilities.dp(2) : 0;
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
    public TextView header;
    public ScrollView scrollView;
    public RecyclerListView listView;
    public PeersAdapter adapter;

    public interface SendAsPopupViewDelegate {
        default void onClose() {}
        default void onSelect(TLRPC.Peer peer) {}
    }
    public SendAsPopupView(Context context, SendAsPopupViewDelegate delegate) {
        super(context);

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

        popup = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(420), MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        popup.setOrientation(LinearLayout.VERTICAL);
        popup.setBackground(shadowDrawable2);
        popup.setTranslationX(8);
        popup.setClickable(true);

        header = new TextView(context);
        header.setGravity(Gravity.LEFT);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        header.setTextColor(0xff3995D4);
        header.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        header.setText(LocaleController.getString("MessageSendAs", R.string.MessageSendAs));
        header.setMaxLines(1);
        popup.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 14, 16, 8));

        listView = new RecyclerListView(context);
        listView.setAdapter(adapter = new PeersAdapter(context));
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
//        listView.setPadding(0, 2, 0, 10);
        listView.setOnItemClickListener((view, position) -> {
            TLRPC.Peer peer = adapter.peers.get(position);
            if (peer != null) {
                if (delegate != null) {
                    delegate.onSelect(peer);
                }
                this.setSelected(peer);

                ((SendAsPeerView) view).setChecked(true, true);
                for (int a = 0, N = listView.getChildCount(); a < N; a++) {
                    View child = listView.getChildAt(a);
                    if (child != view) {
                        ((SendAsPeerView) child).setChecked(false, true);
                    }
                }
            }
        });
        popup.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        addView(popup, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, -5, 0, 0, -2));
    }

    private ViewPropertyAnimator popupAnimation;
    private ViewPropertyAnimator containerAnimation;
    public void show(boolean enabled) {
        if (containerAnimation != null)
            containerAnimation.cancel();
        if (popupAnimation != null)
            popupAnimation.cancel();

        if (!enabled) {
            containerAnimation = this.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                this.setVisibility(View.GONE);
            });
            popupAnimation = popup.animate().translationY(8).setDuration(150);
        } else {
            this.setVisibility(View.VISIBLE);
            containerAnimation = this.animate().alpha(1f).setDuration(150);
            popupAnimation = popup.animate().translationY(0).setDuration(150);
        }
    }

    public void setSelected(TLRPC.Peer peer) {
        adapter.selectPeer(peer);
    }

    public void setPeers(ArrayList<TLRPC.Peer> peers) {
        // peers == null -> loading
        if (peers != null) {
//            scrollView.setVisibility(View.VISIBLE);
            adapter.setPeers(peers);
        } else {
//            scrollView.setVisibility(View.GONE);
        }
    }
}
