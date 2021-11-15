
package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
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
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
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
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

public class HistoryCalendarActivity extends BaseFragment {

    public final boolean BLEEP_SELECTED_DAY_AFTER_OPENING_CALENDAR = false;

    SizeNotifierFrameLayout contentView;
    FrameLayout touchListener;

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
    SparseArray<SparseArray<HistoryCalendarActivity.PeriodDay>> messagesByYearMounth = new SparseArray<>();

    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private boolean isOpened;

    int selectedYear;
    int selectedMonth;
    int selectedDay;
    long selectedAt;

    private JumpToDateRunnable jumpToDate;
    public interface JumpToDateRunnable {
        void run(int date);
    }

    public HistoryCalendarActivity(Bundle args, int selectedDate, JumpToDateRunnable jumpToDate) {
        super(args);

        this.jumpToDate = jumpToDate;

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
            selectedDay = calendar.get(Calendar.DATE) - 1;
            selectedAt = System.currentTimeMillis();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public boolean onBackPressed() {
        if (cancelHold != null) {
            cancelHold.run();
            cancelHold = null;
        }

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

        fragmentView = contentView = new SizeNotifierFrameLayout(context) {

            @Override
            protected void dispatchDraw(Canvas canvas) {
//                int actionBarHeight = getActionBarFullHeight();
//                int top;
//                if (inPreviewMode) {
//                    top = AndroidUtilities.statusBarHeight;
//                } else {
//                    top = (int) (-getY() + actionBar.getY());
//                }
//                if (whiteActionBar) {
//                    if (searchAnimationProgress == 1f) {
//                        actionBarSearchPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
//                        if (searchTabsView != null) {
//                            searchTabsView.setTranslationY(0);
//                            searchTabsView.setAlpha(1f);
//                            if (filtersView != null) {
//                                filtersView.setTranslationY(0);
//                                filtersView.setAlpha(1f);
//                            }
//                        }
//                    } else if (searchAnimationProgress == 0) {
//                        if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE) {
//                            filterTabsView.setTranslationY(actionBar.getTranslationY());
//                        }
//                    }
//                    canvas.drawRect(0, top, getMeasuredWidth(), top + actionBarHeight, searchAnimationProgress == 1f ? actionBarSearchPaint : actionBarDefaultPaint);
//                    if (searchAnimationProgress > 0 && searchAnimationProgress < 1f) {
//                        actionBarSearchPaint.setColor(ColorUtils.blendARGB(Theme.getColor(folderId == 0 ? Theme.key_actionBarDefault : Theme.key_actionBarDefaultArchived), Theme.getColor(Theme.key_windowBackgroundWhite), searchAnimationProgress));
//                        if (searchIsShowed || !searchWasFullyShowed) {
//                            canvas.save();
//                            canvas.clipRect(0, top, getMeasuredWidth(), top + actionBarHeight);
//                            float cX = getMeasuredWidth() - AndroidUtilities.dp(24);
//                            int statusBarH = actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0;
//                            float cY = statusBarH + (actionBar.getMeasuredHeight() - statusBarH) / 2f;
//                            canvas.drawCircle(cX, cY, getMeasuredWidth() * 1.3f * searchAnimationProgress, actionBarSearchPaint);
//                            canvas.restore();
//                        } else {
//                            canvas.drawRect(0, top, getMeasuredWidth(), top + actionBarHeight, actionBarSearchPaint);
//                        }
//                        if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE) {
//                            filterTabsView.setTranslationY(actionBarHeight - (actionBar.getHeight() + filterTabsView.getMeasuredHeight()));
//                        }
//                        if (searchTabsView != null) {
//                            float y = actionBarHeight - (actionBar.getHeight() + searchTabsView.getMeasuredHeight());
//                            float alpha;
//                            if (searchAnimationTabsDelayedCrossfade) {
//                                alpha = searchAnimationProgress < 0.5f ? 0 : (searchAnimationProgress - 0.5f) / 0.5f;
//                            } else {
//                                alpha = searchAnimationProgress;
//                            }
//
//                            searchTabsView.setTranslationY(y);
//                            searchTabsView.setAlpha(alpha);
//                            if (filtersView != null) {
//                                filtersView.setTranslationY(y);
//                                filtersView.setAlpha(alpha);
//                            }
//                        }
//                    }
//                } else if (!inPreviewMode) {
//                    if (progressToActionMode > 0) {
//                        actionBarSearchPaint.setColor(ColorUtils.blendARGB(Theme.getColor(folderId == 0 ? Theme.key_actionBarDefault : Theme.key_actionBarDefaultArchived), Theme.getColor(Theme.key_windowBackgroundWhite), progressToActionMode));
//                        canvas.drawRect(0, top, getMeasuredWidth(), top + actionBarHeight, actionBarSearchPaint);
//                    } else {
//                        canvas.drawRect(0, top, getMeasuredWidth(), top + actionBarHeight, actionBarDefaultPaint);
//                    }
//                }
//                tabsYOffset = 0;
//                if (filtersTabAnimator != null && filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE) {
//                    tabsYOffset = - (1f - filterTabsProgress) * filterTabsView.getMeasuredHeight();
//                    filterTabsView.setTranslationY(actionBar.getTranslationY() + tabsYOffset);
//                    filterTabsView.setAlpha(filterTabsProgress);
//                    viewPages[0].setTranslationY(-(1f - filterTabsProgress) * filterTabsMoveFrom);
//                } else if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE) {
//                    filterTabsView.setTranslationY(actionBar.getTranslationY());
//                    filterTabsView.setAlpha(1f);
//                }
//                updateContextViewPosition();
//                super.dispatchDraw(canvas);
//                if (whiteActionBar && searchAnimationProgress > 0 && searchAnimationProgress < 1f && searchTabsView != null) {
//                    windowBackgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
//                    windowBackgroundPaint.setAlpha((int) (windowBackgroundPaint.getAlpha() * searchAnimationProgress));
//                    canvas.drawRect(0, top + actionBarHeight, getMeasuredWidth(), top + actionBar.getMeasuredHeight() + searchTabsView.getMeasuredHeight(), windowBackgroundPaint);
//                }
//                if (fragmentContextView != null && fragmentContextView.isCallStyle()) {
//                    canvas.save();
//                    canvas.translate(fragmentContextView.getX(), fragmentContextView.getY());
//                    if (slideFragmentProgress != 1f) {
//                        float s = 1f - 0.05f * (1f - slideFragmentProgress);
//                        canvas.translate((isDrawerTransition ? AndroidUtilities.dp(4) : -AndroidUtilities.dp(4)) * (1f - slideFragmentProgress), 0);
//                        canvas.scale(s, 1f, isDrawerTransition ? getMeasuredWidth() : 0, fragmentContextView.getY());
//                    }
//                    fragmentContextView.setDrawOverlay(true);
//                    fragmentContextView.draw(canvas);
//                    fragmentContextView.setDrawOverlay(false);
//                    canvas.restore();
//                }
                if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
                    if (blurredView.getAlpha() != 1f) {
                        if (blurredView.getAlpha() != 0) {
                            canvas.saveLayerAlpha(blurredView.getLeft(), 0, blurredView.getRight(), blurredView.getBottom(), (int) (255 * blurredView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                            canvas.translate(blurredView.getLeft(), 0);
                            blurredView.draw(canvas);
                            canvas.restore();
                        }
                    } else {
                        blurredView.draw(canvas);
                    }
                }
                super.dispatchDraw(canvas);
//                if (scrimView != null) {
//                    canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);
//                    canvas.save();
//                    getLocationInWindow(pos);
//                    canvas.translate(scrimViewLocation[0] - pos[0], scrimViewLocation[1] - (Build.VERSION.SDK_INT < 21 ? AndroidUtilities.statusBarHeight : 0));
//                    scrimView.draw(canvas);
//                    if (scrimViewSelected) {
//                        Drawable drawable = filterTabsView.getSelectorDrawable();
//                        canvas.translate(-scrimViewLocation[0], -drawable.getIntrinsicHeight() - 1);
//                        drawable.draw(canvas);
//                    }
//                    canvas.restore();
//                }
            }
        };

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
                drawEndDayCircle(canvas);
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
        selectDaysButton.setBackground(Theme.createSelectorDrawable(0x333a8cce, 3)); // TODO(dkaraush): text!
        selectDaysButton.bringToFront();
        selectDaysButton.setOnClickListener(view -> switchSelectingDays(true));
        selectDaysButton.setAlpha(1f);
        selectDaysButton.setClickable(true);
        bottomButtonContainer.addView(selectDaysButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        clearHistoryButton = new TextView(context);
        clearHistoryButton.setText(LocaleController.getString("ClearHistory", R.string.ClearHistory).toUpperCase());
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
            if (begin.getValue() == null)
                return;

            askAndClearHistory(begin.getValue(), end.getValue());
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

        touchListener = new FrameLayout(context);
        touchListener.setOnTouchListener((view, motionEvent) -> this.onTouchEvent(motionEvent));
        contentView.addView(touchListener, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        if (BLEEP_SELECTED_DAY_AFTER_OPENING_CALENDAR) {
            selectedAt = System.currentTimeMillis();
            updateMonthsFor(950);
        }
        return fragmentView;
    }

    private void deleteMedia(Day from, Day to) {
        if (from == null || to == null)
            return;
        Day day = Day.min(from, to),
            max = Day.max(from, to);
        long maxTimestamp = max.getTimestamp(), now = System.currentTimeMillis();
        int duration = 250;
        for (; day.getTimestamp() <= maxTimestamp; day.nextDay()) {
            SparseArray<PeriodDay> monthMessages;
            if ((monthMessages = messagesByYearMounth.get(day.year * 100 + day.month)) != null) {
                PeriodDay periodDay;
                if ((periodDay = monthMessages.get(day.day)) != null) {
                    periodDay.deletedAt = now;
                    now += 30;
                    duration += 30;
                }
            }
        }
        updateMonthsFor(duration);
    }

    private void askAndClearHistory(Day from, Day to) {

        final Day minDay = Day.min(from, to),
                  maxDay = Day.max(from, to);

        long timestampA = minDay.getStartTimestamp(),
             timestampB = maxDay.getEndTimestamp(),
             startTimestamp = Math.min(timestampA, timestampB),
             endTimestamp =   Math.max(timestampA, timestampB);

        int days = Math.round((endTimestamp - startTimestamp) / (float) Day.DAY_LENGTH);
        TLRPC.User user = null;
        try {
            user = getMessagesController().getUser(DialogObject.isEncryptedDialog(dialogId) ? getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialogId)).user_id : dialogId);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (user != null && user.self)
            user = null;
        AlertsCreator.createDeleteMessagesInRangeAlert(this, days, user, alsoDeleteFor -> {
            TLRPC.TL_messages_deleteHistory req = new TLRPC.TL_messages_deleteHistory();
            req.revoke = alsoDeleteFor;
            req.peer = getMessagesController().getInputPeer(dialogId);

            int min_date = (int) (startTimestamp / 1000L),
                    max_date = (int) (endTimestamp / 1000L);

            req.min_date = min_date;
            req.flags |= 4;

            req.max_date = max_date;
            req.flags |= 8;

            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    if (response instanceof TLRPC.TL_messages_affectedHistory) {
                        TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                        getMessagesController().processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                        HashMap<Long, ArrayList<Integer>> deletedMessageIds = getMessagesStorage().markMessagesAsDeletedInDateRange(dialogId, min_date, max_date, false, false);
                        if (deletedMessageIds != null) {
                            for (HashMap.Entry<Long, ArrayList<Integer>> entry : deletedMessageIds.entrySet()) {
                                Long dialogId = entry.getKey();
                                ArrayList<Integer> messageIds = entry.getValue();
                                getMessagesStorage().updateDialogsWithDeletedMessages(dialogId, 0, messageIds, null, true);
                                AndroidUtilities.runOnUIThread(() -> {
                                    getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, messageIds, 0L, false, dialogId);
                                });
                            }
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            deleteMedia(minDay, maxDay);
                        });
                    }
                } else {
                    FileLog.e(error.toString());
//                    Toast.makeText(getParentActivity(), "Error deleting history!", Toast.LENGTH_SHORT).show();
                }

                AndroidUtilities.runOnUIThread(() -> {
                    switchSelectingDays(false);
                });
            });
        });
    }

    private ViewPropertyAnimator selectDaysAnimator = null;
    private ViewPropertyAnimator clearHistoryAnimator = null;
    private boolean selectingDays = false;
    private void switchSelectingDays(boolean value) {
        if (selectingDays == value)
            return;

        selectingDays = value;
        String actionBarTitle = selectingDays ? "Select days" : LocaleController.getString("Calendar", R.string.Calendar); // TODO(dkaraush): text!
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
            updateMonthsFor(216);
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
            if (error == null && response != null && response instanceof TLRPC.TL_messages_searchResultsCalendar) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                if (res.periods != null) {
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
                        int index = calendar.get(Calendar.DATE);
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

        public static final int CALENDAR_HEIGHT = 356;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(CALENDAR_HEIGHT), MeasureSpec.EXACTLY));
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
        private final Day thisDay = new Day(2021, 0, 0);

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            long now = System.currentTimeMillis();

            thisDay.year = currentYear;
            thisDay.month = currentMonthInYear;
            thisDay.day = 0;

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

                boolean selectedAnimation = false, isEndDay = false;
                float selectedAnimationT = 0f;
                boolean thisDaySelected = false;
                if (thisDay.equals(end.getValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = end.getAnimatedT();
                    thisDaySelected = true;
                    isEndDay = true;
                } else if (thisDay.equals(end.getOutValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = end.getOutAnimatedT();
                    isEndDay = true;
                }
                if (thisDay.equals(begin.getValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = begin.getAnimatedT();
                    thisDaySelected = true;
                    isEndDay = false;
                } else if (thisDay.equals(begin.getOutValue())) {
                    selectedAnimation = true;
                    selectedAnimationT = begin.getOutAnimatedT();
                    isEndDay = false;
                }
                boolean isSelected = thisDay.isBetween(rangeFrom, rangeTo) || (thisDaySelected && selectedAnimationT > 0.9f);
                Paint defaultTextPaint = isSelected ? boldBlackTextPaint : textPaint;
                if (selectedAnimation) {
                    if (!isEndDay) {
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44 - 8) / 2f * selectedAnimationT, selectedPaint);
                        selectedStrokePaint.setAlpha((int) (selectedAnimationT * 255));
                        canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44 - 12) + AndroidUtilities.dp(12) * selectedAnimationT) / 2f, selectedStrokePaint);
                    }
                    selectedStrokeBackgroundPaint.setAlpha((int) (selectedAnimationT * 255));
                    canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44 - 12 - 4) + AndroidUtilities.dp(12) * selectedAnimationT) / 2f, selectedStrokeBackgroundPaint);
                }

                if (BLEEP_SELECTED_DAY_AFTER_OPENING_CALENDAR) {
                    if (selectedYear == thisDay.year && selectedMonth == thisDay.month && selectedDay == thisDay.day) {
                        float t = Math.max(0f, Math.min(1f, (now - selectedAt - 500L) / 250f));
                        if (t >= 0f && t <= 1f) {
                            float alpha = (float) Math.abs(Math.sin(2 * t * Math.PI)) * 0.35f;
                            int oldAlpha = selectedPaint.getAlpha();
                            selectedPaint.setAlpha((int) (alpha * 255));
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, selectedPaint);
                            selectedPaint.setAlpha(oldAlpha);
                        }
                    }
                }

                if (nowTime < startMonthTime + (i + 1) * 86400) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else {
                    PeriodDay periodDay = messagesByDays == null ? null : messagesByDays.get(i, null);
                    float imageDeletionAnimation = periodDay == null || periodDay.deletedAt <= 0 ? 0f : Math.max(0f, Math.min(1f, (now - periodDay.deletedAt) / 75f));
                    boolean deleted = periodDay != null && periodDay.deletedAt + 75 < now && periodDay.deletedAt > 0;

                    if (imagesByDays != null && imagesByDays.get(i) != null && periodDay != null && !deleted) {
                        float alpha;
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
                        alpha = messagesByDays.get(i).enterAlpha * (1f - imageDeletionAnimation);
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s,cx, cy);
                        }
                        imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                        float imageSize = AndroidUtilities.dp(44 - (8 * Math.max(selectedInsideT, selectedAnimationT))) * (1f - imageDeletionAnimation);
                        imagesByDays.get(i).setImageCoords(cx - imageSize / 2f, cy - imageSize / 2f, imageSize, imageSize);
                        imagesByDays.get(i).draw(canvas);
                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, imageSize / 2f, blackoutPaint);
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f) {
                            canvas.restore();
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

    boolean pressed;
    long pressedTime;
    long pressId = 0;
    long movingEndDayPressId;
    float pressX = 0, pressY = 0;
    Runnable cancelHold;
    Point monthViewOffsetPoint = new Point(),
          listViewOffsetPoint = new Point();
    Rect monthViewRectBounds = new Rect(),
         listViewRectBounds = new Rect();
    Rect buttonVisibleRect = new Rect();

    boolean draggingEnd = false;

    public boolean onTouchEvent(MotionEvent event) {
        float rx = event.getRawX(), ry = event.getRawY();
        listView.getGlobalVisibleRect(listViewRectBounds, listViewOffsetPoint);
        if (ry < listViewRectBounds.top || ry > listViewRectBounds.bottom)
            return false;

        Day thisDay = null;
        float dayX = 0f, dayY = 0f;
        try {
            MonthView thisMonthView = null;

            LinearLayoutManager listLayoutManager = (LinearLayoutManager) listView.getLayoutManager();
            if (listLayoutManager != null) {
                for (int i = 0; i <= listLayoutManager.getChildCount(); ++i) {
                    View child = listLayoutManager.getChildAt(i);
                    if (child != null && child instanceof MonthView) {
                        MonthView monthView = (MonthView) child;
                        monthView.getGlobalVisibleRect(monthViewRectBounds, monthViewOffsetPoint);
//                        monthView.getDrawingRect(monthViewRectBounds);

                        if (ry >= monthViewOffsetPoint.y &&
                            ry <= monthViewOffsetPoint.y + AndroidUtilities.dp(MonthView.CALENDAR_HEIGHT)) {
                            thisMonthView = monthView;
                            break;
                        }
                    }
                }
            }

            if (thisMonthView != null) {
//                int r = listView.getChildCount() * AndroidUtilities.dp(MonthView.CALENDAR_HEIGHT),
//                    e = listView.getMeasuredHeight(),
                int s = listView.computeVerticalScrollOffset();

                float xStep = thisMonthView.getMeasuredWidth() / 7f;
                float yStep = AndroidUtilities.dp(44 + 8);

                int x = (int) Math.round(((rx - monthViewOffsetPoint.x) / xStep) - .5f),
                    y = (int) Math.round(((ry - monthViewOffsetPoint.y - AndroidUtilities.dp(44)) / yStep) - .5f);

                int day = (x + y * 7) - thisMonthView.startDayOfWeek;
                if (day >= 0 && day < thisMonthView.daysInMonth)
                    thisDay = new Day(thisMonthView.currentYear, thisMonthView.currentMonthInYear, day);

                float localDayX = (x + .5f) * xStep,
                      localDayY = (y + .5f) * yStep + AndroidUtilities.dp(44);

                dayX = localDayX;
                dayY = s + (monthViewOffsetPoint.y + localDayY - listViewOffsetPoint.y);
            }
        } catch (Exception e) {}

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            final long currentPressId = ++pressId;
            pressed = true;
            pressedTime = System.currentTimeMillis();
            pressX = rx;
            pressY = ry;
            if (!selectingDays) {
                if (thisDay != null && !thisDay.isInFuture() && cancelHold == null) {
                    final Day finalDay = thisDay;
                    contentView.postDelayed(() -> {
                        if (
                                pressed &&
                                pressId == currentPressId &&
                                ((pressX - rx) * (pressX - rx) + (pressY - ry) * (pressY - ry)) < 8f
                        ) {
                            listView.requestDisallowInterceptTouchEvent(true);
                            Runnable closeMessages = showMessagesPreview(finalDay);
                            cancelHold = () -> {
                                if (closeMessages != null)
                                    closeMessages.run();
                                if (cancelHold != null)
                                    cancelHold = null;
                            };
                        }
                    }, ViewConfiguration.getTapTimeout());

                    updateClearHistoryButton();
                    return true;
                }
            } else if (thisDay != null && !thisDay.isInFuture()) {
                movingEndDayPressId = pressId;
                if (begin.getValue() == null) {
                    begin.update(thisDay);
                    end.update(null);
                    endStrokePos.update(null);
                    endPosOpacity.update(new Alpha(0f));
                    listView.requestDisallowInterceptTouchEvent(true);
                    updateMonthsFor(216);
                } else if (begin.getValue() != null && (end.getValue() == null || begin.getValue().equals(end.getValue())) && !thisDay.equals(end.getValue())) {
                    end.set(begin.getValue());
                    end.update(thisDay);
                    endPos.update(new Pos(dayX, dayY));
                    endStrokePos.update(new Pos(dayX, dayY));
                    endPosOpacity.update(new Alpha(1f));
                    listView.requestDisallowInterceptTouchEvent(true);
                    updateMonthsFor(216);
                } else if (!draggingEnd && begin.getValue() != null && end.getValue() != null && (thisDay.equals(begin.getValue()) || thisDay.equals(end.getValue()))) {
                    boolean draggingBegin = thisDay.equals(begin.getValue());
                    draggingEnd = !draggingBegin && thisDay.equals(end.getValue());

                    if (draggingBegin) {
                        Day endWas = end.getValue();
                        end.set(begin.getValue());
                        begin.set(endWas);
                        draggingEnd = true;
                    }

                    if (draggingEnd) {
                        end.update(thisDay);
                        if (draggingBegin) {
                            endPos.set(new Pos(dayX, dayY));
                            endStrokePos.set(new Pos(dayX, dayY));
                            endPosOpacity.set(new Alpha(1f));
                        } else {
                            endPos.update(new Pos(dayX, dayY));
                            endStrokePos.update(new Pos(dayX, dayY));
                            endPosOpacity.update(new Alpha(1f));
                        }
                        listView.requestDisallowInterceptTouchEvent(true);
                        updateMonthsFor(216);
                    }
                } else if (begin.getValue() != null && end.getValue() != null && !begin.getValue().equals(end.getValue())) {
                    begin.update(thisDay);
                    end.update(null);
                    endPos.update(new Pos(dayX, dayY));
                    endStrokePos.update(new Pos(dayX, dayY));
                    endPosOpacity.update(new Alpha(0f));
                    listView.requestDisallowInterceptTouchEvent(true);
                    updateMonthsFor(216);
                } else {
                    return false;
                }

                updateClearHistoryButton();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            pressX = rx;
            pressY = ry;

            if (!selectingDays && cancelHold != null && previewMenu != null) {
                // action buttons
                for (int i = 0; i < previewMenu.getItemsCount(); ++i) {
                    ActionBarMenuSubItem button = (ActionBarMenuSubItem) previewMenu.getItemAt(i);
                    if (button != null && Build.VERSION.SDK_INT >= 21) {
                        RippleDrawable ripple = (RippleDrawable) button.getBackground();
                        button.getGlobalVisibleRect(buttonVisibleRect);
                        if (ripple != null && buttonVisibleRect != null) {
                            boolean shouldBeEnabled = buttonVisibleRect.contains((int) rx, (int) ry),
                                    enabled = ripple.getState().length == 2;
                            if (shouldBeEnabled != enabled)
                                ripple.setState(shouldBeEnabled ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[]{});
                        }
                    }
                }
            }

            if (selectingDays && thisDay != null && !thisDay.isInFuture() && movingEndDayPressId == pressId) {
                if (draggingEnd) {
                    if (!thisDay.equals(end.getValue())) {
                        end.update(thisDay);
                        endPos.update(new Pos(dayX, dayY));
                        endStrokePos.update(new Pos(dayX, dayY));
                        endPosOpacity.update(new Alpha(1f));
                        listView.requestDisallowInterceptTouchEvent(true);
                        updateMonthsFor(216);
                    }
                } else if (!thisDay.equals(end.getValue())) {
                    end.update(thisDay);
                    endPos.update(new Pos(dayX, dayY));
                    endStrokePos.update(new Pos(dayX, dayY));
                    endPosOpacity.update(new Alpha(1f));
                    listView.requestDisallowInterceptTouchEvent(true);
                    updateMonthsFor(216);
                } else {
                    return false;
                }

                updateClearHistoryButton();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            pressed = false;

            boolean buttonClicked = false;
            if (!selectingDays && cancelHold != null && previewMenu != null) {
                // action buttons
                for (int i = 0; i < previewMenu.getItemsCount(); ++i) {
                    ActionBarMenuSubItem button = (ActionBarMenuSubItem) previewMenu.getItemAt(i);
                    if (button != null) {
                        button.getGlobalVisibleRect(buttonVisibleRect);
                        if (buttonVisibleRect != null && buttonVisibleRect.contains((int) rx, (int) ry)) {
                            button.performClick();
                            buttonClicked = true;
                            break;
                        }
                    }
                }
            }

            if (cancelHold != null) {
                cancelHold.run();
                cancelHold = null;
            }

            draggingEnd = false;
            listView.requestDisallowInterceptTouchEvent(false);
            long pressDuration = System.currentTimeMillis() - pressedTime;
            if (!selectingDays && thisDay != null && !thisDay.isInFuture() && pressDuration < ViewConfiguration.getLongPressTimeout()) {
                switchSelectingDays(true);
                begin.update(thisDay);
                end.update(null);
                endStrokePos.update(null);
                endPosOpacity.update(new Alpha(0f));
                listView.requestDisallowInterceptTouchEvent(true);
                updateMonthsFor(216);
            }
        }

        updateClearHistoryButton();
        return false;
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

    private void drawEndDayCircle(Canvas canvas) {
        float scrollY = listView.computeVerticalScrollOffset();

        Alpha posAlpha = endPosOpacity.getAnimatedValue();
        if (posAlpha != null) {
            selectedStrokePaint.setAlpha((int) (posAlpha.value * 255));
        }

        Pos pos = endPos.getAnimatedValue();
        if (pos != null) {
            float scale = 1f - .8f * (float) Math.sin(endPos.getAnimatedT() * Math.PI);
            canvas.drawCircle(pos.x, pos.y - scrollY, AndroidUtilities.dp(44 - 8 ) / 2f * scale * end.getOpacity(), selectedStrokeBackgroundPaint);
            canvas.drawCircle(pos.x, pos.y - scrollY, AndroidUtilities.dp(44 - 8) / 2f * scale * end.getOpacity(), selectedPaint);
        }

        Pos strokePos = endStrokePos.getAnimatedValue();
        if (strokePos != null) {
            float scale = 1f - .1f * (float) Math.sin(endStrokePos.getAnimatedT() * Math.PI);
            canvas.drawCircle(strokePos.x, strokePos.y - scrollY, AndroidUtilities.dp(44) / 2f * scale * end.getOpacity(), selectedStrokePaint);
        }
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
        if (updateMonthesAnimator != null) {
            if (updateMonthesAnimator.isRunning() && updateMonthesAnimator.getDuration() - updateMonthesAnimator.getCurrentPlayTime() > duration)
                return;
            updateMonthesAnimator.cancel();
        }
        updateMonthesAnimator = ObjectAnimator.ofFloat(0f, 1f).setDuration(duration);
        updateMonthesAnimator.addUpdateListener((v) -> {
            listView.invalidate();
            LinearLayoutManager listLayoutManager = (LinearLayoutManager) listView.getLayoutManager();
            if (listLayoutManager != null) {
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
            if (listLayoutManager != null) {
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
            }
        });
        updateMonthesAnimator.start();
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;
        long deletedAt = 0;
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

    private Bitmap makeBlurOf(View view) {
        return makeBlurOf(view, 6f);
    }
    private Bitmap makeBlurOf(View view, float s) {
        int w = (int) (view.getMeasuredWidth() / 6.0f);
        int h = (int) (view.getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        view.draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        return bitmap;
    }

    ValueAnimator blurredAnimator;
    Drawable actionBarForeground;
    FrameLayout blurredView;
    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }

        blurredView.setBackground(new BitmapDrawable(makeBlurOf(fragmentView)));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
        blurredView.setClickable(false);

        if (actionBarForeground != null)
            actionBarForeground = null;
        actionBar.setForeground(actionBarForeground = new BitmapDrawable(makeBlurOf(actionBar)));
        actionBarForeground.setAlpha(255);

        if (blurredAnimator != null)
            blurredAnimator.cancel();
        blurredAnimator = ObjectAnimator.ofFloat(0f, 1f);
        blurredAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            if (blurredView != null)
                blurredView.setAlpha(t);
            if (actionBarForeground != null)
                actionBarForeground.setAlpha((int) (t * 255));
        });
        blurredAnimator.setDuration(125);
        blurredAnimator.start();
    }
    private void hideBlurBitmap() {
        if (blurredAnimator != null)
            blurredAnimator.cancel();
        blurredAnimator = ObjectAnimator.ofFloat(1f, 0f);
        blurredAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            if (blurredView != null)
                blurredView.setAlpha(t);
            if (actionBarForeground != null)
                actionBarForeground.setAlpha((int) (t * 255));
        });
        blurredAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                blurredView.setVisibility(View.GONE);
                actionBar.setForeground(null);
            }
        });
        blurredAnimator.setDuration(125);
        blurredAnimator.start();
    }

    private void tryToCloseMessagesPreview() {
        if (previewIsShown) {
            previewIsShown = false;
            hideBlurBitmap();
            finishPreviewFragment();
        }
    }

    private boolean previewIsShown = false;
    private Runnable showMessagesPreview(final Day day) {
        if (previewIsShown)
            return null;

        previewIsShown = true;

        Bundle bundle = new Bundle();
        bundle.putInt("chatMode", ChatActivity.MODE_DAY);
        bundle.putLong("dialog_id", dialogId);
        bundle.putLong("user_id", dialogId);
        bundle.putInt("messages_date_start", (int) (day.getStartTimestamp() / 1000L));
        bundle.putInt("messages_date_end",   (int) (day.getEndTimestamp() / 1000L));
        bundle.putBoolean("show_pinned_messages", false);
        bundle.putBoolean("preview_title", true);

        ChatActivity chatActivity = new ChatActivity(bundle);
        chatActivity.setInPreviewMode(true);

        previewMenu = getPreviewMenu();
        previewMenu.getItemAt(0).setOnClickListener(e -> {
//            tryToCloseMessagesPreview();
            contentView.postDelayed(() -> {
                this.finishFragment();
                if (jumpToDate != null)
                    jumpToDate.run((int) (day.getStartTimestamp() / 1000L));
            }, 170);
        });
        previewMenu.getItemAt(1).setOnClickListener(e -> {
            tryToCloseMessagesPreview();
            switchSelectingDays(true);
            begin.update(day);
            end.update(null);
            endStrokePos.update(null);
            endPosOpacity.update(new Alpha(0f));
            listView.requestDisallowInterceptTouchEvent(true);
            updateMonthsFor(216);
        });
        previewMenu.getItemAt(2).setOnClickListener(e -> {
            tryToCloseMessagesPreview();
            contentView.postDelayed(() -> {
                askAndClearHistory(day, day);
            }, 150);
        });

        prepareBlurBitmap();
        presentFragmentAsPreviewWithButtons(chatActivity, previewMenu);

        View chatFragment = chatActivity.getFragmentView();
        if (chatFragment == null) {
            tryToCloseMessagesPreview();
        } else {
            chatFragment.setEnabled(false);
        }

        return this::tryToCloseMessagesPreview;
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

        previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, getResourceProvider());
        previewMenu.setMinimumWidth(AndroidUtilities.dp(200));
        previewMenu.setAnimationEnabled(true);
