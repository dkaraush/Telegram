package org.telegram.ui.Components;

import static android.graphics.Color.TRANSPARENT;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.icu.util.Measure;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SpoilerSpan extends TextStyleSpan {
    public static class SpoilerSpanLocation {
        Integer account = null;
        Long dialogId = null;
        Integer msgId = null;
        Integer start = null;
        Integer end = null;
        public SpoilerSpanLocation(Integer account, Long dialogId, Integer msgId) {
            this.account = account;
            this.dialogId = dialogId;
            this.msgId = msgId;
        }
        public SpoilerSpanLocation(Integer account, Long dialogId, Integer msgId, Integer start, Integer end) {
            this.account = account;
            this.dialogId = dialogId;
            this.msgId = msgId;
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SpoilerSpanLocation that = (SpoilerSpanLocation) o;
            return Objects.equals(account, that.account) && Objects.equals(dialogId, that.dialogId) && Objects.equals(msgId, that.msgId) && ((Objects.equals(start, that.start) && Objects.equals(end, that.end)));
        }

        @Override
        public int hashCode() {
            return Objects.hash(account, dialogId, msgId, start, end);
        }
        public SpoilerSpanLocation clone() { return new SpoilerSpanLocation(account, dialogId, msgId, start, end); }
    }

    public SpoilerSpanLocation location;
    public SpoilerSpan(SpoilerSpanLocation location, TextStyleSpan.TextStyleRun style) {
        super(style);
        this.location = location;
    }
    public SpoilerSpan(SpoilerSpanLocation location, TextStyleSpan.TextStyleRun style, int size) {
        super(style, size);
        this.location = location;
    }
    public SpoilerSpan(SpoilerSpanLocation location, TextStyleSpan.TextStyleRun style, int size, int textColor) {
        super(style, size, textColor);
        this.location = location;
    }

    public static void applyLocation(CharSequence text, SpoilerSpanLocation location) {
        if (!(text instanceof Spannable))
            return;
        Spannable spannable = (Spannable) text;
        SpoilerSpan[] spoilers = spannable.getSpans(0, text.length(), SpoilerSpan.class);
        if (spoilers != null)
            for (int i = 0; i < spoilers.length; ++i)
                spoilers[i].location = location;
    }

    private static ArrayList<SpoilerSpan> spoilers = new ArrayList<>();

    boolean transparent = true;
    @Override
    public void updateDrawState(TextPaint p) {
        super.updateDrawState(p);
        p.setAlpha(transparent ? 0 : 255);
    }

    private long stateToggleDuration = 200;
    public boolean haveState = false;
    public boolean state = true;
    private long stateToggled = 0;
    private float pointerX = 0, pointerY = 0;
    public void toggle(float x, float y) {
        pointerX = x;
        pointerY = y;
        state = !state;
        stateToggled = SystemClock.uptimeMillis();
        if (view != null)
            view.updateFor(!state ? stateToggleDuration : Integer.MAX_VALUE);

        for (SpoilerSpan spoiler : spoilers)
            if (spoiler != this && location != null && location.equals(spoiler.location))
                spoiler.setState(state);
    }
    public void setState(boolean value) {
        pointerX = -1;
        pointerY = -1;
        if (state != value) {
            stateToggled = SystemClock.uptimeMillis();
            if (view != null)
                view.updateFor(!value ? stateToggleDuration : Integer.MAX_VALUE);
        }
        state = value;
    }
    private float getSpoiler() {
        float t = Math.min(Math.max(((float) (SystemClock.uptimeMillis() - stateToggled) / (float) stateToggleDuration), 0f), 1f);
        if (!state)
            t = 1f - t;
        return t;
    }

    public static void register(SpoilerSpan spoiler) {
        if (!spoilers.contains(spoiler))
            spoilers.add(spoiler);
    }
    public static void unregister(SpoilerSpan spoiler) {
        if (spoilers.contains(spoiler))
            spoilers.remove(spoiler);
    }

    static class PaddingPath extends Path {

        public Float left = null;
        public Float top = null;
        public Float right = null;
        public Float bottom = null;

        public int vertPadding, horPadding;
        public PaddingPath(int vertPadding, int horPadding) {
            this.vertPadding = vertPadding;
            this.horPadding = horPadding;
            this.reset();
        }

        @Override
        public void reset() {
            super.reset();
            left = null;
            top = null;
            right = null;
            bottom = null;
        }

        @Override
        public void addRect(float left, float top, float right, float bottom, @NonNull Direction dir) {
            left -= horPadding;
            top -= vertPadding;
            right += horPadding;
            bottom += vertPadding;
            super.addRect(left, top, right, bottom, dir);
            this.top = this.top == null ? top : Math.min(this.top, top);
            this.left = this.left == null ? left : Math.min(this.left, left);
            this.right = this.right == null ? right : Math.max(this.right, right);
            this.bottom = this.bottom == null ? bottom : Math.max(this.bottom, bottom);
        }
    }

    private StaticLayout layout = null;
//    private RectF bounds = new RectF();
    private Path path = new Path();
    private PaddingPath paddedPath = new PaddingPath(AndroidUtilities.dp(0), AndroidUtilities.dp(4));
//    private Paint paint = new Paint();
    private SpoilerView view = null;
    private Canvas canvas = null;
    private boolean textBlurBitmapUpdated = false;
    private Bitmap textBitmap = null,
                   textBlurBitmap = null;
    private int particleColor = 0;
    private int textColor = 0;
//    private int backgroundColor = 0;
    private int blurDist = AndroidUtilities.dp(10);
    public int start, end;
    public void draw(
        SpoilerSpanLocation location,
        ViewGroup parent,
        StaticLayout layout,
        int textColor, int particleColor,
        float offsetX,
        float offsetY
    ) {
        this.location = location;
//        this.boundsView = boundsView;
        this.textColor = textColor;
        this.particleColor = particleColor;
//        this.backgroundColor = backgroundColor;
        this.layout = layout;
        CharSequence text = this.layout.getText();
        Spannable spannable = text instanceof Spannable ? ((Spannable) text) : null;
        if (spannable == null)
            return;
        path.reset();
        paddedPath.reset();
        blurDist = AndroidUtilities.dp(8);
        paddedPath.vertPadding = AndroidUtilities.dp(0);
        paddedPath.horPadding = AndroidUtilities.dp(4);
        start = spannable.getSpanStart(this);
      end = spannable.getSpanEnd(this);
        if (this.location != null && (this.location.start == null || this.location.start != start || this.location.end == null || this.location.end != end)) {
            this.location = this.location.clone();
            this.location.start = start;
            this.location.end = end;
        }
        this.layout.getSelectionPath(Math.max(start, 0), Math.min(end, spannable.length() - 1), path);
        this.layout.getSelectionPath(Math.max(start, 0), Math.min(end, spannable.length() - 1), paddedPath);

        if (paddedPath.left != null && paddedPath.top != null && paddedPath.right != null && paddedPath.bottom != null) {
            int left = (int) (float) (paddedPath.left + offsetX),
                top =  (int) (float) (paddedPath.top + offsetY),
                right = (int) (float) (paddedPath.right + offsetX),
                bottom = (int) (float) (paddedPath.bottom + offsetY);
            int width = (int) (paddedPath.right - paddedPath.left),
                height = (int) (paddedPath.bottom - paddedPath.top);
            if (textBitmap == null || textBitmap.getWidth() != width || textBitmap.getHeight() != height) {
                if (textBitmap != null)
                    textBitmap.recycle();
                textBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                if (canvas == null)
                    canvas = new Canvas();
                canvas.setBitmap(textBitmap);
                canvas.translate(offsetX - left, offsetY - top);
                canvas.clipPath(path);
                transparent = false;
                layout.draw(canvas);
                transparent = true;

                SpoilerSpan that = this;
                if (textBlurBitmap != null)
                    textBlurBitmap.eraseColor(TRANSPARENT);
                textBlurBitmap = textBitmap.copy(textBitmap.getConfig(), true);
//                new Thread() {
//                    @Override
//                    public void run() {
//                        Bitmap wasBitmap = textBlurBitmap;
//                        if (that.textBlurBitmapUpdated) {
//                            textBlurBitmap = wasBitmap;
//                            return;
//                        }
//                        textBlurBitmap = textBitmap.copy(textBitmap.getConfig(), true);
                        Utilities.stackBlurBitmap(textBlurBitmap, blurDist);
//                        if (that.textBlurBitmapUpdated) {
//                            textBlurBitmap = wasBitmap;
//                            return;
//                        }
//                        AndroidUtilities.runOnUIThread(() -> {
                        that.textBlurBitmapUpdated = true;
//                            that.textBlurBitmap = textBlurBitmap;
                        if (view != null && view.sview != null) {
                            view.sview.requestLayout();
                            view.sview.requestRender();
                        }
//                        });
//                    }
//                }.start();
            }
        }

//        if (view != null && view.getParent() != parent) {
//            ((ViewGroup) view.getParent()).removeView(view);
//            view = null;
//        }
//        if (view != null && view.getParent() != parent) {
//            if (view.getParent() != null)
//                ((ViewGroup) view.getParent()).removeView(view);
//        }
        if (view == null) {
            haveState = false;
//            for (int i = 0; i < parent.getChildCount(); ++i) {
//                View child = parent.getChildAt(i);
//                if (!(child instanceof SpoilerView))
//                    continue;
//                SpoilerView v = (SpoilerView) child;
//                if (v.span != null && (v.span == this || (v.span != null && location != null && location.equals(v.span.location) && v.span.start == start && v.span.end == end))) {
//                    view = v;
//                    view.setSpan(this);
//                    break;
//                }
//            }
//            if (view == null)
                parent.addView(view = spoilerViewFactory(parent.getContext(), this));
        }
//
//        ViewParent glParent = view.getParent();
//        if (glParent != parent) {
//            if (glParent instanceof ViewGroup)
//                ((ViewGroup) glParent).removeView(view);
//            parent.addView(view);
//        }

        if (paddedPath.left != null && paddedPath.top != null && paddedPath.right != null && paddedPath.bottom != null) {
            int top = (int) (float) (paddedPath.top + offsetY),
                left = (int) (float) (paddedPath.left + offsetX),
                right = (int) (float) (paddedPath.right + offsetX),
                bottom = (int) (float) (paddedPath.bottom + offsetY);
            int width = (int) (paddedPath.right - paddedPath.left),
                height = (int) (paddedPath.bottom - paddedPath.top);

            if (view.getLeft() != left ||
                    view.getTop() != top ||
                    view.getRight() != right ||
                    view.getBottom() != bottom) {
                view.layout(left, top, right, bottom);
            }

            if (view.getMeasuredWidth() != width ||
                view.getMeasuredHeight() != height) {
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                );
//                FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(width, height, Gravity.NO_GRAVITY);
//                view.setLayoutParams(p);
//                p.setMargins(left, top, 0, 0);
            }

        }

        if (!haveState) {
            for (SpoilerSpan spoiler : spoilers)
                if (spoiler != this && location != null && location.equals(spoiler.location) && spoiler.haveState)
                    state = spoiler.state;
            if (state)
                view.updateFor(Integer.MAX_VALUE);
            haveState = true;
        }
    }

    public static void putSpoilers(
        SpoilerSpanLocation location,
        ViewGroup parent,
//        View boundsView,
        StaticLayout layout,
//        int backgroundColor,
        int textColor, int particleColor,
        float offsetX, float offsetY
    ) {
        CharSequence blockText = layout != null ? layout.getText() : null;
        SpoilerSpan[] spoilerSpans = blockText instanceof Spannable ? ((Spannable) blockText).getSpans(0, blockText.length(), SpoilerSpan.class) : null;
        if (parent != null) {
            for (int i = 0; i < parent.getChildCount(); ++i) {
                View child = parent.getChildAt(i);
                if (!(child instanceof SpoilerView))
                    continue;
                if (spoilerSpans == null) {
                    parent.removeView(child);
                    i--;
                } else {
                    boolean found = false;
                    for (int j = 0; !found && j < spoilerSpans.length; ++j) {
                        if (spoilerSpans[j] == ((SpoilerView) child).span)
                            found = true;
                    }
                    if (!found) {
                        parent.removeView(child);
                        i--;
                    }
                }
            }
//            for (int i = 0; i < parent.getChildCount(); ++i) {
//                View child1 = parent.getChildAt(i);
//                if (!(child1 instanceof SpoilerView))
//                    continue;
//                for (int j = i + 1; j < parent.getChildCount(); ++j) {
//                    View child2 = parent.getChildAt(i);
//                    if (!(child2 instanceof SpoilerView))
//                        continue;
//                    if (((SpoilerView) child1).span == ((SpoilerView) child2).span) {
//                        if (((SpoilerView) child1).span != null)
//                            ((SpoilerView) child1).span.view = null;
//                        parent.removeView(child1);
//                        i--;
//                        break;
//                    }
//                }
//            }
        }
//        clearSpoilers(parent);
        if (spoilerSpans != null && spoilerSpans.length > 0) {
            for (SpoilerSpan spoiler : spoilerSpans) {
                if (spoiler == null)
                    continue;
                spoiler.draw(
                    location,
                    parent, // boundsView,
                    layout,
//                    backgroundColor,
                    textColor,
                    particleColor,
                    offsetX, offsetY
                );
            }
        }
    }

    public static void clearSpoilers(ViewGroup parent) {
        if (parent == null)
            return;
        for (int i = 0; i < parent.getChildCount(); ++i) {
            View child = parent.getChildAt(i);
            if (child instanceof SpoilerView) {
                parent.removeViewAt(i--);
            }
        }
    }

    public static SpoilerView spoilerViewFactory(Context context, SpoilerSpan span) {
        return new SpoilerView(context, span);
    }

    public static class SpoilerView extends FrameLayout {
        private GLSurfaceView sview;
        private final AtomicReference<Bitmap> bitmapRef = new AtomicReference<>(null);
        private SpoilerSpan span = null;

        public void setSpan(SpoilerSpan span) {
            unregister(this.span);
            this.span = span;
            if (sview != null)
                sview.requestLayout();
            if (span != null && span.state)
                updateFor(Integer.MAX_VALUE);
            register(this.span);
        }
        boolean attached = false;

        private ValueAnimator animator = null;
        public void updateFor(long ms) {
            if (animator != null) {
                animator.cancel();
//                if (animator.getDuration() <= Integer.MAX_VALUE)
//                    ms = Math.max(ms, animator.getDuration() - (long) animator.getAnimatedValue());
            }
            animator = ValueAnimator.ofInt(0, (int) ms);
            animator.addUpdateListener(a -> {
                if (sview != null && ((int) a.getAnimatedValue()) % 2 == 0)
                    sview.requestRender();
            });
            animator.setDuration(ms);
            animator.start();
        }

        @Override
        protected void onAttachedToWindow() {
            attached = true;
            super.onAttachedToWindow();
            register(this.span);

            if (sview != null)
                sview.requestLayout();
            if (span != null && span.state)
                updateFor(Integer.MAX_VALUE);
        }

        @Override
        protected void onDetachedFromWindow() {
            attached = false;
            super.onDetachedFromWindow();
            synchronized (bitmapRef) {
                if (bitmapRef.get() != null) {
                    bitmapRef.set(null);
                    invalidate();
                }
            }
            if (this.span != null) {
//                clearSpoilers((ViewGroup) this.span.view.getParent());
                this.span.view = null;
            }
            unregister(this.span);
        }

        public SpoilerView(Context context, SpoilerSpan span) {
            super(context);
            this.span = span;

            setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            setWillNotDraw(false);

            FrameLayout parent = this;
            sview = new GLSurfaceView(getContext());
            sview.setZOrderOnTop(false);
            sview.setEGLContextClientVersion(2);
            sview.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
            sview.getHolder().setFormat(PixelFormat.RGBA_8888);
            sview.setAlpha(0);
            sview.setRenderer(new GLSurfaceView.Renderer() {
                private int[] frameBufferIds = new int[1];
                private int[] textureIds = new int[3];
                private int vPosition, u_dpi, u_particle_color, u_pointer, u_bounds, u_spoiler_direction, u_background_color, u_text_color, u_blurdist, u_spoiler, u_time, u_resolution, tex_text, tex_blur;
                private ByteBuffer posBuffer;
                private FloatBuffer posFloatBuffer;

                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
//                    GLES20.glClearColor(
//                        (backgroundColor >> 16 & 0xff) / 255.f,
//                        (backgroundColor >> 8 & 0xff) / 255f,
//                        (backgroundColor & 0xff) / 255f,
//                        (backgroundColor >> 24 & 0xff) / 255f
//                    );
                    GLES20.glClearColor(0,0,0,0);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                    try {
                        GLES20.glUseProgram(program = program == -1 ? makeProgram() : program);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }

                    GLES20.glGenFramebuffers(1, frameBufferIds, 0);
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferIds[0]);

                    vPosition = GLES20.glGetAttribLocation(program, "vPosition");
                    initPositions();

                    u_spoiler_direction = GLES20.glGetUniformLocation(program, "u_spoiler_direction");
                    u_background_color = GLES20.glGetUniformLocation(program, "u_background_color");
                    u_particle_color = GLES20.glGetUniformLocation(program, "u_particle_color");
                    u_text_color = GLES20.glGetUniformLocation(program, "u_text_color");
                    u_resolution = GLES20.glGetUniformLocation(program, "u_resolution");
                    u_blurdist = GLES20.glGetUniformLocation(program, "u_blurdist");
                    u_spoiler = GLES20.glGetUniformLocation(program, "u_spoiler");
                    u_pointer = GLES20.glGetUniformLocation(program, "u_pointer");
//                    u_bounds = GLES20.glGetUniformLocation(program, "u_bounds");
                    tex_text = GLES20.glGetUniformLocation(program, "tex_text");
                    tex_blur = GLES20.glGetUniformLocation(program, "tex_blur");
                    u_time = GLES20.glGetUniformLocation(program, "u_time");
                    u_dpi = GLES20.glGetUniformLocation(program, "u_dpi");

                    GLES20.glHint(GLES20.GL_GENERATE_MIPMAP_HINT, GLES20.GL_NICEST);
                    GLES20.glGenTextures(3, textureIds, 0);

                    if (span.textBitmap != null && !span.textBitmap.isRecycled()) {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, span.textBitmap, 0);
                    }
                    if (span.textBlurBitmap != null && !span.textBlurBitmap.isRecycled()) {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[1]);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, span.textBlurBitmap, 0);
                        span.textBlurBitmapUpdated = false;
                    }

                    GLES20.glUniform1i(tex_text, 0);
                    GLES20.glUniform1i(tex_blur, 1);
                    GLES20.glUniform1f(u_dpi, AndroidUtilities.density);
                    GLES20.glUniform1f(u_blurdist, (float) span.blurDist);
                    glUniformColor(u_particle_color, span.particleColor);
                    glUniformColor(u_text_color, span.textColor);
