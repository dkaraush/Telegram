package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ContentView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;

public class HistoryCalendarActivity extends BaseFragment {

    FrameLayout contentView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    BackDrawable backButton;
    TextView selectDaysButton;
    TextView clearHistoryButton;

    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;


    int startFromYear;
    int startFromMonth;
    int monthCount;

    HistoryCalendarActivity.CalendarAdapter adapter;
    HistoryCalendarActivity.Callback callback;


    SparseArray<SparseArray<HistoryCalendarActivity.PeriodDay>> messagesByYearMounth = new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;

    public HistoryCalendarActivity(Bundle args, int selectedDate) {
        super(args);

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public boolean onBackPressed() {
        if (selectingDays) {
            switchSelectingDays(false);
            return false;
        } else {
            finishFragment();
            return super.onBackPressed();
        }
    }

    @Override
    public View createView(Context context) {
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        fragmentView = contentView = new FrameLayout(context);
        contentView.setClipChildren(false);

        createActionBar(context);
        contentView.addView(actionBar);

        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new HistoryCalendarActivity.CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, 54));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (selectingDays) {
                        switchSelectingDays(false);
                    } else {
                        finishFragment();
                    }
                }
            }
        });

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }

        selectDaysButton = new TextView(context);
        selectDaysButton.setText("SELECT DAYS"); // TODO(dkaraush): text!
        selectDaysButton.setTextColor(0xff3a8cce); // TODO(dkaraush): color!
        selectDaysButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectDaysButton.setGravity(Gravity.CENTER);
        selectDaysButton.setPadding(
                AndroidUtilities.dp(18),
                AndroidUtilities.dp(18),
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(18)
        );
        selectDaysButton.setBackground(Theme.createSelectorDrawable(0x333a8cce, 3));
        selectDaysButton.bringToFront();
        selectDaysButton.setOnClickListener(view -> switchSelectingDays(true));
        selectDaysButton.setAlpha(1f);
        selectDaysButton.setClickable(true);
        contentView.addView(selectDaysButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 54, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        clearHistoryButton = new TextView(context);
        clearHistoryButton.setText("CLEAR HISTORY"); // TODO(dkaraush): text!
        clearHistoryButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5)); // TODO(dkaraush): color!
        clearHistoryButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        clearHistoryButton.setGravity(Gravity.CENTER);
        clearHistoryButton.setPadding(
                AndroidUtilities.dp(18),
                AndroidUtilities.dp(18),
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(18)
        );
        clearHistoryButton.setBackground(Theme.createSelectorDrawable(0x33ffffff & Theme.getColor(Theme.key_windowBackgroundWhiteRedText5), 3));
        clearHistoryButton.bringToFront();
