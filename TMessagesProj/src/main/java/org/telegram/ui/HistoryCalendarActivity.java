package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class HistoryCalendarActivity extends BaseFragment {

    FrameLayout contentView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint boldBlackTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint boldWhiteTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    BackDrawable backButton;
    View calendarSignatureView;
    FrameLayout bottomButtonContainerBorder;
    FrameLayout bottomButtonContainer;
    TextView selectDaysButton;
    TextView clearHistoryButton;

    Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectedStrokeBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

        boldWhiteTextPaint.setTextSize(AndroidUtilities.dp(16));
        boldWhiteTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        boldWhiteTextPaint.setTextAlign(Paint.Align.CENTER);
        boldBlackTextPaint.setTextSize(AndroidUtilities.dp(16));
        boldBlackTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        boldBlackTextPaint.setTextAlign(Paint.Align.CENTER);

        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));

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
            @Override
            public void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                drawEndDayCircle(canvas, getScrollX(), computeVerticalScrollOffset());
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setClipChildren(true);
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

        calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(3), backgroundPaint);

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

        bottomButtonContainer = new FrameLayout(context);
        bottomButtonContainerBorder = new FrameLayout(context);
        bottomButtonContainerBorder.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(1.25f), Gravity.TOP | Gravity.FILL_HORIZONTAL));
        bottomButtonContainer.addView(bottomButtonContainerBorder);

        selectDaysButton = new TextView(context);
        selectDaysButton.setText("SELECT DAYS"); // TODO(dkaraush): text!
        selectDaysButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectDaysButton.setGravity(Gravity.CENTER);
        selectDaysButton.setTextSize(15);
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
        bottomButtonContainer.addView(selectDaysButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        clearHistoryButton = new TextView(context);
        clearHistoryButton.setText("CLEAR HISTORY"); // TODO(dkaraush): text!
        clearHistoryButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        clearHistoryButton.setGravity(Gravity.CENTER);
        clearHistoryButton.setTextSize(15);
        clearHistoryButton.setPadding(
                AndroidUtilities.dp(18),
                AndroidUtilities.dp(18),
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(18)
        );
        clearHistoryButton.setBackground(Theme.createSelectorDrawable(0x33ffffff & Theme.getColor(Theme.key_windowBackgroundWhiteRedText5), 3));
        clearHistoryButton.bringToFront();
        clearHistoryButton.setOnClickListener(view -> {
            int days = end.getValue() == null ? 1 : (int) Math.round((end.getValue().getEndTimestamp() - begin.getValue().getStartTimestamp()) / (double) (Day.DAY_LENGTH));
            TLRPC.User user = null;
            try {
                user = getMessagesController().getUser(DialogObject.isEncryptedDialog(dialogId) ? getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialogId)).user_id : dialogId);
            } catch (Exception e) {}
            AlertsCreator.createDeleteMessagesInRangeAlert(this, days, user, alsoDeleteFor -> {

            });
        });
        clearHistoryButton.setAlpha(0);
        clearHistoryButton.setClickable(false);
        bottomButtonContainer.addView(clearHistoryButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        contentView.addView(bottomButtonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 54, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        blurredView = new FrameLayout(context);
        blurredView.bringToFront();
        contentView.addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        loadNext();
        updateColors();
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

        if (selectDaysAnimator != null)
            selectDaysAnimator.cancel();
        if (clearHistoryAnimator != null)
            clearHistoryAnimator.cancel();
        selectDaysAnimator = selectDaysButton.animate().alpha(selectingDays ? 0f : 1f).setDuration(150);

        if (!selectingDays) {
            begin.update(null);
            end.update(null);
            endPos.update(null);
            endStrokePos.update(null);
            endPosOpacity.update(new Alpha(0f));
            updateMonthsFor(200);
        }

        updateClearHistoryButton();
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        calendarSignatureView.invalidate();
        bottomButtonContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        boldWhiteTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundCheckText));
        boldBlackTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
        selectDaysButton.setTextColor(0xff3a8cce); // TODO(dkaraush): color!
        clearHistoryButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
        selectedPaint.setColor(0xff50A5E6); // TODO(dkaraush): color!
        selectedStrokePaint.setColor(0xff50A5E6); // TODO(dkaraush): color!
        selectedStrokePaint.setStyle(Paint.Style.STROKE);
        selectedStrokePaint.setStrokeWidth(AndroidUtilities.dp(2f));
        selectedStrokeBackgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        selectedStrokeBackgroundPaint.setStyle(Paint.Style.STROKE);
        selectedStrokeBackgroundPaint.setStrokeWidth(AndroidUtilities.dp(2f));
        selectionPaint.setColor(0x2950A5E6); // TODO(dkaraush): color!
        bottomButtonContainerBorder.setBackgroundColor(0xffeaeaea); // TODO(dkaraush): color!
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        req.filter = new TLRPC.TL_inputMessagesFilterPhotos();

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
            startMonthTime = (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            Day touchDay = null;

            int index = event.getActionIndex();

            Rect bounds = new Rect();
            getDrawingRect(bounds);

            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);

            int x = (int) Math.round(((event.getX(index) - bounds.left) / xStep) - .5f),
                y = (int) Math.round(((event.getY(index) - bounds.top - AndroidUtilities.dp(44)) / yStep) - .5f);

            int day = (x + y * 7) - startDayOfWeek;
            if (day >= 0 && day < daysInMonth)
                touchDay = new Day(currentYear, currentMonthInYear, day);

            float localDayX = (x + .5f) * xStep,
                  localDayY = (y + .5f) * yStep + AndroidUtilities.dp(44);

            onTouch(this, touchDay, event, localDayX, localDayY);

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
//                pressed = false;
//            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
//                pressed = false;
//            }
            return true;
        }

        float[] getDayCoordinates(int day) {
            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);

            int column = (day + startDayOfWeek) % 7;
            int cell = (day + startDayOfWeek) / 7;

            return new float[] {
                xStep * (column + .5f),
                yStep * (cell + .5f) + AndroidUtilities.dp(44)
            };
        }

        private final Path path = new Path();
        private final RectF oval1 = new RectF(), oval2 = new RectF();

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            Day thisDay = new Day(currentYear, currentMonthInYear, 0);

            Day rangeFrom = Day.min(begin.getAnimatedValue(), end.getAnimatedValue());
            Day rangeTo = Day.max(begin.getAnimatedValue(), end.getAnimatedValue());
            if (rangeFrom != null && rangeTo != null && Day.inBetween(rangeFrom, rangeTo, currentYear, currentMonthInYear)) {
                int dayStart = rangeFrom.year == currentYear && rangeFrom.month == currentMonthInYear ? rangeFrom.day : 0;
                int dayEnd =   rangeTo.year == currentYear   && rangeTo.month == currentMonthInYear   ? rangeTo.day   : daysInMonth - 1;
                for (int r = 0; r < cellCount; ++r) {
                    int weekDayStart = Math.max(0, 7 * r - startDayOfWeek),
                        weekDayEnd = Math.min(daysInMonth, 7 * (r) + (6 - startDayOfWeek));
                    if (dayStart > weekDayEnd || dayEnd < weekDayStart)
                        continue;
                    float[] fromCoords = getDayCoordinates(Math.max(weekDayStart, dayStart));
                    float[] toCoords = getDayCoordinates(Math.min(weekDayEnd, dayEnd));

                    // float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean useCenter, @NonNull Paint paint
                    float radius = AndroidUtilities.dp(44) / 2f;

                    path.reset();
                    oval1.set(fromCoords[0] - radius, fromCoords[1] - radius, fromCoords[0] + radius, fromCoords[1] + radius);
                    path.arcTo(oval1, 0f + 90f, 180f, false);
                    oval2.set(toCoords[0] - radius, toCoords[1] - radius, toCoords[0] + radius, toCoords[1] + radius);
                    path.arcTo(oval2, 180f+ 90f, 180f, false);
                    canvas.drawPath(path, selectionPaint);
                }
            }

