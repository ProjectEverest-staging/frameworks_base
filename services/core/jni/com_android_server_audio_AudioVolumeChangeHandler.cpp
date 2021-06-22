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

//#define LOG_NDEBUG 0

#define LOG_TAG "AudioVolumeChangeHandler-JNI"

#include <utils/Log.h>
#include <nativehelper/JNIHelp.h>
#include "core_jni_helpers.h"

#include "com_android_server_audio_AudioVolumeChangeHandler.h"


// ----------------------------------------------------------------------------
namespace android {

static const char* const kAudioVolumeChangeHandlerClassPathName =
        "com/android/server/audio/AudioVolumeChangeHandler";

static struct {
    jfieldID    mJniCallback;
} gAudioVolumeChangeHandlerFields;

static struct {
    jmethodID    postEventFromNative;
} gAudioVolumeChangeHandlerMethods;

static Mutex gLock;

JNIAudioVolumeChangeHandler::JNIAudioVolumeChangeHandler(JNIEnv* env,
                                                         jobject thiz,
                                                         jobject weak_thiz)
{
    jclass clazz = env->GetObjectClass(thiz);
    if (clazz == NULL) {
        ALOGE("Can't find class %s", kAudioVolumeChangeHandlerClassPathName);
        return;
    }
    mClass = (jclass)env->NewGlobalRef(clazz);

    // We use a weak reference so the AudioVolumeChangeHandler object can be garbage collected.
    // The reference is only used as a proxy for callbacks.
    mObject  = env->NewGlobalRef(weak_thiz);
}

JNIAudioVolumeChangeHandler::~JNIAudioVolumeChangeHandler()
{
    // remove global references
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (env == NULL) {
        return;
    }
    env->DeleteGlobalRef(mObject);
    env->DeleteGlobalRef(mClass);
}

void JNIAudioVolumeChangeHandler::onAudioVolumeGroupChanged(volume_group_t group, int flags)
{
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (env == NULL) {
        return;
    }
    env->CallStaticVoidMethod(mClass,
                              gAudioVolumeChangeHandlerMethods.postEventFromNative,
                              mObject,
                              AUDIOVOLUMEGROUP_EVENT_VOLUME_CHANGED, group, flags, NULL);
    if (env->ExceptionCheck()) {
        ALOGW("An exception occurred while notifying an event.");
        env->ExceptionClear();
    }
}

static
sp<JNIAudioVolumeChangeHandler> setJniCallback(JNIEnv* env,
                                               jobject thiz,
                                               const sp<JNIAudioVolumeChangeHandler>& callback)
{
    Mutex::Autolock l(gLock);
    sp<JNIAudioVolumeChangeHandler> old = (JNIAudioVolumeChangeHandler*)env->GetLongField(
                thiz, gAudioVolumeChangeHandlerFields.mJniCallback);
    if (callback.get()) {
        callback->incStrong((void*)setJniCallback);
    }
    if (old != 0) {
        old->decStrong((void*)setJniCallback);
    }
    env->SetLongField(thiz, gAudioVolumeChangeHandlerFields.mJniCallback,
                      (jlong)callback.get());
    return old;
}

static void
com_android_server_audio_AudioVolumeChangeHandler_eventHandlerSetup(JNIEnv *env,
                                                              jobject thiz,
                                                              jobject weak_this)
{
    ALOGD("%s", __FUNCTION__);
    sp<JNIAudioVolumeChangeHandler> callback =
            new JNIAudioVolumeChangeHandler(env, thiz, weak_this);

    if (AudioSystem::addAudioVolumeGroupCallback(callback) == NO_ERROR) {
        setJniCallback(env, thiz, callback);
    }
}

static void
com_android_server_audio_AudioVolumeChangeHandler_eventHandlerFinalize(JNIEnv *env, jobject thiz)
{
    ALOGD("%s", __FUNCTION__);
    sp<JNIAudioVolumeChangeHandler> callback = setJniCallback(env, thiz, 0);
    if (callback != 0) {
        AudioSystem::removeAudioVolumeGroupCallback(callback);
    }
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    {"native_setup", "(Ljava/lang/Object;)V",
        (void *)com_android_server_audio_AudioVolumeChangeHandler_eventHandlerSetup},
    {"native_finalize",  "()V",
        (void *)com_android_server_audio_AudioVolumeChangeHandler_eventHandlerFinalize},
};

int register_android_server_audio_AudioVolumeChangeHandler(JNIEnv *env)
{
    jclass AudioVolumeChangeHandlerClass =
            FindClassOrDie(env, kAudioVolumeChangeHandlerClassPathName);
    gAudioVolumeChangeHandlerMethods.postEventFromNative =
            GetStaticMethodIDOrDie(env, AudioVolumeChangeHandlerClass, "postEventFromNative",
                                   "(Ljava/lang/Object;IIILjava/lang/Object;)V");

    gAudioVolumeChangeHandlerFields.mJniCallback =
            GetFieldIDOrDie(env, AudioVolumeChangeHandlerClass, "mJniCallback", "J");

    env->DeleteLocalRef(AudioVolumeChangeHandlerClass);

    return RegisterMethodsOrDie(env,
                                kAudioVolumeChangeHandlerClassPathName,
                                gMethods,
                                NELEM(gMethods));
}

} // namespace android