//        clearHistoryButton.setOnClickListener(view -> switchSelectingDays(false));
        clearHistoryButton.setAlpha(0);
        clearHistoryButton.setClickable(false);
        contentView.addView(clearHistoryButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 54, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        blurredView = new FrameLayout(context);
        blurredView.bringToFront();
        contentView.addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        loadNext();
        updateColors();
        activeTextPaint.setColor(Color.WHITE);
        actionBar.setBackButtonDrawable(backButton = new BackDrawable(false));
        return fragmentView;
    }

    private ViewPropertyAnimator selectDaysAnimator = null;
    private ViewPropertyAnimator clearHistoryAnimator = null;
    private boolean selectingDays = false;
    private void switchSelectingDays(boolean value) {
        if (selectingDays == value)
            return;

        selectingDays = value;
        String actionBarTitle = selectingDays ? "Select days" : LocaleController.getString("Calendar", R.string.Calendar);
        actionBar.setTitleAnimated(actionBarTitle, !value, 150);

        backButton.setRotation(value ? 1.0f : 0f, true);
        selectDaysButton.setClickable(!value);
        clearHistoryButton.setClickable(value);

        if (selectDaysAnimator != null)
            selectDaysAnimator.cancel();
        if (clearHistoryAnimator != null)
            clearHistoryAnimator.cancel();
        selectDaysAnimator = selectDaysButton.animate().alpha(selectingDays ? 0f : 1f).setDuration(150);
        clearHistoryAnimator = clearHistoryButton.animate().alpha(selectingDays ? 1f : 0f).setDuration(150);
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
        selectedPaint.setColor(0xff50A5E6); // TODO(dkaraush): color!
        selectionPaint.setColor(0xff50A5E6); // TODO(dkaraush): color!
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(AndroidUtilities.dp(2f));
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<HistoryCalendarActivity.PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    HistoryCalendarActivity.PeriodDay periodDay = new HistoryCalendarActivity.PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }

                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((calendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof HistoryCalendarActivity.MonthView) {
                int currentMonth = ((HistoryCalendarActivity.MonthView) child).currentYear * 100 + ((HistoryCalendarActivity.MonthView) child).currentMonthInYear;
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        };
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new HistoryCalendarActivity.MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            HistoryCalendarActivity.MonthView monthView = (HistoryCalendarActivity.MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMounth.get(year * 100 + month), animated);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class MonthView extends FrameLayout {

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;

        SparseArray<HistoryCalendarActivity.PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

//        SparseArray<HistoryCalendarActivity.PeriodDay> animatedFromMessagesByDays = new SparseArray<>();
//        SparseArray<ImageReceiver> animatedFromImagesByDays = new SparseArray<>();

        boolean attached;
        float animationProgress = 1f;

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));
        }

        public void setDate(int year, int monthInYear, SparseArray<HistoryCalendarActivity.PeriodDay> messagesByDays, boolean animated) {
            boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;

            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).onDetachedFromWindow();
                        imagesByDays.valueAt(i).setParentView(null);
                    }
                    imagesByDays = null;
                }
            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    HistoryCalendarActivity.PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    if (messageObject != null) {
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, receiver);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime= (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }


        boolean pressed;
        long pressId = 0;
        float pressedX;
        float pressedY;
        Runnable cancelHold;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            Day touchDay = null;

            int index = event.getActionIndex();

            Rect bounds = new Rect();
            getDrawingRect(bounds);

            float[] point = new float[] { event.getX(index) - bounds.left, event.getY(index) - bounds.top - AndroidUtilities.dp(44) };
            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);

            int x = (int) Math.round((point[0] / xStep) - .5f),
                y = (int) Math.round((point[1] / yStep) - .5f);

            int day = (x + y * 7) - startDayOfWeek;
            if (day >= 0 && day < daysInMonth)
                touchDay = new Day(currentYear, currentMonthInYear, day);

