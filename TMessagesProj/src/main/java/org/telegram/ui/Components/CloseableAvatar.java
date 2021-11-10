package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.Shape;
import android.view.Gravity;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class CloseableAvatar extends FrameLayout {

    public boolean showClose = false;
    private RelativeLayout layout;
    private BackupImageView avatar;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private ImageView closeIcon;

    public CloseableAvatar(Context context) {
        this(context, 0);
    }
    public CloseableAvatar(Context context, float padding) {
        super(context);

        layout = new RelativeLayout(context);
        addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, padding, padding, padding, padding));

        ShapeDrawable shape = new ShapeDrawable(new OvalShape());
        shape.getPaint().setColor(Theme.getColor(Theme.key_changephoneinfo_image2)); // TODO(dkaraush): color!
        layout.setBackground(shape);

        avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(20));
        layout.addView(avatar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        avatar.setAlpha(showClose ? 0f : 1f);

        closeIcon = new ImageView(context);
        closeIcon.setImageResource(R.drawable.ic_close_white);
        closeIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        closeIcon.setRotation(90f);
        closeIcon.setAlpha(0f);
        layout.addView(closeIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 4f, 4f, 4f, 4f));
    }

    public void setAvatar(TLRPC.Peer peer) {
        if (peer instanceof TLRPC.TL_peerUser) {
            TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(peer.user_id);
            if (user != null) {
                this.setAvatar(user);
            }
        } else {
            long id = (
                    peer instanceof TLRPC.TL_peerChat ?
                            peer.chat_id : peer.channel_id
            );
            TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(id);
            if (chat != null) {
                this.setAvatar(chat);
            }
        }
    }
    public void setAvatar(TLRPC.User user) {
        avatarDrawable.setInfo(user);
        avatar.setForUserOrChat(user, avatarDrawable);
    }
    public void setAvatar(TLRPC.Chat chat) {
        avatarDrawable.setInfo(chat);
        avatar.setForUserOrChat(chat, avatarDrawable);
    }

    public void toggleShowClose() {
        setShowClose(!showClose);
    }

    private ViewPropertyAnimator closeIconAnimation;
    private ViewPropertyAnimator avatarAnimation;
    public void setShowClose(boolean enabled) {
        boolean toggled = enabled != showClose;
        showClose = enabled;

        if (toggled) {
            if (closeIconAnimation != null)
                closeIconAnimation.cancel();
            if (avatarAnimation != null)
                avatarAnimation.cancel();

            avatarAnimation = avatar.animate().alpha(enabled ? 0f : 1f).setDuration(150);
            closeIconAnimation = closeIcon.animate().rotation(enabled ? 0f : 90f).alpha(enabled ? 1f : 0f).setDuration(150);
        }
    }
}