//        Rect backgroundPaddings = new Rect();
//        Drawable shadowDrawable = getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
//        shadowDrawable.getPadding(backgroundPaddings);
        if (Build.VERSION.SDK_INT >= 21) {
            previewMenu.setElevation(AndroidUtilities.dp(4));
        }
        previewMenu.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        ActionBarMenuSubItem cell1 = new ActionBarMenuSubItem(getParentActivity(), true, false, getResourceProvider());
        cell1.setTextAndIcon("Jump to date", R.drawable.msg_message); // TODO(dkaraush): text!
        cell1.setItemHeight(46);
        cell1.setTag(R.id.width_tag, 240);
        cell1.setClickable(true);
        previewMenu.addView(cell1);

        ActionBarMenuSubItem cell2 = new ActionBarMenuSubItem(getParentActivity(), false, false, getResourceProvider());
        cell2.setTextAndIcon("Select this day", R.drawable.msg_select);
        cell2.setItemHeight(46);
        cell2.setTag(R.id.width_tag, 240);
        cell2.setClickable(true);
        previewMenu.addView(cell2);

        ActionBarMenuSubItem cell3 = new ActionBarMenuSubItem(getParentActivity(), false, true, getResourceProvider());
        cell3.setTextAndIcon("Clear history", R.drawable.msg_delete); // TODO(dkaraush): text!
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

            if (cancelHold != null) {
                cancelHold.run();
                cancelHold = null;
            }
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        inTransitionAnimation = false;
        if (parentLayout != null && !parentLayout.isInPreviewMode()) {
            hideBlurBitmap();
            previewIsShown = false;

            if (cancelHold != null) {
                cancelHold.run();
                cancelHold = null;
            }
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
        if (b == null) return a;
        if (a.year != b.year) return (a.year < b.year ? a : b);
        if (a.month != b.month) return (a.month < b.month ? a : b);
        if (a.day != b.day) return (a.day < b.day ? a : b);
        return a;
    }
    public static Day max(Day a, Day b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.year != b.year) return (a.year > b.year ? a : b);
        if (a.month != b.month) return (a.month > b.month ? a : b);
        if (a.day != b.day) return (a.day > b.day ? a : b);
        return a;
    }

    public long getStartTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day + 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
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
        calendar.set(Calendar.DAY_OF_MONTH, day + 1);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
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
                calendar.get(Calendar.DAY_OF_MONTH) - 1,
                (
                    calendar.get(Calendar.HOUR) +
                    calendar.get(Calendar.MINUTE) / 60f +
                    calendar.get(Calendar.SECOND) / 3600f
                ) / 24f
        );
    }
    public void setFromTimestamp(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        this.year = calendar.get(Calendar.YEAR);
        this.month = calendar.get(Calendar.MONTH);
        this.day = calendar.get(Calendar.DAY_OF_MONTH) - 1;
        this.offset = (
            calendar.get(Calendar.HOUR) +
            calendar.get(Calendar.MINUTE) / 60f +
            calendar.get(Calendar.SECOND) / 3600f
        ) / 24f;
    }
    public void nextDay() {
        this.setFromTimestamp(this.getTimestamp() + DAY_LENGTH);
    }

    public int daysInMonth() {
        return YearMonth.of(year, month).lengthOfMonth();
    }

    public Day lerp(Day to, float t) {
        long fromTimestamp = this.getTimestamp(),
             toTimestamp =   to.getTimestamp();
        return Day.fromTimestamp((fromTimestamp + (long) ((toTimestamp - fromTimestamp) * t)));
    }

    public boolean isBetween(Day from, Day to) {
        if (from == null || to == null)
            return false;
        long mine = getTimestamp();
        return mine >= from.getStartTimestamp() && mine <= to.getEndTimestamp();
    }

    public boolean isInFuture() {
        return getStartTimestamp() > System.currentTimeMillis();
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

    public void set(T newValue) {
        oldValue = value = newValue;
        switchedAt = System.currentTimeMillis() - this.duration;
    }

    private float interpolate(float t) {
        if (interpolation == null)
            return t;
        switch (interpolation) {
            case NONE:
                return t;
            case EASE_OUT_BACK:
                float G = 1.05f; // 1.70158f;
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


    public float getOpacity() {
        if (oldValue == null && value != null)
            return getAnimatedT();
        if (value == null && oldValue != null)
            return getOutAnimatedT();
        if (value != null)
            return 1f;
        return 0f;
    }
}