//            if (touchDay != null) {
//                if (toast != null)
//                    toast.cancel();
//                toast = Toast.makeText(getContext(), touchDay.year + " " + touchDay.month + " " + touchDay.day, Toast.LENGTH_SHORT);
//                toast.show();
//            }

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                long currentPressId = ++pressId;
                pressed = true;
                pressedX = event.getX();
                pressedY = event.getY();
                if (!selectingDays) {
                    postDelayed(() -> {
                        if (pressed && pressId == currentPressId) {
                            listView.requestDisallowInterceptTouchEvent(true);
                            cancelHold = showMessagesPreview();
                        }
                    }, ViewConfiguration.getTapTimeout());
                } else if (touchDay != null) {
                    if (begin.getValue() == null || end.getValue() != null) {
                        begin.update(touchDay);
                        end.update(null);
                        listView.requestDisallowInterceptTouchEvent(true);
                        updateMonthsFor(150);
                    } else if (begin.getValue() != null && end.getValue() == null && !touchDay.equals(end.getValue())) {
                        end.update(touchDay);
                        listView.requestDisallowInterceptTouchEvent(true);
                        updateMonthsFor(150);
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (selectingDays && touchDay != null) {
                    if (!touchDay.equals(end.getValue())) {
                        end.update(touchDay);
                        listView.requestDisallowInterceptTouchEvent(true);
                        updateMonthsFor(150);
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (cancelHold != null) {
                    cancelHold.run();
                    cancelHold = null;
                }
                listView.requestDisallowInterceptTouchEvent(false);
//
//                long pressedDuration = System.currentTimeMillis() - ;
//                if (pressedDuration >= ) {
//                    // long press
//
//                } else if (pressedDuration >= ViewConfiguration.getTapTimeout()) {
//                    // tap
//
//                }

//                    for (int i = 0; i < imagesByDays.size(); i++) {
//                        if (imagesByDays.valueAt(i).getDrawRegion().contains(pressedX, pressedY)) {
//                            if (callback != null) {
//                                HistoryCalendarActivity.PeriodDay periodDay = messagesByDays.valueAt(i);
//                                callback.onDateSelected(periodDay.messageObject.getId(), periodDay.startOffset);
//                                finishFragment();
                pressed = false;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = false;
            }
            return pressed;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;

            Day thisDay = new Day(currentYear, currentMonthInYear, 0);
            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * (currentColumn + .5f);
                float cy = yStep * (currentCell + .5f) + AndroidUtilities.dp(44);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);

                thisDay.day = i;

                boolean selectedAnimation = false;
                float selectedAnimationT = 0f;
                if (thisDay.equals(begin.getValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = Math.max(selectedAnimationT, begin.getAnimatedT());
                }
                if (thisDay.equals(begin.getOutValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = Math.max(selectedAnimationT, begin.getOutAnimatedT());
                }
                if (thisDay.equals(end.getValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = Math.max(selectedAnimationT, end.getAnimatedT());
                }
                if (selectedAnimation) {
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(44 - 8) / 2f * selectedAnimationT, selectedPaint);
                    selectionPaint.setAlpha((int) (selectedAnimationT * 255));
                    canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44 - 12) + AndroidUtilities.dp(12) * selectedAnimationT) / 2f, selectionPaint);
                }

                if (nowTime < startMonthTime + (i + 1) * 86400) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (messagesByDays != null && messagesByDays.get(i, null) != null) {
                    float alpha = 1f;
                    if (imagesByDays.get(i) != null) {
                        if (checkEnterItems && !messagesByDays.get(i).wasDrawn) {
                            messagesByDays.get(i).enterAlpha = 0f;
                            messagesByDays.get(i).startEnterDelay = (cy + getY()) / listView.getMeasuredHeight() * 150;
                        }
                        if (messagesByDays.get(i).startEnterDelay > 0) {
                            messagesByDays.get(i).startEnterDelay -= 16;
                            if (messagesByDays.get(i).startEnterDelay < 0) {
                                messagesByDays.get(i).startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (messagesByDays.get(i).startEnterDelay == 0 && messagesByDays.get(i).enterAlpha != 1f) {
                            messagesByDays.get(i).enterAlpha += 16 / 220f;
                            if (messagesByDays.get(i).enterAlpha > 1f) {
                                messagesByDays.get(i).enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = messagesByDays.get(i).enterAlpha;
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s,cx, cy);
                        }
                        imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                        float imageSize = AndroidUtilities.dp(44 - 8 * selectedAnimationT);
                        imagesByDays.get(i).setImageCoords(cx - imageSize / 2f, cy - imageSize / 2f, imageSize, imageSize);
                        imagesByDays.get(i).draw(canvas);
                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, imageSize / 2f, blackoutPaint);
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f) {
                            canvas.restore();
                        }
                    }

                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    }

                } else {
                    if (selectedAnimation) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - selectedAnimationT)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * selectedAnimationT));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    }
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                }
            }
        }
    }

    private AnimatedProperty<Day> begin = new AnimatedProperty<>(150);
    private AnimatedProperty<Day> end = new AnimatedProperty<>(150);

    private ValueAnimator updateMonthesAnimator = null;
    public void updateMonthsFor(int duration) {
        if (updateMonthesAnimator != null)
            updateMonthesAnimator.cancel();
        updateMonthesAnimator = ObjectAnimator.ofFloat(0f, 1f).setDuration(duration);
        updateMonthesAnimator.addUpdateListener((v) -> {
            LinearLayoutManager listLayoutManager = (LinearLayoutManager) listView.getLayoutManager();
            for (
                    int i = listLayoutManager.findFirstVisibleItemPosition();
                    i <= listLayoutManager.findLastVisibleItemPosition();
                    ++i
            ) {
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(i);
                if (holder != null) {
                    MonthView monthView = (MonthView) holder.itemView;
                    monthView.invalidate();
                }
            }
        });
        updateMonthesAnimator.start();
    }
    public void updateMonthsFor(Day from, Day to, int duration) {
        if (updateMonthesAnimator != null)
            updateMonthesAnimator.cancel();
        updateMonthesAnimator = ObjectAnimator.ofFloat(0f, 1f).setDuration(duration);
        updateMonthesAnimator.addUpdateListener((v) -> {
            LinearLayoutManager listLayoutManager = (LinearLayoutManager) listView.getLayoutManager();
            for (
                    int i = listLayoutManager.findFirstVisibleItemPosition();
                    i <= listLayoutManager.findLastVisibleItemPosition();
                    ++i
            ) {
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(i);
                if (holder != null) {
                    MonthView monthView = (MonthView) holder.itemView;
                    if (Day.inBetween(from, to, monthView.currentYear, monthView.currentMonthInYear))
                        monthView.invalidate();
                }
            }
        });
        updateMonthesAnimator.start();
    }

    public void setCallback(HistoryCalendarActivity.Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onDateSelected(int messageId, int startOffset);
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                updateColors();
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector);


        return super.getThemeDescriptions();
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }

    ViewPropertyAnimator blurredViewAnimator;
    FrameLayout blurredView;
    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }
        int w = (int) (fragmentView.getMeasuredWidth() / 6.0f);
        int h = (int) (fragmentView.getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        fragmentView.draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
        blurredView.setClickable(false);

        if (blurredViewAnimator != null)
            blurredViewAnimator.cancel();
        blurredViewAnimator = blurredView.animate().alpha(1f).setDuration(150);
    }
    private void hideBlurBitmap() {
        if (blurredViewAnimator != null)
            blurredViewAnimator.cancel();
        blurredViewAnimator = blurredView.animate().alpha(0f).setDuration(150).withEndAction(() -> blurredView.setVisibility(View.GONE));
    }

    private boolean previewIsShown = false;
    private Runnable showMessagesPreview() {
        if (previewIsShown)
            return null;

        previewIsShown = true;

        Bundle bundle = new Bundle();
        bundle.putLong("dialog_id", dialogId);
        bundle.putLong("chat_id", -dialogId);

        ChatActivity chatActivity = new ChatActivity(bundle);

        prepareBlurBitmap();
        presentFragmentAsPreviewWithButtons(chatActivity, getPreviewMenu());
        return () -> {
            previewIsShown = false;
            hideBlurBitmap();
            finishPreviewFragment();
        };
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        if (previewMenu != null) {

            ViewGroup parent = (ViewGroup) previewMenu.getParent();
            if (parent != null) {
                parent.removeView(previewMenu);
            }
        }
    }

    private ActionBarPopupWindow.ActionBarPopupWindowLayout previewMenu;
    private ViewPropertyAnimator previewMenuAnimator;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout getPreviewMenu() {
        if (previewMenu != null) {
            return previewMenu;
        }

        previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, null);
        previewMenu.setMinimumWidth(AndroidUtilities.dp(200));
        previewMenu.setAnimationEnabled(true);
