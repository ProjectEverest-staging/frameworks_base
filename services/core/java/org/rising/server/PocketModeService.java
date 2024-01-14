/*
 * Copyright (C) 2023 The RisingOS Android Project
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
package org.rising.server;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager; 
import android.os.UserHandle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.android.internal.R;
import com.android.server.SystemService;

public class PocketModeService extends SystemService {

    private static final float PROXIMITY_THRESHOLD = 1.0f;
    private static final float LIGHT_THRESHOLD = 2.0f;
    private static final float GRAVITY_THRESHOLD = -0.6f;
    private static final int MIN_INCLINATION = 75;
    private static final int MAX_INCLINATION = 100;

    private float[] gravityValues;
    private float proximitySensorValue;
    private float lightSensorValue;
    private int inclinationAngle;

    private Context mContext;
    private View mOverlayView;
    private WindowManager mWindowManager;
    private GestureDetector mGestureDetector;
    private PowerManager mPowerManager;
    private WindowManager.LayoutParams mLayoutParams;
    private TelephonyManager mTelephonyManager;

    private BroadcastReceiver mScreenStateReceiver;
    private SettingsObserver mSettingsObserver;

    private static final String POCKET_MODE_ENABLED = "pocket_mode_enabled";
    private final Handler mHandler = new Handler();

    private SensorManager mSensorManager;
    private Sensor mAccelerometerSensor;
    private Sensor mProximitySensor;
    private Sensor mLightSensor;
    boolean mIsInPocket;
    boolean mIsUnlocked;
    
    private boolean mAttached = false;
    private KeyguardManager mKeyguardManager;

    private static final int POCKET_MODE_SENSOR_DELAY = 400000;
    
    private VibrationEffect mDoubleClickEffect;
    private Vibrator mVibrator;

    public PocketModeService(Context context) {
        super(context);
        mContext = context;
    }

    private boolean isPocketModeEnabled() {
        return Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                POCKET_MODE_ENABLED,
                0, ActivityManager.getCurrentUser()) == 1;
    }

    private void registerListeners() {
        registerSensorListeners();
        registerScreenStateReceiver(mContext);
        initializeGestureDetector(mContext);
        mOverlayView.setOnTouchListener(mOverlayTouchListener);
    }

    private void unregisterListeners() {
        unregisterSensorListeners();
        unregisterScreenStateReceiver(mContext);
        mGestureDetector = null;
        mOverlayView.setOnTouchListener(null);
    }

    private void registerScreenStateReceiver(Context context) {
        mScreenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case Intent.ACTION_SCREEN_ON:
                    case Intent.ACTION_SCREEN_OFF:
                        mIsUnlocked = false;
                        if (mIsInPocket && !mIsUnlocked) {
                            showOverlay();
                        } else {
                            hideOverlay();
                        }
                        break;
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(mScreenStateReceiver, filter, null, mHandler);
    }

    private void unregisterScreenStateReceiver(Context context) {
        if (mScreenStateReceiver != null && context != null) {
            context.unregisterReceiver(mScreenStateReceiver);
            mScreenStateReceiver = null;
        }
    }

    private Runnable mDismissOverlayRunnable = new Runnable() {
        @Override
        public void run() {
           if (isDeviceOnKeyguard()) {
               hideOverlay();
               if (mPowerManager != null) {
                   mPowerManager.goToSleep(SystemClock.uptimeMillis());
               }
           }
        }
    };

    private void showOverlay() {
        final Runnable show = new Runnable() {
            @Override
            public void run() {
                if (!isCallInProgress() && mWindowManager != null && !mAttached && isDeviceOnKeyguard()) {
                    mWindowManager.addView(mOverlayView, mLayoutParams);
                    mOverlayView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
                    mAttached = true;
                }
            }
        };
        mHandler.post(show);
    }

    private void hideOverlay() {
        final Runnable hide = new Runnable() {
            @Override
            public void run() {
                if (mWindowManager != null && mAttached) {
                    mOverlayView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                    mWindowManager.removeView(mOverlayView);
                    mAttached = false;
                }
            }
        };
        mHandler.post(hide);
    }

    private View.OnTouchListener mOverlayTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            mGestureDetector.onTouchEvent(event);
            return true;
        }
    };

    private void registerSensorListeners() {
        if (mSensorManager != null) {
            if (mAccelerometerSensor != null) {
                mSensorManager.registerListener(mPocketModeListener, mAccelerometerSensor, POCKET_MODE_SENSOR_DELAY, mHandler);
            }
            if (mProximitySensor != null) {
                mSensorManager.registerListener(mPocketModeListener, mProximitySensor, POCKET_MODE_SENSOR_DELAY, mHandler);
            }
            if (mLightSensor != null) {
                mSensorManager.registerListener(mPocketModeListener, mLightSensor, POCKET_MODE_SENSOR_DELAY, mHandler);
            }
        }
    }

    private void unregisterSensorListeners() {
        if (mSensorManager != null && mPocketModeListener != null) {
            mSensorManager.unregisterListener(mPocketModeListener);
        }
    }
    
    private final SensorEventListener mPocketModeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                gravityValues = sensorEvent.values.clone();
                double gravityMagnitude = Math.sqrt(
                        gravityValues[0] * gravityValues[0] +
                                gravityValues[1] * gravityValues[1] +
                                gravityValues[2] * gravityValues[2]);

                gravityValues[0] = (float) (gravityValues[0] / gravityMagnitude);
                gravityValues[1] = (float) (gravityValues[1] / gravityMagnitude);
                gravityValues[2] = (float) (gravityValues[2] / gravityMagnitude);

                inclinationAngle = (int) Math.round(Math.toDegrees(Math.acos(gravityValues[2])));
            }

            if (sensorEvent.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                proximitySensorValue = sensorEvent.values[0];
            }

            if (sensorEvent.sensor.getType() == Sensor.TYPE_LIGHT) {
                lightSensorValue = sensorEvent.values[0];
            }

            if (proximitySensorValue != -1 && lightSensorValue != -1 && inclinationAngle != -1 && gravityValues != null) {
                detect(proximitySensorValue, lightSensorValue, gravityValues, inclinationAngle);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private float round(float value) {
        return Math.round(value * 100.0) / 100.0f;
    }

    public void detect(Float prox, Float light, float[] g, Integer inc) {
        boolean isProxInPocket = mProximitySensor != null && prox != -1f && prox < PROXIMITY_THRESHOLD;
        boolean isLightInPocket = mLightSensor != null && light != -1f && light < LIGHT_THRESHOLD;
        boolean isGravityInPocket = mAccelerometerSensor != null && g != null && g.length == 3 && g[1] < GRAVITY_THRESHOLD;
        boolean isInclinationInPocket= mAccelerometerSensor != null && inc != -1 && (inc > MIN_INCLINATION || inc < MAX_INCLINATION);

        mIsInPocket = isProxInPocket;
        if (!mIsInPocket) {
            mIsInPocket = isLightInPocket && isGravityInPocket && isInclinationInPocket;
        }
        if (!mIsInPocket) {
            mIsInPocket = isGravityInPocket && isInclinationInPocket;
        }
        if (mIsInPocket && !mIsUnlocked) {
            showOverlay();
        } else {
            hideOverlay();
        }
    }

    @Override
    public void onStart() {
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (mSensorManager != null) {
            mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        }
        mDoubleClickEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK);
        createOverlayView(mContext);
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(POCKET_MODE_ENABLED), false, this,
                    UserHandle.USER_ALL);
            updatePocketModeSettings();
        }
        void updatePocketModeSettings() {
             if (isPocketModeEnabled()) {
                registerListeners();
             } else {
                unregisterListeners();
             }
        }
        @Override
        public void onChange(boolean selfChange) {
            updatePocketModeSettings();
        }
    }

    private void createOverlayView(Context context) {
        mOverlayView = View.inflate(context, R.layout.pocket_mode_layout, null);
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        mLayoutParams.gravity = Gravity.CENTER;
        mLayoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        mOverlayView.setLayoutParams(mLayoutParams);
        mOverlayView.setBackgroundColor(Color.argb(224, 0, 0, 0));
    }

    private void vibrate(VibrationEffect effect) {
        if (mVibrator != null) {
            mVibrator.vibrate(effect);
        }
    }

    private void initializeGestureDetector(Context context) {
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                vibrate(mDoubleClickEffect);
                hideOverlay();
                mIsUnlocked = true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (mPowerManager != null) {
                    hideOverlay();
                    mPowerManager.goToSleep(SystemClock.uptimeMillis());
                }
                return true;
            }
        });
    }
    
    private boolean isDeviceOnKeyguard() {
        return mKeyguardManager != null && mKeyguardManager.isDeviceLocked();
    }
    
    private boolean isCallInProgress() {
        if (mTelephonyManager != null) {
            int callState = mTelephonyManager.getCallState();
            return callState != mTelephonyManager.CALL_STATE_IDLE;
        }
        return false;
    }

}