//                    glUniformColor(u_background_color, span.backgroundColor);

                    lastFrameTime = SystemClock.uptimeMillis();
                }

                private void initPositions() {
                    posBuffer = ByteBuffer.allocateDirect(24 * 4);
                    posBuffer.order(ByteOrder.nativeOrder());
                    posFloatBuffer = posBuffer.asFloatBuffer();
                    posFloatBuffer.put(new float[] {
                            1f,  1f, 0.0f, 1.0f,
                            1f, -1f, 0.0f, 1.0f,
                            -1f, -1f, 0.0f, 1.0f,

                            -1f, 1f, 0.0f, 1.0f,
                            1f, 1f, 0.0f, 1.0f,
                            -1f, -1f, 0.0f, 1.0f
                    });
                    posFloatBuffer.position(0);

                    GLES20.glEnableVertexAttribArray(vPosition);
                    GLES20.glVertexAttribPointer(vPosition, 4, GLES20.GL_FLOAT, false, 0, posFloatBuffer);
                }

                int framebufferWidth = 0, framebufferHeight = 0;
                @Override
                public void onSurfaceChanged(GL10 gl10, int width, int height) {
                    if (framebufferWidth != width || framebufferHeight != height) {
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[2]);
                        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, framebufferWidth = width, framebufferHeight = height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureIds[2], 0);
                    }

                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferIds[0]);
                    GLES20.glViewport(0, 0, width, height);
                    GLES20.glUniform2f(u_resolution, width, height);

                    if (span.textBitmap != null && !span.textBitmap.isRecycled()) {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, span.textBitmap, 0);
                    }
                    if (span.textBlurBitmap != null && !span.textBlurBitmap.isRecycled()) {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[1]);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, span.textBlurBitmap, 0);
                        span.textBlurBitmapUpdated = false;
                    }

                    synchronized (bitmapRef) {
                        Bitmap bitmap = bitmapRef.get();
                        if (bitmap == null || bitmap.getWidth() != framebufferWidth || bitmap.getHeight() != framebufferHeight) {
                            if (bitmap != null)
                                bitmap.eraseColor(TRANSPARENT);
                            bitmapRef.set(Bitmap.createBitmap(framebufferWidth, framebufferHeight, Bitmap.Config.ARGB_8888));
                        }
                    }
                    int framebufferLength = framebufferWidth * framebufferHeight * 4;
                    if (framebuffer == null || framebuffer.limit() != framebufferLength) {
                        framebuffer = ByteBuffer.allocateDirect(framebufferLength);
                    }

                    GLES20.glUniform1f(u_dpi, AndroidUtilities.density);
                    GLES20.glUniform1f(u_blurdist, (float) span.blurDist);
                    glUniformColor(u_particle_color, span.particleColor);
                    glUniformColor(u_text_color, span.textColor);
