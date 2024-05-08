/*
 * Copyright (C) 2020 The LineageOS Project
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
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.service.quicksettings.Tile;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.res.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.BatteryController;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import vendor.lineage.powershare.V1_0.IPowerShare;

import java.util.NoSuchElementException;

import javax.inject.Inject;

public class PowerShareTile extends QSTileImpl<QSTile.BooleanState>
        implements BatteryController.BatteryStateChangeCallback {

    public static final String TILE_SPEC = "powershare";

    private IPowerShare mPowerShare;
    private BatteryController mBatteryController;
    private NotificationManager mNotificationManager;
    private Notification mNotification;
    private static final String CHANNEL_ID = "powershare";
    private static final int NOTIFICATION_ID = 273298;

    private boolean mLowPowerMode;

    @Inject
    public PowerShareTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            BatteryController batteryController) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mPowerShare = getPowerShare();
        if (mPowerShare == null) return;

        mBatteryController = batteryController;
        mNotificationManager = mContext.getSystemService(NotificationManager.class);

        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID,
                mContext.getString(R.string.quick_settings_powershare_label),
                NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(notificationChannel);

        Notification.Builder builder = new Notification.Builder(mContext, CHANNEL_ID);
        builder.setContentTitle(
                mContext.getString(R.string.quick_settings_powershare_enabled_label));
        builder.setSmallIcon(R.drawable.ic_qs_powershare);
        builder.setOnlyAlertOnce(true);
        mNotification = builder.build();
        mNotification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        mNotification.visibility = Notification.VISIBILITY_PUBLIC;

        batteryController.addCallback(this);
    }

    @Override
    public boolean isAvailable() {
        return mPowerShare != null;
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = false;
        return state;
    }

    @Override
    public void handleClick(@Nullable View view) {
        if (getState().state == Tile.STATE_UNAVAILABLE) {
            return;
        }
        final boolean prevState = isPowerShareEnabled();
        refreshState(!prevState);
        setEnabled(!prevState);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        if (mLowPowerMode) {
            return mContext.getString(R.string.quick_settings_powershare_off_powersave_label);
        } else if (getBatteryLevel() < getMinBatteryLevel()) {
            return mContext.getString(R.string.quick_settings_powershare_off_low_battery_label);
        }

        return mContext.getString(R.string.quick_settings_powershare_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_powershare);
        state.label = getTileLabel();
        if (arg instanceof Boolean) {
            boolean value = (Boolean) arg;
            if (value != state.value) {
                state.value = value;
            }
        } else {
            state.value = isPowerShareEnabled();
        }
        if (mLowPowerMode || getBatteryLevel() < getMinBatteryLevel()) {
            state.state = Tile.STATE_UNAVAILABLE;
            handleNotification(false);
        } else {
            state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
            handleNotification(state.value);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EVEREST;
    }

    @Override
    public void handleSetListening(boolean listening) { }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        if (isPowerSave) setEnabled(false);
        mLowPowerMode = isPowerSave;
        refreshState();
    }

    private synchronized IPowerShare getPowerShare() {
        try {
            return IPowerShare.getService();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NoSuchElementException ex) {
            // service not available
        }

        return null;
    }

    private int getMinBatteryLevel() {
        try {
            return mPowerShare.getMinBattery();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }

        return 0;
    }

    private int getBatteryLevel() {
        BatteryManager bm = mContext.getSystemService(BatteryManager.class);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private boolean isPowerShareEnabled() {
        try {
            return mPowerShare.isEnabled();
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }

        return false;
    }

    private boolean setEnabled(boolean enable) {
        try {
            return mPowerShare.setEnabled(enable);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }

        return false;
    }

    private void handleNotification(boolean enable) {
        if (enable) {
            mNotificationManager.notify(NOTIFICATION_ID, mNotification);
        } else {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
    }
}