//        Rect backgroundPaddings = new Rect();
//        Drawable shadowDrawable = getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
//        shadowDrawable.getPadding(backgroundPaddings);
        if (Build.VERSION.SDK_INT >= 21) {
            previewMenu.setElevation(AndroidUtilities.dp(4));
        }
        previewMenu.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        ActionBarMenuSubItem cell1 = new ActionBarMenuSubItem(getParentActivity(), true, true, null);
        cell1.setTextAndIcon("Jump to date", R.drawable.msg_message);
        cell1.setItemHeight(46);
        cell1.setTag(R.id.width_tag, 240);
        cell1.setClickable(true);
        previewMenu.addView(cell1);

        ActionBarMenuSubItem cell2 = new ActionBarMenuSubItem(getParentActivity(), true, true, null);
        cell2.setTextAndIcon("Select this day", R.drawable.msg_select);
        cell2.setItemHeight(46);
        cell2.setTag(R.id.width_tag, 240);
        cell2.setClickable(true);
        previewMenu.addView(cell2);

        ActionBarMenuSubItem cell3 = new ActionBarMenuSubItem(getParentActivity(), true, true, null) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Drawable ripple = this.getBackground();
                    if (ripple != null && ripple instanceof RippleDrawable) {
                        if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER || event.getAction() == MotionEvent.ACTION_MOVE)
                            ripple.setState(new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled });
                        else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT || event.getAction() == MotionEvent.ACTION_OUTSIDE)
                            ripple.setState(new int[] { });
                    }
                }

                return super.onTouchEvent(event);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Drawable ripple = this.getBackground();
                    if (ripple != null && ripple instanceof RippleDrawable) {
                        if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER || event.getAction() == MotionEvent.ACTION_MOVE)
                            ripple.setState(new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled });
                        else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT || event.getAction() == MotionEvent.ACTION_OUTSIDE)
                            ripple.setState(new int[] { });
                    }
                }


                return super.onInterceptTouchEvent(event);
            }
        };
        cell3.setTextAndIcon("Clear history", R.drawable.msg_delete);
        cell3.setItemHeight(46);
        cell3.setTag(R.id.width_tag, 240);
        cell3.setClickable(true);
        previewMenu.addView(cell3);

        previewMenu.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));

        return previewMenu;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!parentLayout.isInPreviewMode()) {
            hideBlurBitmap();
            previewIsShown = false;
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        inTransitionAnimation = false;
        if (parentLayout != null && !parentLayout.isInPreviewMode()) {
            hideBlurBitmap();
            previewIsShown = false;
        }
    }
}