//                    glUniformColor(u_background_color, span.backgroundColor);

                    GLES20.glClearColor(0,0,0,0);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                }

                private void glUniformColor(int location, int color) {
                    GLES20.glUniform4f(
                            location,
                            (color >> 16 & 0xff) / 255.f,
                            (color >> 8 & 0xff) / 255f,
                            (color & 0xff) / 255f,
                            (color >> 24 & 0xff) / 255f
                    );
                }

                //                private Rect drawingRect = new Rect();
//                private Rect visibleRect = new Rect();
//                private Point visibleOffset = new Point();
//                private Rect boundsViewVisibleRect = new Rect();
//                private Point boundsViewVisibleOffset = new Point();
                private long lastFrameTime;
                private long minFrameLock = 1000 / 35;
                private ByteBuffer framebuffer = null;
                private int i = 0;
                @Override
                public void onDrawFrame(GL10 gl10) {
                    long currentTime = SystemClock.uptimeMillis();
                    long elapsed = currentTime - lastFrameTime;

                    if (span.textBlurBitmapUpdated) {
                        if (span.textBlurBitmap != null && !span.textBlurBitmap.isRecycled()) {
                            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[1]);
                            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, span.textBlurBitmap, 0);
                            span.textBlurBitmapUpdated = false;
                        }
                    }