//            float xStep = getMeasuredWidth() / 7f;
//            float yStep = AndroidUtilities.dp(44 + 8);
            for (int i = 0; i < daysInMonth; i++) {
//                float cx = xStep * (currentColumn + .5f);
//                float cy = yStep * (currentCell + .5f) + AndroidUtilities.dp(44);
                float[] c = getDayCoordinates(i);
                float cx = c[0], cy = c[1];
                int nowTime = (int) (System.currentTimeMillis() / 1000L);

                thisDay.day = i;

                boolean isSelected = thisDay.isBetween(rangeFrom, rangeTo);
                float selectedInsideT;
                long l = 0, r = 0, x = thisDay.getTimestamp();
                float maxDist = Day.DAY_LENGTH;
                if ((rangeFrom == null || x >= (l = rangeFrom.getTimestamp())) && (rangeTo == null || x <= (r = rangeTo.getTimestamp())) && !(rangeFrom == null && rangeTo == null)) {
                    selectedInsideT = 0f;
                } else {
                    if (rangeFrom != null && rangeTo != null) {
                        selectedInsideT = (float) Math.min(Math.abs(l - x), Math.abs(x - r)) / maxDist;
                    } else if (rangeFrom != null)
                        selectedInsideT = (float) Math.abs(l - x) / maxDist;
                    else if (rangeTo != null)
                        selectedInsideT = (float) Math.abs(x - r) / maxDist;
                    else
                        selectedInsideT = 1f;
                }
                selectedInsideT = 1f - Math.min(1f, selectedInsideT);
                Paint defaultTextPaint = isSelected ? boldBlackTextPaint : textPaint;

                boolean selectedAnimation = false, isEndDay = false;
                float selectedAnimationT = 0f;
                if (thisDay.equals(end.getValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = end.getAnimatedT();
                    isEndDay = true;
                } else if (thisDay.equals(end.getOutValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = end.getOutAnimatedT();
                    isEndDay = true;
                }
                if (thisDay.equals(begin.getValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = begin.getAnimatedT();
                    isEndDay = false;
                } else if (thisDay.equals(begin.getOutValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = begin.getOutAnimatedT();
                    isEndDay = false;
                }
                if (selectedAnimation) {
                    if (!isEndDay) {
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44 - 8) / 2f * selectedAnimationT, selectedPaint);
                        selectedStrokePaint.setAlpha((int) (selectedAnimationT * 255));
                        canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44 - 12) + AndroidUtilities.dp(12) * selectedAnimationT) / 2f, selectedStrokePaint);
                    }
                    selectedStrokeBackgroundPaint.setAlpha((int) (selectedAnimationT * 255));
                    canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44 - 12 - 4) + AndroidUtilities.dp(12) * selectedAnimationT) / 2f, selectedStrokeBackgroundPaint);
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
                        float imageSize = AndroidUtilities.dp(44 - (8 * Math.max(selectedInsideT, selectedAnimationT)));
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
                        int oldAlpha = defaultTextPaint.getAlpha();
                        defaultTextPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), defaultTextPaint);
                        defaultTextPaint.setAlpha(oldAlpha);

                        oldAlpha = boldWhiteTextPaint.getAlpha();
                        boldWhiteTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), boldWhiteTextPaint);
                        boldWhiteTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), boldWhiteTextPaint);
                    }

                } else {
                    if (selectedAnimation) {
                        int oldAlpha = defaultTextPaint.getAlpha();
                        defaultTextPaint.setAlpha((int) (oldAlpha * (1f - selectedAnimationT)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), defaultTextPaint);
                        defaultTextPaint.setAlpha(oldAlpha);

                        oldAlpha = boldWhiteTextPaint.getAlpha();
                        boldWhiteTextPaint.setAlpha((int) (oldAlpha * selectedAnimationT));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), boldWhiteTextPaint);
                        boldWhiteTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), defaultTextPaint);
                    }
                }