interface Lerping<T> {
    T lerp(T to, float t);
}


class Day implements Lerping<Day> {
    int year;
    int month;
    int day;

    public Day(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public boolean equals(Day otherDay) {
        return otherDay != null &&
                this.year == otherDay.year &&
                this.month == otherDay.month &&
                this.day == otherDay.day;
    }

    public boolean insideMonth(int year, int month) {
        return (this.year == year && this.month == month);
    }

    public static boolean inBetween(Day from, Day to, int thisYear, int thisMonth) {
        return (
            (from == null || (thisYear >= from.year && (thisYear != from.year || thisMonth >= from.month))) &&
            (to == null || (thisYear <= to.year && (thisYear != to.year || thisMonth <= to.month)))
        );
    }

    public long getStartTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime().getTime();
    }
    public long getEndTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime().getTime();
    }

    public Day lerp(Day to, float t) {
        Day lerpedDay = new Day(this.year, this.month, this.day);
//                int daysInBetween =
        return lerpedDay;
    }
}

// implementation of transition-like-in-css behaviour
class AnimatedProperty<T extends Lerping<T>> {
    private T value = null;
    private T oldValue = null;
    private long switchedAt = 0;
    private long duration = 0;

    public AnimatedProperty(int transitionDuration) {
        this.duration = transitionDuration;
    }

    public void update(T newValue) {
        oldValue = getAnimatedValue();
        value = newValue;
        switchedAt = System.currentTimeMillis();
    }

    public float getAnimatedT() {
        return Math.min(Math.max(((float) (System.currentTimeMillis() - switchedAt)) / duration, 0f), 1f);
    }

    public T getAnimatedValue() {
        if (oldValue == null)
            return value;
        if (value == null)
            return null;

        float t = getAnimatedT();
        if (t >= 1f)
            return value;
        if (t <= 0f)
            return oldValue;
        return oldValue.lerp(value, t);
    }

    public float getOutAnimatedT() {
        return 1f - getAnimatedT();
    }
    public T getOutValue() {
        if (getAnimatedT() > 1f)
            return null;
        return oldValue;
    }

    public T getValue() { return value; }
}