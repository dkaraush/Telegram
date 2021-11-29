package org.telegram.ui.Components;

import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;

public class ChatMessageScrimPopup extends View {



    public ChatMessageScrimPopup(Context context, ActionBarPopupWindow.ActionBarPopupWindowLayout buttons, TLRPC.TL_messageReactions reactions) {
        super(context);



        this.setOnTouchListener(new View.OnTouchListener() {
            private int[] pos = new int[2];

            @Override
            public boolean onTouch (View v, MotionEvent event){
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
//                    if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
//                        View contentView = scrimPopupWindow.getContentView();
//                        contentView.getLocationInWindow(pos);
//                        rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
//                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
//                            // TODO(dkaraush): dismiss
//                        }
//                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
//                    if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                        // TODO(dkaraush): dismiss
//                    }
                }
                return false;
            }
        });
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            // TODO(dkaraush): dismiss
        }
        return super.dispatchKeyEvent(event);
    }

}
