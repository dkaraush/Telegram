package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.google.android.gms.vision.Frame;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class TranslateAlert extends BottomSheet {
    private LinearLayout container;
    private TextView titleView;
    private TextView subtitleView;
    private ScrollView contentScrollView;
    private LinearLayout contentView;
    private TextView textView;
    private TextView buttonTextView;
    private FrameLayout buttonView;

//    public boolean onContainerTouchEvent(MotionEvent event) {
//        return container.onTouchEvent(event);
//    }

    private String fromLanguage, toLanguage, text, translated = null;
    public TranslateAlert(Context context, String fromLanguage, String toLanguage, String text) {
        super(context, true);

        this.fromLanguage = fromLanguage;
        this.toLanguage = toLanguage;
        this.text = text;

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        NestedScrollView scrollView = new NestedScrollView(context);
        scrollView.addView(container);
        setCustomView(scrollView);

        contentScrollView = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(250), MeasureSpec.AT_MOST));
            }
        };
        contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setPadding(dp(20), dp(7), dp(20), dp(26));

        titleView = new TextView(context);
        titleView.setLines(1);
        titleView.setText("Automatic translation"); // TODO(dkaraush): text
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        contentView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 5.3f));

        subtitleView = new TextView(context);
        subtitleView.setLines(1);
        LocaleController.LocaleInfo from = LocaleController.getInstance().getLanguageFromDict(fromLanguage); // TODO(dkaraush): it wouldn't find languages with region codes
        LocaleController.LocaleInfo to = LocaleController.getInstance().getLanguageFromDict(toLanguage);
        subtitleView.setText("From " + (from != null ? from.nameEnglish : "") + " to " + (to != null ? to.nameEnglish : "")); // TODO(dkaraush): text
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        contentView.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        textView = new TextView(context);
        textView.setLines(0);
        textView.setMaxLines(0);
        textView.setSingleLine(false);
        textView.setEllipsize(null);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        contentView.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        contentScrollView.addView(contentView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        container.addView(contentScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        buttonTextView = new TextView(context);
        buttonTextView.setLines(1);
        buttonTextView.setSingleLine(true);
        buttonTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        buttonTextView.setEllipsize(TextUtils.TruncateAt.END);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setText("Close Translation"); // TODO(dkaraush): text

        buttonView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
            }
        };
        buttonView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        buttonView.addView(buttonTextView);
        buttonView.setOnClickListener(e -> dismiss());

        container.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 16));

        fetchTranslation();
    }

    public void updateTranslatedText() {
        if (translated != null)
            textView.setText(translated);
    }

    private void fetchTranslation() {
        new Thread() {
            @Override
            public void run() {
                try {
                    String uri = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" + Uri.encode(fromLanguage) + "&tl=" + Uri.encode(toLanguage) + "&dt=t&q=" + Uri.encode(text) + "&ie=UTF-8&oe=UTF-8&otf=1&ssel=0&tsel=0&kc=7&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss";
                    HttpURLConnection connection = (HttpURLConnection) new URI(uri).toURL().openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36 Edg/96.0.1054.62");
                    connection.getInputStream();

                    StringBuilder textBuilder = new StringBuilder();
                    try (Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")))) {
                        int c = 0;
                        while ((c = reader.read()) != -1) {
                            textBuilder.append((char) c);
                        }
                    }
                    JSONTokener tokener = new JSONTokener(textBuilder.toString());
                    JSONArray array = new JSONArray(tokener);
                    JSONArray array1 = array.getJSONArray(0);
                    translated = "";
                    for (int i = 0; i < array1.length(); ++i) {
                        String blockText = array1.getJSONArray(i).getString(0);
                        if (blockText != null && !blockText.equals("null"))
                            translated += (i > 0 ? "\n" : "") + blockText;
                    }
                    AndroidUtilities.runOnUIThread(TranslateAlert.this::updateTranslatedText);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("google translate", "failed to translate a text");
                }
            }
        }.start();
    }

    public static void showAlert(Context context, BaseFragment fragment, String fromLanguage, String toLanguage, String text) {
        TranslateAlert alert = new TranslateAlert(context, fromLanguage, toLanguage, text);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
    }
}