//                    i++;
//                    if (true) {
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferIds[0]);

//                    gl10.glClear(GL10.GL_COLOR_BUFFER_BIT);
//                    gl10.glLoadIdentity();

                    GLES20.glUniform2f(u_pointer, span.pointerX < 0 ? framebufferWidth / 2f : span.pointerX, span.pointerY < 0 ? framebufferHeight / 2f : span.pointerY);
                    GLES20.glUniform1f(u_spoiler, span.getSpoiler());
                    GLES20.glUniform1i(u_spoiler_direction, span.state ? 1 : 0);
                    GLES20.glUniform1f(u_time, currentTime / 1000f);

//                    parent.getGlobalVisibleRect(visibleRect, visibleOffset);
//                    if (boundsView != null) {
//                        boundsView.getGlobalVisibleRect(boundsViewVisibleRect, boundsViewVisibleOffset);
//                        boundsViewVisibleRect.offset(Math.min(boundsViewVisibleOffset.x, 0), Math.min(boundsViewVisibleOffset.y, 0));
//                    }

//                    visibleRect.offset(Math.min(visibleOffset.x, 0), Math.min(visibleOffset.y, 0));
//
//                    GLES20.glUniform4f(
//                        u_bounds,
//                        Math.max(0, boundsViewVisibleRect.left - visibleRect.left),
//                        Math.max(0, boundsViewVisibleRect.top - visibleRect.top),
//                        Math.max(0, visibleRect.right - boundsViewVisibleRect.right),
//                        Math.max(0, visibleRect.bottom - boundsViewVisibleRect.bottom)
//                    );

                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, vPosition, 6);

                    if (framebuffer != null) {
                        framebuffer.clear();
                        GLES20.glReadPixels(0, 0, framebufferWidth, framebufferHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, framebuffer);
                        synchronized (bitmapRef) {
                            Bitmap bitmap = bitmapRef.get();
                            if (bitmap != null) {
//                                    bitmap.eraseColor(TRANSPARENT);
                                bitmap.copyPixelsFromBuffer(framebuffer);
                                bitmapRef.set(bitmap);
                            }
                        }
                        invalidateSelf();
                    }

                    lastFrameTime = currentTime;