//
//                currentColumn++;
//                if (currentColumn >= 7) {
//                    currentColumn = 0;
//                    currentCell++;
//                }
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
    private AnimatedProperty<Day> end = new AnimatedProperty<>(100);
    private AnimatedProperty<Pos> endPos = new AnimatedProperty<Pos>(100, AnimatedProperty.AnimatedPropertyInterpolation.EASE_OUT_BACK);
    private AnimatedProperty<Pos> endStrokePos = new AnimatedProperty<Pos>(175, AnimatedProperty.AnimatedPropertyInterpolation.EASE_OUT_BACK);
    private AnimatedProperty<Alpha> endPosOpacity = new AnimatedProperty<Alpha>(150);

    class Pos implements Lerping<Pos> {
        float x, y;
        Pos(float x, float y) {
            this.x = x;
            this.y = y;
        }
        public Pos lerp(Pos to, float t) {
            return new Pos(x + (to.x - x) * t, y + (to.y - y) * t);
        }
    }
    class Alpha implements Lerping<Alpha> {
        float value;
        Alpha(float alpha) { this.value = alpha; }
        public Alpha lerp(Alpha to, float t) { return new Alpha(value + (to.value - value) * t); }
    }

    private void drawEndDayCircle(Canvas canvas, int scrollX, int scrollY) {
        Alpha posAlpha = endPosOpacity.getAnimatedValue();
        if (posAlpha != null) {
            selectedStrokePaint.setAlpha((int) (posAlpha.value * 255));
        }

        Pos pos = endPos.getAnimatedValue();
        if (pos != null) {
            float scale = 1f - .8f * (float) Math.sin(endPos.getAnimatedT() * Math.PI);
            canvas.drawCircle(pos.x + scrollX, pos.y - scrollY, AndroidUtilities.dp(44 - 8 ) / 2f * scale, selectedStrokeBackgroundPaint);
            canvas.drawCircle(pos.x + scrollX, pos.y - scrollY, AndroidUtilities.dp(44 - 8) / 2f * scale, selectedPaint);
        }

        Pos strokePos = endStrokePos.getAnimatedValue();
        if (strokePos != null) {
            float scale = 1f - .1f * (float) Math.sin(endStrokePos.getAnimatedT() * Math.PI);
            canvas.drawCircle(strokePos.x + scrollX, strokePos.y - scrollY, AndroidUtilities.dp(44) / 2f * scale, selectedStrokePaint);
        }
    }


    boolean pressed;
    long pressId = 0;
    long movingEndDayPressId;
    Runnable cancelHold;

    public void onTouch(MonthView monthView, Day touchDay, MotionEvent event, float localDayX, float localDayY) {

        float monthYOffset = 0;
        if (monthView != null) {
            monthYOffset = (monthCount - listView.getChildAdapterPosition(monthView) - 1) * AndroidUtilities.dp(304);
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            long currentPressId = ++pressId;
            pressed = true;
            if (!selectingDays) {
                if (cancelHold == null) {
                    contentView.postDelayed(() -> {
                        if (pressed && pressId == currentPressId) {
                            listView.requestDisallowInterceptTouchEvent(true);
                            Runnable closeMessages = showMessagesPreview();
                            cancelHold = () -> {
                                closeMessages.run();
                                cancelHold = null;
                            };
                        }
                    }, ViewConfiguration.getTapTimeout());
                }
            } else if (touchDay != null) {
                if (begin.getValue() == null) {
                    begin.update(touchDay);
                    end.update(null);
                    endStrokePos.update(null);
                    endPosOpacity.update(new Alpha(0f));
                    listView.requestDisallowInterceptTouchEvent(true);
                    updateMonthsFor(200);
                } else if (begin.getValue() != null && end.getValue() == null && !touchDay.equals(end.getValue())) {
                    end.update(touchDay);
                    endPos.update(new Pos(localDayX, monthYOffset + localDayY));
                    endStrokePos.update(new Pos(localDayX, monthYOffset + localDayY));
                    endPosOpacity.update(new Alpha(1f));
                    listView.requestDisallowInterceptTouchEvent(true);
                    updateMonthsFor(200);
                }
                movingEndDayPressId = ++pressId;
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (selectingDays && touchDay != null && movingEndDayPressId == pressId) {
                if (!touchDay.equals(end.getValue())) {
                    end.update(touchDay);
                    endPos.update(new Pos(localDayX, monthYOffset + localDayY));
                    endStrokePos.update(new Pos(localDayX, monthYOffset + localDayY));
                    endPosOpacity.update(new Alpha(1f));
                    listView.requestDisallowInterceptTouchEvent(true);
                    updateMonthsFor(200);
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (cancelHold != null) {
                cancelHold.run();
                cancelHold = null;
            }
            listView.requestDisallowInterceptTouchEvent(false);
        }

        updateClearHistoryButton();
    }

    private void updateClearHistoryButton() {
        clearHistoryButton.setEnabled(selectingDays && begin.getValue() != null);
        clearHistoryButton.setClickable(selectingDays && begin.getValue() != null);
        float alpha = !selectingDays ? 0f : (clearHistoryButton.isEnabled() ? 1f : 0.5f);

        if (clearHistoryAnimator != null)
            clearHistoryAnimator.cancel();
        clearHistoryAnimator = clearHistoryButton.animate().alpha(alpha).setDuration(150).withEndAction(() -> clearHistoryAnimator = null);
    }

    private ValueAnimator updateMonthesAnimator = null;
    public void updateMonthsFor(int duration) {
        if (updateMonthesAnimator != null)
            updateMonthesAnimator.cancel();
        updateMonthesAnimator = ObjectAnimator.ofFloat(0f, 1f).setDuration(duration);
        updateMonthesAnimator.addUpdateListener((v) -> {
            listView.invalidate();
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
            listView.invalidate();
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
    float offset = -2f; // offset to move to the next day

    public Day(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }
    public Day(int year, int month, int day, float offset) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.offset = offset;
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

    public static Day min(Day a, Day b) {
        if (a == null) return b;
        if (a.year != b.year) return (a.year < b.year ? a : b);
        if (a.month != b.month) return (a.month < b.month ? a : b);
        if (a.day != b.day) return (a.day < b.day ? a : b);
        return a;
    }
    public static Day max(Day a, Day b) {
        if (a == null) return b;
        if (a.year != b.year) return (a.year > b.year ? a : b);
        if (a.month != b.month) return (a.month > b.month ? a : b);
        if (a.day != b.day) return (a.day > b.day ? a : b);
        return a;
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
    public static final long DAY_LENGTH = 1000 * 60 * 60 * 24; // approximately of course
    public long getTimestamp() {
        float myOffset = offset < -1f ? .5f : offset;
        return getStartTimestamp() + (long) (myOffset * DAY_LENGTH);
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
    public static Day fromTimestamp(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        return new Day(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
                (
                    calendar.get(Calendar.HOUR) +
                    calendar.get(Calendar.MINUTE) / 60f +
                    calendar.get(Calendar.SECOND) / 3600f
                ) / 24f
        );
    }

    public int daysInMonth() {
        return YearMonth.of(year, month).lengthOfMonth();
    }

    public Day lerp(Day to, float t) {
        long fromTimestamp = this.getStartTimestamp(),
             toTimestamp =   to.getEndTimestamp();
        return Day.fromTimestamp((fromTimestamp + (long) ((toTimestamp - fromTimestamp) * t)));
    }

    public boolean isBetween(Day from, Day to) {
        if (from == null || to == null)
            return false;
        if (year < from.year || year > to.year)
            return false;
        if ((year == from.year && month < from.month) || (year == to.year && month > to.month))
            return false;
        if ((year == from.year && month == from.month && day < from.day) || (year == to.year && month == to.month && day > to.day))
            return false;
        return true;
    }
}

// implementation of transition-like-in-css behaviour
class AnimatedProperty<T extends Lerping<T>> {
    private T value = null;
    private T oldValue = null;
    private long switchedAt = 0;
    private long duration = 0;
    private AnimatedPropertyInterpolation interpolation;

    enum AnimatedPropertyInterpolation {
        NONE,
        EASE_OUT_BACK
    }

    public AnimatedProperty(int transitionDuration) {
        this.duration = transitionDuration;
    }
    public AnimatedProperty(int transitionDuration, AnimatedPropertyInterpolation interpolation) {
        this.duration = transitionDuration;
        this.interpolation = interpolation;
    }

    public void update(T newValue) {
        oldValue = getAnimatedValue();
        value = newValue;
        switchedAt = System.currentTimeMillis();
    }

    private float interpolate(float t) {
        if (interpolation == null)
            return t;
        switch (interpolation) {
            case NONE:
                return t;
            case EASE_OUT_BACK:
                float G = 1.2f; // 1.70158f;
                return 1f + (1f + G) * ((float) Math.pow(t - 1f, 3f)) + G * ((float) Math.pow(t - 1f, 2f));
        }
        return t;
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
        return oldValue.lerp(value, this.interpolate(t));
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