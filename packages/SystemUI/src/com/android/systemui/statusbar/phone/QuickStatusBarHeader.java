
/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.os.UserManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto;
import com.android.keyguard.KeyguardStatusView;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.qs.QSPanel.Callback;
import com.android.systemui.qs.QuickQSPanel;
import com.android.systemui.qs.TouchAnimator;
import com.android.systemui.qs.TouchAnimator.Builder;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.EmergencyListener;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.NextAlarmController.NextAlarmChangeCallback;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserInfoController.OnUserInfoChangedListener;

public class QuickStatusBarHeader extends BaseStatusBarHeader implements
        NextAlarmChangeCallback, OnClickListener, OnUserInfoChangedListener, EmergencyListener,
        SignalCallback {

    private static final String TAG = "QuickStatusBarHeader";

    private static final float EXPAND_INDICATOR_THRESHOLD = .93f;

    private ActivityStarter mActivityStarter;
    private NextAlarmController mNextAlarmController;
    private SettingsButton mSettingsButton;
    protected View mSettingsContainer;

    private TextView mAlarmStatus;
    private View mAlarmStatusCollapsed;

    private QSPanel mQsPanel;

    private boolean mExpanded;
    private boolean mAlarmShowing;

    private ViewGroup mDateTimeGroup;
    private ViewGroup mDateTimeAlarmGroup;
    private ViewGroup mDateTimeAlarmCenterGroup;
    private TextView mEmergencyOnly;

    protected ExpandableIndicator mExpandIndicator;

    private boolean mListening;
    private AlarmManager.AlarmClockInfo mNextAlarm;

    private QuickQSPanel mHeaderQsPanel;
    private boolean mShowEmergencyCallsOnly;
    protected MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;


    private TouchAnimator mAnimator;
    protected TouchAnimator mSettingsAlpha;
    private float mExpansionAmount;
    private QSTileHost mHost;
    private View mEdit;
    private boolean mShowFullAlarm;
    private float mDateTimeTranslation;
    private SparseBooleanArray mRoamingsBySubId = new SparseBooleanArray();
    private boolean mIsRoaming;
    private HorizontalScrollView mQuickQsPanelScroller;

    private boolean isSettingsIcon;
    private boolean isSettingsExpanded;
    private boolean isEdit;
    private boolean isExpandIndicator;
    private boolean isMultiUserSwitch;
    private boolean mDateTimeGroupCenter;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mEmergencyOnly = (TextView) findViewById(R.id.header_emergency_calls_only);

        mEdit = findViewById(android.R.id.edit);
        findViewById(android.R.id.edit).setOnClickListener(view ->
                mHost.startRunnableDismissingKeyguard(() -> mQsPanel.showEdit(view)));

        mDateTimeAlarmGroup = (ViewGroup) findViewById(R.id.date_time_alarm_group);
        mDateTimeAlarmGroup.setOnClickListener(this);
        mDateTimeAlarmGroup.findViewById(R.id.empty_time_view).setVisibility(View.GONE);
        mDateTimeAlarmCenterGroup = (ViewGroup) findViewById(R.id.date_time_alarm_center_group);
        mDateTimeAlarmCenterGroup.setVisibility(View.GONE);
        mDateTimeGroup = (ViewGroup) findViewById(R.id.date_time_group);
        mDateTimeGroup.setOnClickListener(this);
        mDateTimeGroup.setPivotX(0);
        mDateTimeGroup.setPivotY(0);
        mDateTimeTranslation = getResources().getDimension(R.dimen.qs_date_time_translation);
        mShowFullAlarm = getResources().getBoolean(R.bool.quick_settings_show_full_alarm);

        mExpandIndicator = (ExpandableIndicator) findViewById(R.id.expand_indicator);

        mHeaderQsPanel = (QuickQSPanel) findViewById(R.id.quick_qs_panel);
        mQuickQsPanelScroller = (HorizontalScrollView) findViewById(R.id.quick_qs_panel_scroll);
        mQuickQsPanelScroller.setHorizontalScrollBarEnabled(false);

        mSettingsButton = (SettingsButton) findViewById(R.id.settings_button);
        mSettingsContainer = findViewById(R.id.settings_button_container);
        mSettingsButton.setOnClickListener(this);

        mAlarmStatusCollapsed = findViewById(R.id.alarm_status_collapsed);
        mAlarmStatus = (TextView) findViewById(R.id.alarm_status);
        mAlarmStatus.setOnClickListener(this);

        mMultiUserSwitch = (MultiUserSwitch) findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = (ImageView) mMultiUserSwitch.findViewById(R.id.multi_user_avatar);

        // RenderThread is doing more harm than good when touching the header (to expand quick
        // settings), so disable it for this view
        ((RippleDrawable) mSettingsButton.getBackground()).setForceSoftware(true);
        ((RippleDrawable) mExpandIndicator.getBackground()).setForceSoftware(true);

        updateResources();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    private void updateResources() {
        FontSizeUtils.updateFontSize(mAlarmStatus, R.dimen.qs_date_collapsed_size);
        FontSizeUtils.updateFontSize(mEmergencyOnly, R.dimen.qs_emergency_calls_only_text_size);

        Builder builder = new Builder()
                .addFloat(mShowFullAlarm ? mAlarmStatus : findViewById(R.id.date), "alpha", 0, 1)
                .addFloat(mEmergencyOnly, "alpha", 0, 1);
        if (mShowFullAlarm) {
            builder.addFloat(mAlarmStatusCollapsed, "alpha", 1, 0);
        }
        mAnimator = builder.build();

        updateSettingsAnimator();
    }

    protected void updateSettingsAnimator() {
        mSettingsAlpha = new TouchAnimator.Builder()
                .addFloat(mEdit, "alpha", 0, 1)
                .addFloat(mMultiUserSwitch, "alpha", 0, 1)
                .build();

        final boolean isRtl = isLayoutRtl();
        if (isRtl && mDateTimeGroup.getWidth() == 0) {
            mDateTimeGroup.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    mDateTimeGroup.setPivotX(getWidth());
                    mDateTimeGroup.removeOnLayoutChangeListener(this);
                }
            });
        } else {
            mDateTimeGroup.setPivotX(isRtl ? mDateTimeGroup.getWidth() : 0);
        }
    }

    private void updateDateTimeCenter() {
        mDateTimeGroupCenter = isDateTimeGroupCenter();
	if (mDateTimeGroupCenter && (!(isSettingsIcon || isSettingsExpanded) || !isEdit || !isMultiUserSwitch || !isExpandIndicator)) {
            mDateTimeAlarmGroup.setVisibility(View.GONE);
            mDateTimeAlarmCenterGroup.setVisibility(View.VISIBLE);
        } else {
            mDateTimeAlarmCenterGroup.setVisibility(View.GONE);
            mDateTimeAlarmGroup.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getCollapsedHeight() {
        return getHeight();
    }

    @Override
    public int getExpandedHeight() {
        return getHeight();
    }

    @Override
    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        mHeaderQsPanel.setExpanded(expanded);
        updateEverything();
    }

    @Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarm = nextAlarm;
        if (nextAlarm != null) {
            String alarmString = KeyguardStatusView.formatNextAlarm(getContext(), nextAlarm);
            mAlarmStatus.setText(alarmString);
            mAlarmStatus.setContentDescription(mContext.getString(
                    R.string.accessibility_quick_settings_alarm, alarmString));
            mAlarmStatusCollapsed.setContentDescription(mContext.getString(
                    R.string.accessibility_quick_settings_alarm, alarmString));
        }
        if (mAlarmShowing != (nextAlarm != null)) {
            mAlarmShowing = nextAlarm != null;
            updateEverything();
        }
    }

    @Override
    public void setExpansion(float headerExpansionFraction) {
        mExpansionAmount = headerExpansionFraction;
        updateDateTimePosition();
        mAnimator.setPosition(headerExpansionFraction);
        mSettingsAlpha.setPosition(headerExpansionFraction);

        updateAlarmVisibilities();

        mExpandIndicator.setExpanded(headerExpansionFraction > EXPAND_INDICATOR_THRESHOLD);
    }

    @Override
    protected void onDetachedFromWindow() {
        setListening(false);
        mHost.getUserInfoController().remListener(this);
        mHost.getNetworkController().removeEmergencyListener(this);
        super.onDetachedFromWindow();
    }

    private void updateAlarmVisibilities() {
        mAlarmStatus.setVisibility(mAlarmShowing && mShowFullAlarm ? View.VISIBLE : View.INVISIBLE);
        mAlarmStatusCollapsed.setVisibility(mAlarmShowing ? View.VISIBLE : View.INVISIBLE);
    }

    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mHeaderQsPanel.setListening(listening);
        mListening = listening;
        updateListeners();
    }

    @Override
    public void updateEverything() {
        post(() -> {
            updateVisibilities();
            updateClickables();
            setClickable(false);
        });
    }

    protected void updateVisibilities() {
        updateAlarmVisibilities();
        updateDateTimePosition();
        mEmergencyOnly.setVisibility(mExpanded && (mShowEmergencyCallsOnly || mIsRoaming)
                ? View.VISIBLE : View.INVISIBLE);
        final boolean isDemo = UserManager.isDeviceInDemoMode(mContext);
        mMultiUserSwitch.setVisibility(mExpanded && mMultiUserSwitch.hasMultipleUsers() && !isDemo
                ? View.VISIBLE : View.GONE);
        isEdit = isEditEnabled();
        mEdit.setVisibility(!isEdit || isDemo || !mExpanded ? View.GONE : View.VISIBLE);
        isSettingsIcon = isSettingsIconEnabled();
        isSettingsExpanded = isSettingsExpandedEnabled();
        mSettingsButton.setVisibility(mExpanded && isSettingsExpanded || isSettingsIcon
                ? View.VISIBLE : View.GONE);
        mSettingsContainer.setVisibility(
                mExpanded && isSettingsExpanded || isSettingsIcon ? View.VISIBLE : View.GONE);
        isExpandIndicator = isExpandIndicatorEnabled();
        mExpandIndicator.setVisibility(isExpandIndicator ? View.VISIBLE : View.GONE);
        isMultiUserSwitch = isMultiUserSwitchEnabled();
        mMultiUserSwitch.setVisibility(isMultiUserSwitch ? View.VISIBLE : View.GONE);
        mMultiUserAvatar.setVisibility(isMultiUserSwitch ? View.VISIBLE : View.GONE);
    }

    private void updateDateTimePosition() {
        mDateTimeAlarmGroup.setTranslationY(mShowEmergencyCallsOnly || mIsRoaming
                ? mExpansionAmount * mDateTimeTranslation : 0);
        updateDateTimeCenter();
    }

    private void updateClickables() {
        mDateTimeAlarmGroup.setClickable(mExpanded);
    }

    private void updateListeners() {
        if (mListening) {
            mNextAlarmController.addStateChangedCallback(this);
            if (mHost.getNetworkController().hasVoiceCallingFeature()) {
                mHost.getNetworkController().addEmergencyListener(this);
                mHost.getNetworkController().addSignalCallback(this);
            }
        } else {
            mNextAlarmController.removeStateChangedCallback(this);
            mHost.getNetworkController().removeEmergencyListener(this);
            mHost.getNetworkController().removeSignalCallback(this);
        }
    }
    @Override
    public void setActivityStarter(ActivityStarter activityStarter) {
        mActivityStarter = activityStarter;
    }

    @Override
    public void setQSPanel(final QSPanel qsPanel) {
        mQsPanel = qsPanel;
        setupHost(qsPanel.getHost());
        if (mQsPanel != null) {
            mMultiUserSwitch.setQsPanel(qsPanel);
        }
    }

    public void setupHost(final QSTileHost host) {
        mHost = host;
        host.setHeaderView(mExpandIndicator);
        mHeaderQsPanel.setQSPanelAndHeader(mQsPanel, this);
        mHeaderQsPanel.setHost(host, null /* No customization in header */);
        setUserInfoController(host.getUserInfoController());
        setBatteryController(host.getBatteryController());
        setNextAlarmController(host.getNextAlarmController());

        final boolean isAPhone = mHost.getNetworkController().hasVoiceCallingFeature();
        if (isAPhone) {
            mHost.getNetworkController().addEmergencyListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mSettingsButton) {
            MetricsLogger.action(mContext,
                    mExpanded ? MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH
                            : MetricsProto.MetricsEvent.ACTION_QS_COLLAPSED_SETTINGS_LAUNCH);
            startSettingsActivity();
            }
        if (v == mAlarmStatus && mNextAlarm != null) {
            PendingIntent showIntent = mNextAlarm.getShowIntent();
            mActivityStarter.startPendingIntentDismissingKeyguard(showIntent);
        } else if (v == mDateTimeAlarmGroup) {
            startDateActivity();
        } else if (v == mDateTimeGroup) {
            startClockActivity();
        }
    }

    private void startClockActivity() {
        mActivityStarter.startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS),
                true /* dismissShade */);
    }

    private void startDateActivity() {
        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        builder.appendPath("time");
        ContentUris.appendId(builder, System.currentTimeMillis());
        Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startSettingsActivity() {
        mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                true /* dismissShade */);
    }

    @Override
    public void setNextAlarmController(NextAlarmController nextAlarmController) {
        mNextAlarmController = nextAlarmController;
    }

    @Override
    public void setBatteryController(BatteryController batteryController) {
        // Don't care
    }

    @Override
    public void setUserInfoController(UserInfoController userInfoController) {
        userInfoController.addListener(this);
    }

    @Override
    public void setCallback(Callback qsPanelCallback) {
        mHeaderQsPanel.setCallback(qsPanelCallback);
    }

    @Override
    public void setEmergencyCallsOnly(boolean show) {
        boolean changed = show != mShowEmergencyCallsOnly;
        if (changed) {
            mShowEmergencyCallsOnly = show;
            if (mExpanded) {
                updateEverything();
            }
        }
    }

    @Override
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
            String description, boolean isWide, int subId, boolean roaming) {
        mRoamingsBySubId.put(subId, roaming);
        boolean isRoaming = calculateRoaming();
        if (mIsRoaming != isRoaming) {
            mIsRoaming = isRoaming;
            mEmergencyOnly.setText(mIsRoaming ? R.string.accessibility_data_connection_roaming
                    : com.android.internal.R.string.emergency_calls_only);
            if (mExpanded) {
                updateEverything();
            }
        }
    }

    private boolean calculateRoaming() {
        for (int i = 0; i < mRoamingsBySubId.size(); i++) {
            if (mRoamingsBySubId.valueAt(i)) return true;
        }
        return false;
    }

    @Override
    public void onUserInfoChanged(String name, Drawable picture) {
        mMultiUserAvatar.setImageDrawable(picture);
    }

    public boolean isSettingsIconEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_SETTINGS_ICON_TOGGLE, 1) == 1;
    }

    public boolean isSettingsExpandedEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_SETTINGS_EXPANDED_TOGGLE, 0) == 1;
    }

    public boolean isEditEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_EDIT_TOGGLE, 1) == 1;
    }

    public boolean isExpandIndicatorEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_EXPAND_INDICATOR_TOGGLE, 1) == 1;
    }

    public boolean isMultiUserSwitchEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_MULTIUSER_SWITCH_TOGGLE, 1) == 1;
            }
        }
        if (mHeaderQsPanel != null) {
            mHeaderQsPanel.updateSettings();
        }
    }

    @Override
    public void onClosingFinished() {
        mQuickQsPanelScroller.scrollTo(0, 0);
>>>>>>> cdb7edb... All tile scroller for quickbar settings [1/2]
    }

    public boolean isDateTimeGroupCenter() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.QS_DATE_TIME_CENTER, 1) == 1;
    }

    @Override
    public void updateSettings() {
        if (mQsPanel != null) {
            mQsPanel.updateSettings();
        }
        if (mHeaderQsPanel != null) {
            mHeaderQsPanel.updateSettings();
        }
    }
}
