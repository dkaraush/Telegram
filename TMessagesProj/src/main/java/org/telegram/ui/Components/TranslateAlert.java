package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
    private LoadingTextView subtitleView;
    private LinearLayout contentView;
    private LoadingTextView textView;
    private TextView buttonTextView;
    private FrameLayout buttonView;

//    public boolean onContainerTouchEvent(MotionEvent event) {
//        return container.onTouchEvent(event);
//    }

    private String fromLanguage, toLanguage, text, translated = null;
    public TranslateAlert(Context context, String fromLanguage, String toLanguage, String text) {
        super(context, true);

        this.fromLanguage = fromLanguage != null && fromLanguage.equals("und") ? "auto" : fromLanguage;
        this.toLanguage = toLanguage;
        this.text = text;

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        NestedScrollView scrollView = new NestedScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(heightMeasureSpec), dp(450)), MeasureSpec.AT_MOST));
            }
        };
        scrollView.addView(container);
        setCustomView(scrollView);

        contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setPadding(dp(20), dp(7), dp(20), dp(26));

        titleView = new TextView(context);
        titleView.setLines(1);
        titleView.setText(LocaleController.getString("AutomaticTranslation", R.string.AutomaticTranslation));
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        contentView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 5.3f));

        LocaleController.LocaleInfo from = LocaleController.getInstance().getLanguageByPlural(fromLanguage);
        LocaleController.LocaleInfo to = LocaleController.getInstance().getLanguageByPlural(toLanguage);
        String subtitleText = LocaleController.formatString("FromLanguageToLanguage", R.string.FromLanguageToLanguage, (from != null ? from.nameEnglish : ""), (to != null ? to.nameEnglish : ""));
        subtitleView = new LoadingTextView(context, subtitleText);
        subtitleView.showLoadingText(false);
        subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        subtitleView.setLines(1);
        if (from != null && to != null)
            subtitleView.setText(subtitleText);
        subtitleView.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        contentView.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        textView = new LoadingTextView(context, text);
        textView.setLines(0);
        textView.setMaxLines(0);
        textView.setSingleLine(false);
        textView.setEllipsizeNull();
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        contentView.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