//                    if (elapsed < minFrameLock && attached)
//                        try { Thread.sleep(minFrameLock - elapsed); } catch (Exception e) {}
//                    }
                }
            });
            sview.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            parent.addView(sview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        void invalidateSelf() {
            AndroidUtilities.runOnUIThread(this::invalidate);
        }

        private Matrix transform = new Matrix();
        private Paint paint = new Paint();
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            synchronized (bitmapRef) {
                if (bitmapRef.get() != null)
                    canvas.drawBitmap(bitmapRef.get(), transform, paint);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
//            super.onLayout(changed, left, top, right, bottom);

            if (sview != null) {
                sview.measure(
                        MeasureSpec.makeMeasureSpec(right - left, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(bottom - top, MeasureSpec.EXACTLY)
                );
                sview.layout(0, 0, right - left, bottom - top);
            }
        }

        private int program = -1;
        private int vertexShader = -1;
        private int fragmentShader = -1;

        private static final String vertexShaderSource = "" +
                "attribute vec4 vPosition;\n" +
                "void main() {\n" +
                "   gl_Position = vPosition;\n" +
                "}\n";
        private static final String fragmentShaderSource =
                "precision highp float;\n" +
                "\n" +
                "uniform vec4 u_particle_color;\n" +
                "uniform vec4 u_text_color;\n" +
                "uniform vec4 u_background_color;\n" +
                "uniform float u_dpi;\n" +
                "uniform float u_spoiler;\n" +
                "uniform int u_spoiler_direction;\n" +
                "uniform float u_time;\n" +
                "uniform float u_blurdist;\n" +
//                "uniform vec4 u_bounds;\n" +
                "uniform vec2 u_pointer;\n" +
                "uniform vec2 u_resolution;\n" +
                "uniform sampler2D tex_text;\n" +
                "uniform sampler2D tex_blur;\n" +
                "\n" +
                "float random(vec2 st) {\n" +
                "    float a = 12.9898;\n" +
                "    float b = 78.233;\n" +
                "    float c = 43758.5453;\n" +
                "    float dt= dot(st.xy, vec2(a,b));\n" +
                "    float sn= mod(dt,3.14);\n" +
                "    return fract(sin(sn) * c);\n" +
                "}\n" +
                "vec4 mixColors(vec4 b, vec4 a) {\n" +
                "    return vec4(a.rgb * (1. - b.a) + b.rgb * b.a, 1. - ((1. - a.a) * (1. - b.a)));\n" +
                "}\n" +
                "float particles(float density, vec2 st, float offset, float scale, float maxr) {\n" +
                "    vec2 i = floor(st * scale + offset);\n" +
                "    float rnd = random(i);\n" +
                "    float r = min((1. + random(-i.yx) * (maxr - 1.)) * scale / u_resolution.y, .5);\n" +
                "    float ar = min(rnd * 2., 1. - r * 2.) * .5;\n" +
                "    float a = rnd * 20. + u_time * (.1 + rnd * 2.5) * (random(-i) > .5 ? 1. : -1.);\n" +
                "    float ax = random(i) * 2.,\n" +
                "          ay = random(-i.yx) * 2.;\n" +
                "    float o = r + ar + .05;\n" +
                "    vec2 p = i + vec2(o) + vec2(rnd, random(-i.yx)) * vec2(1.-2.*o) + vec2(cos(ax*a),sin(ay*a))*ar;\n" +
                "    float alpha = max(min((cos(rnd * 50. + 3.14 * u_time * (.1 + rnd * .6))+1.)*.5, 1.), 0.);\n" +
                "    return (1. - (length(p - st * scale - offset) > r ? 1. : 0.)) * alpha;\n" +
                "}\n" +
                "mat2 rotate(float a) {\n" +
                "\tfloat s = sin(a);\n" +
                "\tfloat c = cos(a);\n" +
                "\treturn mat2(c, -s, s, c);\n" +
                "}\n" +
                "float textDistAt(vec2 uv) {\n" +
                "\treturn texture2D(tex_blur, uv).a;\n" +
                "}\n" +
                "\n" +
                "void main() {\n" +
//                "\tif (gl_FragCoord.x < u_bounds.x || (u_resolution.y - gl_FragCoord.y) < u_bounds.y || gl_FragCoord.x > u_resolution.x - u_bounds.z || gl_FragCoord.y < u_bounds.w) {\n" +
//                "\t\tgl_FragColor = vec4(0.);\n" +
//                "\t\treturn;\n" +
//                "\t}" +
                "\tvec2 uv = gl_FragCoord.xy / u_resolution.xy;\n" +
//                "\tuv.y = 1. - uv.y;\n" +
                "\tfloat S = (u_resolution.x / u_resolution.y);\n" +
                "\tvec2 st = vec2(uv.x * S, uv.y);\n" +
                "\tfloat T = u_spoiler;//1. - min(max(sin(u_time*3.14*2.5),0.),1.);\n" +
                "\n" +
                "\tvec2 center = .5 * vec2(S, 1.);\n" +
                "\tif (u_spoiler_direction == 0) {\n" +
                "\t\tT = min(max(length(st - (u_pointer.xy / u_resolution.xy * vec2(S, 1.))) + 1. - (1. - u_spoiler) * (S + 1.), 0.), 1.);\n" +
                "\t\tT = (1. - u_spoiler) > .8 ? T + (0. - T) * ((1. - u_spoiler) - .8) / .2 : T;\n" +
                "\t}\n" +
                "\t\n" +
                "\tvec4 textColor = texture2D(tex_text, uv);\n" +
                "\tfloat textDist = textDistAt(uv);\n" +
                "\n" +
                "\tfloat nepsilon = .5;\n" +
                "\tvec2 normal = textDist >= 1. ? vec2(0.) : normalize(vec2(\n" +
                "\t\ttextDistAt(uv - vec2(nepsilon / u_resolution.x, 0.)) - textDistAt(uv + vec2(nepsilon / u_resolution.x, 0.)),\n" +
                "\t\ttextDistAt(uv - vec2(0., nepsilon / u_resolution.y)) - textDistAt(uv + vec2(0., nepsilon / u_resolution.y))\n" +
                "\t));\n" +
                "\n" +
                "\tfloat td = textDistAt(uv);\n" +
                "\tvec2 sto = st + normal * min(textDist, 1. - T) * (u_blurdist / u_resolution.y) * textDist * 1.;\n" +
                "\tfloat p = 0.;\n" +
                "\tfloat s = u_resolution.y / (15. * u_dpi);\n" +
                "\tfloat G = 3.14 / 10.;\n" +
                "    p = max(p, particles(td, sto,                           0., s * 4., 3.));\n" +
                "    p = max(p, particles(td, sto * rotate(1.*G),  1000. +   .5, s * 8., 2.5));\n" +
                "    p = max(p, particles(td, sto * rotate(2.*G),  2000. +  .25, s * 9., 2.));\n" +
//                "    p = max(p, particles(td, sto * rotate(3.*G),  3000. + .125, s * 12., 2.));\n" +
//                "    p = max(p, particles(td, sto * rotate(5.*G), 5000. +  .125, s * 8., 2.));\n" +
//                "    p = max(p, particles(td, sto * rotate(4.*G), 4000. + .0125, s * 15., 1.5) * .5);\n" +
//                "    p = max(p, particles(td, sto * rotate(5.*G), 5000. + .0125, s * 30., 1.5) * .25);\n" +
                "\nfloat particleAlpha = min(p * (p * .2 < td ? 1. : 0.), 1.) * T;\n" +
                "\tvec4 particleColor = vec4(mix(u_text_color, u_particle_color, pow(T, 2.)).rgb, particleAlpha);\n" +
                "\n" +
                "\tgl_FragColor = mixColors(particleColor, textColor * (1. - T));\n" +
//                "\tgl_FragColor = mixColors(gl_FragColor, u_background_color);\n" +
                "}\n" +
                "";

        private void clear() {
            if (program != -1) {
                GLES20.glDeleteProgram(program);
                program = -1;
            }
            if (vertexShader != -1) {
                GLES20.glDeleteShader(vertexShader);
                vertexShader = -1;
            }
            if (fragmentShader != -1) {
                GLES20.glDeleteShader(fragmentShader);
                fragmentShader = -1;
            }
        }

        private int makeProgram() throws Exception {
//            clear();
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader = compileShader(vertexShaderSource, GLES20.GL_VERTEX_SHADER));
            GLES20.glAttachShader(program, fragmentShader = compileShader(fragmentShaderSource, GLES20.GL_FRAGMENT_SHADER));
            GLES20.glLinkProgram(program);
//            GLES20.glDetachShader(program, vertexShader);
//            GLES20.glDetachShader(program, fragmentShader);

            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] != GLES20.GL_TRUE) {
                GLES20.glDeleteProgram(program);
                program = -1;
                Log.e("SpoilerSpan GLES20", "failed to link program");
                throw new Exception("failed to link program");
            }
            return program;
        }

        private int compileShader(final String source, final int type) throws Exception {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] != GLES20.GL_TRUE) {
                String log = GLES20.glGetShaderInfoLog(shader);
                Log.e("SpoilerSpan GLES20", "failed to compile shader (type=" + type + ")\n"+log);
                GLES20.glDeleteShader(shader);
                throw new Exception("failed to compile shader (type=" + type + ")\n"+log);
            }
            return shader;
        }
    }
}
