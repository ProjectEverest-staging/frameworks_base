/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.audio;

import android.annotation.NonNull;
import android.media.audiopolicy.IAudioVolumeChangeDispatcher;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The AudioVolumeChangeHandler handles AudioVolume callbacks posted from JNI
 */
/* private package */ class AudioVolumeChangeHandler {
    private static final String TAG = "AudioVolumeChangeHandler";
    private Handler mHandler;
    private HandlerThread mHandlerThread;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final RemoteCallbackList<IAudioVolumeChangeDispatcher> mListeners =
            new RemoteCallbackList<>();

    private static final int AUDIOVOLUMEGROUP_EVENT_VOLUME_CHANGED = 1000;

    /**
     * Accessed by native methods: JNI Callback context.
     */
    @SuppressWarnings("unused")
    private long mJniCallback;

    /**
     * Initialization
     */
    public void init() {
        if (mHandler != null) {
            return;
        }
        // create a new thread for our new event handler
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();

        if (mHandlerThread.getLooper() == null) {
            mHandler = null;
            return;
        }
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case AUDIOVOLUMEGROUP_EVENT_VOLUME_CHANGED:
                        Log.v(TAG, "AUDIOVOLUMEGROUP_EVENT_VOLUME_CHANGED");
                        sendAudioVolumeGroupChangedToClients((int) msg.arg1, (int) msg.arg2);
                        break;
                    default:
                        break;
                }
            }
        };
        native_setup(new WeakReference<AudioVolumeChangeHandler>(this));
    }

    private void sendAudioVolumeGroupChangedToClients(int groupId, int index) {
        final RemoteCallbackList<IAudioVolumeChangeDispatcher> listeners;
        final int nbDispatchers;
        synchronized (mLock) {
            if (mListeners == null) {
                return;
            }
            listeners = mListeners;
            nbDispatchers = mListeners.beginBroadcast();
        }
        for (int i = 0; i < nbDispatchers; i++) {
            try {
                mListeners.getBroadcastItem(i).onAudioVolumeGroupChanged(groupId, index);
            } catch (RemoteException e) {
            }
        }
        mListeners.finishBroadcast();
    }

    private native void native_setup(Object moduleThis);

    @Override
    protected void finalize() {
        native_finalize();
        if (mHandlerThread.isAlive()) {
            mHandlerThread.quit();
        }
    }
    private native void native_finalize();

   /**
    * @param cb the {@link IAudioVolumeChangeDispatcher} to register
    */
    public void registerListener(@NonNull IAudioVolumeChangeDispatcher cb) {
        Preconditions.checkNotNull(cb, "volume group callback must not be null");
        synchronized (mLock) {
            mListeners.register(cb);
        }
    }

   /**
    * @param cb the {@link IAudioVolumeChangeDispatcher} to unregister
    */
    public void unregisterListener(@NonNull IAudioVolumeChangeDispatcher cb) {
        Preconditions.checkNotNull(cb, "volume group callback must not be null");
        synchronized (mLock) {
            mListeners.unregister(cb);
        }
    }

    Handler handler() {
        return mHandler;
    }

    @SuppressWarnings("unused")
    private static void postEventFromNative(Object moduleRef,
                                            int what, int arg1, int arg2, Object obj) {
        AudioVolumeChangeHandler eventHandler =
                (AudioVolumeChangeHandler) ((WeakReference) moduleRef).get();
        if (eventHandler == null) {
            return;
        }
        Handler handler = eventHandler.handler();
        if (handler != null) {
            Message m = handler.obtainMessage(what, arg1, arg2, obj);
            // Do not remove previous messages, as we would lose notification of group changes
            handler.sendMessage(m);
        }
    }
}