//        contentScrollView.addView(contentView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        container.addView(contentView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        buttonTextView = new TextView(context);
        buttonTextView.setLines(1);
        buttonTextView.setSingleLine(true);
        buttonTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        buttonTextView.setEllipsize(TextUtils.TruncateAt.END);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setText(LocaleController.getString("CloseTranslation", R.string.CloseTranslation));

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        containerView.setPadding(containerView.getPaddingLeft(), dp(10), containerView.getPaddingRight(), 0);
    }

    public void updateTranslatedText() {
        if (translated != null)
            textView.setText(translated);
        LocaleController.LocaleInfo from = LocaleController.getInstance().getLanguageByPlural(fromLanguage);
        LocaleController.LocaleInfo to = LocaleController.getInstance().getLanguageByPlural(toLanguage);
        if (from != null && to != null)
            subtitleView.setText(LocaleController.formatString("FromLanguageToLanguage", R.string.FromLanguageToLanguage, from.nameEnglish, to.nameEnglish));
    }

    private String[] userAgents = new String[] {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36", // 13.5%
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36", // 6.6%
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:94.0) Gecko/20100101 Firefox/94.0", // 6.4%
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:95.0) Gecko/20100101 Firefox/95.0", // 6.2%
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.93 Safari/537.36", // 5.2%
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.55 Safari/537.36" // 4.8%
    };
    private void fetchTranslation() {
        new Thread() {
            @Override
            public void run() {
                long start = SystemClock.elapsedRealtime();
                HttpURLConnection connection = null;
                try {
                    String uri = "https://translate.googleapis.com/";
                    uri += "translate_a";
                    uri += "/single?client=gtx&sl=" + Uri.encode(fromLanguage) + "&tl=" + Uri.encode(toLanguage) + "&dt=t&q=" + Uri.encode(text) + "&ie=UTF-8&oe=UTF-8&otf=1&ssel=0&tsel=0&kc=7&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss";
                    connection = (HttpURLConnection) new URI(uri).toURL().openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", userAgents[(int) Math.round(Math.random() * (userAgents.length - 1))]);
                    connection.getInputStream();

                    StringBuilder textBuilder = new StringBuilder();
                    try (Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charset.forName("UTF-8")))) {
                        int c = 0;
                        while ((c = reader.read()) != -1) {
                            textBuilder.append((char) c);
                        }
                    }
                    String jsonString = textBuilder.toString();

                    JSONTokener tokener = new JSONTokener(jsonString);
                    JSONArray array = new JSONArray(tokener);
                    JSONArray array1 = array.getJSONArray(0);
                    try {
                        if (!subtitleView.loaded)
                            fromLanguage = array.getString(2);
                    } catch (Exception e2) {}
                    translated = "";
                    for (int i = 0; i < array1.length(); ++i) {
                        String blockText = array1.getJSONArray(i).getString(0);
                        if (blockText != null && !blockText.equals("null"))
                            translated += /*(i > 0 ? "\n" : "") +*/ blockText;
                    }
                    AndroidUtilities.runOnUIThread(TranslateAlert.this::updateTranslatedText);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("translate", "failed to translate a text");

                    try {
                        if (connection != null && connection.getResponseCode() == 429) {
                            long elapsed = SystemClock.elapsedRealtime() - start;
                            if (elapsed < 750)
                                Thread.sleep(750 - elapsed);
                            AndroidUtilities.runOnUIThread(() -> {
                                Toast.makeText(getContext(), LocaleController.getString("TranslationFailedAlert1", R.string.TranslationFailedAlert1), Toast.LENGTH_SHORT).show();
                            });
                        } else if (connection != null) {
                            Toast.makeText(getContext(), LocaleController.getString("TranslationFailedAlert2", R.string.TranslationFailedAlert2), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e2) {}

                    AndroidUtilities.runOnUIThread(TranslateAlert.this::dismiss);
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

    private class LoadingTextView extends FrameLayout {
        private TextView loadingTextView;
        public TextView textView;

        private String loadingString;
        private StaticLayout loadingLayout;
        private Paint loadingPaint = new Paint();
        private Path loadingPath = new Path();
        private RectF fetchedPathRect = new RectF();
        private Path fetchPath = new Path() {
            private boolean got = false;

            @Override
            public void reset() {
                super.reset();
                got = false;
            }

            @Override
            public void addRect(float left, float top, float right, float bottom, @NonNull Direction dir) {
                if (!got) {
                    fetchedPathRect.set(left, top, right, bottom);
                    got = true;
                }
            }
        };

        public LoadingTextView(Context context, String loadingString) {
            super(context);

            loadingTextView = new TextView(context);
            loadingTextView.setText(this.loadingString = loadingString);
            addView(loadingTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            textView = new TextView(context);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            int c1 = Theme.getColor(Theme.key_dialogBackground),
                c2 = Theme.getColor(Theme.key_dialogBackgroundGray);
            LinearGradient gradient = new LinearGradient(0, 0, gradientWidth, 0, new int[]{ c1, c2, c1 }, new float[] { 0, 0.67f, 1f }, Shader.TileMode.REPEAT);
            loadingPaint.setShader(gradient);

            setWillNotDraw(false);
            updateHeight();

            updateLoadingLayout();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();

            updateLoadingLayout();
            updateHeight();
        }

        private void updateHeight() {
            int height = (
                (int) (
                    loadingTextView.getMeasuredHeight() + (
                        textView.getMeasuredHeight() -
                        loadingTextView.getMeasuredHeight()
                    ) * loadingT
                )
            );
            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) getLayoutParams();
            if (params == null)
                params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
            params.height = height;
            this.setLayoutParams(params);
        }

        private float gradientWidth = dp(350f);
        private void updateLoadingLayout() {
            float width = getMeasuredWidth();
            if (width > 0) {
                loadingLayout = new StaticLayout(loadingString, loadingTextView.getPaint(), getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
                loadingPath.reset();
                for (int i = 0; i < loadingLayout.getLineCount(); ++i) {
                    int start = loadingLayout.getLineStart(i), end = loadingLayout.getLineEnd(i);
                    if (start == end + 1)
                        continue;
                    loadingLayout.getSelectionPath(start, end, fetchPath);
                    loadingPath.addRoundRect(fetchedPathRect, dp(1), dp(1), Path.Direction.CW);
                }
            }

            if (!loaded && loadingAnimator == null) {
                loadingAnimator = ValueAnimator.ofFloat(0f, 1f);
                loadingAnimator.addUpdateListener(a -> {
                    loadingT = 0f;
                    invalidate();
                });
                loadingAnimator.setDuration(Long.MAX_VALUE);
                loadingAnimator.start();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//            float measureHeight = MeasureSpec.getSize(heightMeasureSpec);
//            float loadingHeight = loadingLayout == null ? measureHeight : loadingLayout.getHeight();
//            float height = measureHeight + (loadingHeight - measureHeight) * (1f - loadingT);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            updateLoadingLayout();
        }

        public boolean loaded = false;
        private float loadingT = 0f;
        private ValueAnimator loadingAnimator = null;

        public void setEllipsizeNull() {
            loadingTextView.setEllipsize(null);
            textView.setEllipsize(null);
        }
        public void setSingleLine(boolean singleLine) {
            loadingTextView.setSingleLine(singleLine);
            textView.setSingleLine(singleLine);
        }
        public void setLines(int lines) {
            loadingTextView.setLines(lines);
            textView.setLines(lines);
        }
        public void setGravity(int gravity) {
            loadingTextView.setGravity(gravity);
            textView.setGravity(gravity);
        }
        public void setMaxLines(int maxLines) {
            loadingTextView.setMaxLines(maxLines);
            textView.setMaxLines(maxLines);
        }
        private boolean showLoadingTextValue = true;
        public void showLoadingText(boolean show) {
            showLoadingTextValue = show;
        }
        public void setTextColor(int textColor) {
            loadingTextView.setTextColor(multAlpha(textColor, showLoadingTextValue ? 0.04f : 0f));
            textView.setTextColor(textColor);
        }
        public void setTextSize(int unit, float size) {
            loadingTextView.setTextSize(unit, size);
            textView.setTextSize(unit, size);
        }
        public int multAlpha(int color, float mult) {
            return (color & 0x00ffffff) | ((int) ((color >> 24 & 0xff) * mult) << 24);
        }
        private ValueAnimator animator = null;
        public void setText(CharSequence text) {
            textView.setText(text);

            if (!loaded) {
                loaded = true;
                loadingT = 0f;
                if (loadingAnimator != null) {
                    loadingAnimator.cancel();
                    loadingAnimator = null;
                }
                if (animator != null)
                    animator.cancel();
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.addUpdateListener(a -> {
                    loadingT = (float) a.getAnimatedValue();
                    updateHeight();
                    forceLayout();
                    invalidate();
                });
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                animator.setDuration(220);
                animator.start();
            }
        }

        private long start = SystemClock.elapsedRealtime();
        private Path shadePath = new Path();
        private Path tempPath = new Path();
        private Path inPath = new Path();
        private Path outPath = new Path();
        private RectF rect = new RectF();
        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();

            float cx = LocaleController.isRTL ? Math.max(w / 2f, w - 8f) : Math.min(w / 2f, 8f),
                  cy = Math.min(h / 2f, 8f),
                  R = (float) Math.sqrt(Math.max(
                    Math.max(cx*cx + cy*cy, (w-cx)*(w-cx) + cy*cy),
                    Math.max(cx*cx + (h-cy)*(h-cy), (w-cx)*(w-cx) + (h-cy)*(h-cy))
                  )),
                  r = loadingT * R;
            inPath.reset();
            inPath.addCircle(cx, cy, r, Path.Direction.CW);

//            outPath.reset();
//            outPath.moveTo(0, 0);
//            rect.set(cx - r, cy - r, cx + r, cy + r);
//            outPath.arcTo(rect, 0, 360, false);
//            outPath.lineTo(w, 0);
//            outPath.lineTo(w, h);
//            outPath.lineTo(0, h);
//            outPath.lineTo(0, 0);

            canvas.save();
            canvas.clipPath(inPath, Region.Op.DIFFERENCE);

            loadingPaint.setAlpha((int) ((1f - loadingT) * 255));
            float dx = gradientWidth - (((SystemClock.elapsedRealtime() - start) / 1200f * gradientWidth) % gradientWidth);
            shadePath.reset();
            shadePath.addRect(0, 0, w, h, Path.Direction.CW);

            canvas.clipPath(loadingPath);
            canvas.translate(-dx, 0);
            shadePath.offset(dx, 0f, tempPath);
            canvas.drawPath(tempPath, loadingPaint);
            canvas.translate(dx, 0);

            loadingTextView.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.clipPath(inPath);
            textView.draw(canvas);
            canvas.restore();
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
//            return super.drawChild(canvas, child, drawingTime);
            return false;
        }
    }
}