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

package android.window;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Data object of params for TaskFragment related {@link WindowContainerTransaction} operation.
 *
 * @see WindowContainerTransaction#addTaskFragmentOperation(IBinder, TaskFragmentOperation).
 * @hide
 */
public final class TaskFragmentOperation implements Parcelable {

    /**
     * Type for tracking other unknown TaskFragment operation that is not set through
     * {@link TaskFragmentOperation}, such as invalid request.
     */
    public static final int OP_TYPE_UNKNOWN = -1;

    /** Creates a new TaskFragment. */
    public static final int OP_TYPE_CREATE_TASK_FRAGMENT = 0;

    /** Deletes the given TaskFragment. */
    public static final int OP_TYPE_DELETE_TASK_FRAGMENT = 1;

    /** Starts an Activity in the given TaskFragment. */
    public static final int OP_TYPE_START_ACTIVITY_IN_TASK_FRAGMENT = 2;

    /** Reparents the given Activity to the given TaskFragment. */
    public static final int OP_TYPE_REPARENT_ACTIVITY_TO_TASK_FRAGMENT = 3;

    /** Sets two TaskFragments adjacent to each other. */
    public static final int OP_TYPE_SET_ADJACENT_TASK_FRAGMENTS = 4;

    /** Clears the adjacent TaskFragments relationship. */
    public static final int OP_TYPE_CLEAR_ADJACENT_TASK_FRAGMENTS = 5;

    /** Requests focus on the top running Activity in the given TaskFragment. */
    public static final int OP_TYPE_REQUEST_FOCUS_ON_TASK_FRAGMENT = 6;

    /** Sets a given TaskFragment to have a companion TaskFragment. */
    public static final int OP_TYPE_SET_COMPANION_TASK_FRAGMENT = 7;

    /** Sets the {@link TaskFragmentAnimationParams} for the given TaskFragment. */
    public static final int OP_TYPE_SET_ANIMATION_PARAMS = 8;

    /** Sets the relative bounds with {@link WindowContainerTransaction#setRelativeBounds}. */
    public static final int OP_TYPE_SET_RELATIVE_BOUNDS = 9;

    /**
     * Reorders the TaskFragment to be the front-most TaskFragment in the Task.
     * Note that there could still have other WindowContainer on top of the front-most
     * TaskFragment, such as a non-embedded Activity.
     */
    public static final int OP_TYPE_REORDER_TO_FRONT = 10;

    /**
     * Sets the activity navigation to be isolated, where the activity navigation on the
     * TaskFragment is separated from the rest activities in the Task. Activities cannot be
     * started on an isolated TaskFragment unless the activities are launched from the same
     * TaskFragment or explicitly requested to.
     */
    public static final int OP_TYPE_SET_ISOLATED_NAVIGATION = 11;

    /**
     * Reorders the TaskFragment to be the bottom-most in the Task. Note that this op will bring the
     * TaskFragment to the bottom of the Task below all the other Activities and TaskFragments.
     *
     * This is only allowed for system organizers. See
     * {@link com.android.server.wm.TaskFragmentOrganizerController#registerOrganizer(
     * ITaskFragmentOrganizer, boolean)}
     */
    public static final int OP_TYPE_REORDER_TO_BOTTOM_OF_TASK = 12;

    /**
     * Reorders the TaskFragment to be the top-most in the Task. Note that this op will bring the
     * TaskFragment to the top of the Task above all the other Activities and TaskFragments.
     *
     * This is only allowed for system organizers. See
     * {@link com.android.server.wm.TaskFragmentOrganizerController#registerOrganizer(
     * ITaskFragmentOrganizer, boolean)}
     */
    public static final int OP_TYPE_REORDER_TO_TOP_OF_TASK = 13;

    /**
     * Creates a decor surface in the parent Task of the TaskFragment. The created decor surface
     * will be provided in {@link TaskFragmentTransaction#TYPE_TASK_FRAGMENT_PARENT_INFO_CHANGED}
     * event callback.
     */
    public static final int OP_TYPE_CREATE_TASK_FRAGMENT_DECOR_SURFACE = 14;

    /**
     * Removes the decor surface in the parent Task of the TaskFragment.
     */
    public static final int OP_TYPE_REMOVE_TASK_FRAGMENT_DECOR_SURFACE = 15;

    /**
     * Applies dimming on the parent Task which could cross two TaskFragments.
     */
    public static final int OP_TYPE_SET_DIM_ON_TASK = 16;

    /**
     * Sets this TaskFragment to move to bottom of the Task if any of the activities below it is
     * launched in a mode requiring clear top.
     *
     * This is only allowed for system organizers. See
     * {@link com.android.server.wm.TaskFragmentOrganizerController#registerOrganizer(
     * ITaskFragmentOrganizer, boolean)}
     */
    public static final int OP_TYPE_SET_MOVE_TO_BOTTOM_IF_CLEAR_WHEN_LAUNCH = 17;

    @IntDef(prefix = { "OP_TYPE_" }, value = {
            OP_TYPE_UNKNOWN,
            OP_TYPE_CREATE_TASK_FRAGMENT,
            OP_TYPE_DELETE_TASK_FRAGMENT,
            OP_TYPE_START_ACTIVITY_IN_TASK_FRAGMENT,
            OP_TYPE_REPARENT_ACTIVITY_TO_TASK_FRAGMENT,
            OP_TYPE_SET_ADJACENT_TASK_FRAGMENTS,
            OP_TYPE_CLEAR_ADJACENT_TASK_FRAGMENTS,
            OP_TYPE_REQUEST_FOCUS_ON_TASK_FRAGMENT,
            OP_TYPE_SET_COMPANION_TASK_FRAGMENT,
            OP_TYPE_SET_ANIMATION_PARAMS,
            OP_TYPE_SET_RELATIVE_BOUNDS,
            OP_TYPE_REORDER_TO_FRONT,
            OP_TYPE_SET_ISOLATED_NAVIGATION,
            OP_TYPE_REORDER_TO_BOTTOM_OF_TASK,
            OP_TYPE_REORDER_TO_TOP_OF_TASK,
            OP_TYPE_CREATE_TASK_FRAGMENT_DECOR_SURFACE,
            OP_TYPE_REMOVE_TASK_FRAGMENT_DECOR_SURFACE,
            OP_TYPE_SET_DIM_ON_TASK,
            OP_TYPE_SET_MOVE_TO_BOTTOM_IF_CLEAR_WHEN_LAUNCH,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OperationType {}

    @OperationType
    private final int mOpType;

    @Nullable
    private final TaskFragmentCreationParams mTaskFragmentCreationParams;

    @Nullable
    private final IBinder mActivityToken;

    @Nullable
    private final Intent mActivityIntent;

    @Nullable
    private final Bundle mBundle;

    @Nullable
    private final IBinder mSecondaryFragmentToken;

    @Nullable
    private final TaskFragmentAnimationParams mAnimationParams;

    private final boolean mIsolatedNav;

    private final boolean mDimOnTask;

    private final boolean mMoveToBottomIfClearWhenLaunch;

    private TaskFragmentOperation(@OperationType int opType,
            @Nullable TaskFragmentCreationParams taskFragmentCreationParams,
            @Nullable IBinder activityToken, @Nullable Intent activityIntent,
            @Nullable Bundle bundle, @Nullable IBinder secondaryFragmentToken,
            @Nullable TaskFragmentAnimationParams animationParams,
            boolean isolatedNav, boolean dimOnTask, boolean moveToBottomIfClearWhenLaunch) {
        mOpType = opType;
        mTaskFragmentCreationParams = taskFragmentCreationParams;
        mActivityToken = activityToken;
        mActivityIntent = activityIntent;
        mBundle = bundle;
        mSecondaryFragmentToken = secondaryFragmentToken;
        mAnimationParams = animationParams;
        mIsolatedNav = isolatedNav;
        mDimOnTask = dimOnTask;
        mMoveToBottomIfClearWhenLaunch = moveToBottomIfClearWhenLaunch;
    }

    private TaskFragmentOperation(Parcel in) {
        mOpType = in.readInt();
        mTaskFragmentCreationParams = in.readTypedObject(TaskFragmentCreationParams.CREATOR);
        mActivityToken = in.readStrongBinder();
        mActivityIntent = in.readTypedObject(Intent.CREATOR);
        mBundle = in.readBundle(getClass().getClassLoader());
        mSecondaryFragmentToken = in.readStrongBinder();
        mAnimationParams = in.readTypedObject(TaskFragmentAnimationParams.CREATOR);
        mIsolatedNav = in.readBoolean();
        mDimOnTask = in.readBoolean();
        mMoveToBottomIfClearWhenLaunch = in.readBoolean();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mOpType);
        dest.writeTypedObject(mTaskFragmentCreationParams, flags);
        dest.writeStrongBinder(mActivityToken);
        dest.writeTypedObject(mActivityIntent, flags);
        dest.writeBundle(mBundle);
        dest.writeStrongBinder(mSecondaryFragmentToken);
        dest.writeTypedObject(mAnimationParams, flags);
        dest.writeBoolean(mIsolatedNav);
        dest.writeBoolean(mDimOnTask);
        dest.writeBoolean(mMoveToBottomIfClearWhenLaunch);
    }

    @NonNull
    public static final Creator<TaskFragmentOperation> CREATOR =
            new Creator<TaskFragmentOperation>() {
                @Override
                public TaskFragmentOperation createFromParcel(Parcel in) {
                    return new TaskFragmentOperation(in);
                }

                @Override
                public TaskFragmentOperation[] newArray(int size) {
                    return new TaskFragmentOperation[size];
                }
            };

    /**
     * Gets the {@link OperationType} of this {@link TaskFragmentOperation}.
     */
    @OperationType
    public int getOpType() {
        return mOpType;
    }

    /**
     * Gets the options to create a new TaskFragment.
     */
    @Nullable
    public TaskFragmentCreationParams getTaskFragmentCreationParams() {
        return mTaskFragmentCreationParams;
    }

    /**
     * Gets the Activity token set in this operation.
     */
    @Nullable
    public IBinder getActivityToken() {
        return mActivityToken;
    }

    /**
     * Gets the Intent to start a new Activity.
     */
    @Nullable
    public Intent getActivityIntent() {
        return mActivityIntent;
    }

    /**
     * Gets the Bundle set in this operation.
     */
    @Nullable
    public Bundle getBundle() {
        return mBundle;
    }

    /**
     * Gets the fragment token of the secondary TaskFragment set in this operation.
     */
    @Nullable
    public IBinder getSecondaryFragmentToken() {
        return mSecondaryFragmentToken;
    }

    /**
     * Gets the animation related override of TaskFragment.
     */
    @Nullable
    public TaskFragmentAnimationParams getAnimationParams() {
        return mAnimationParams;
    }

    /**
     * Returns whether the activity navigation on this TaskFragment is isolated. This is only
     * useful when the op type is {@link OP_TYPE_SET_ISOLATED_NAVIGATION}.
     */
    public boolean isIsolatedNav() {
        return mIsolatedNav;
    }

    /**
     * Returns whether the dim layer should apply on the parent Task.
     */
    public boolean isDimOnTask() {
        return mDimOnTask;
    }

    /**
     * Returns whether the TaskFragment should move to bottom of task when any activity below it
     * is launched in clear top mode.
     */
    public boolean isMoveToBottomIfClearWhenLaunch() {
        return mMoveToBottomIfClearWhenLaunch;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TaskFragmentOperation{ opType=").append(mOpType);
        if (mTaskFragmentCreationParams != null) {
            sb.append(", taskFragmentCreationParams=").append(mTaskFragmentCreationParams);
        }
        if (mActivityToken != null) {
            sb.append(", activityToken=").append(mActivityToken);
        }
        if (mActivityIntent != null) {
            sb.append(", activityIntent=").append(mActivityIntent);
        }
        if (mBundle != null) {
            sb.append(", bundle=").append(mBundle);
        }
        if (mSecondaryFragmentToken != null) {
            sb.append(", secondaryFragmentToken=").append(mSecondaryFragmentToken);
        }
        if (mAnimationParams != null) {
            sb.append(", animationParams=").append(mAnimationParams);
        }
        sb.append(", isolatedNav=").append(mIsolatedNav);
        sb.append(", dimOnTask=").append(mDimOnTask);
        sb.append(", moveToBottomIfClearWhenLaunch=").append(mMoveToBottomIfClearWhenLaunch);

        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOpType, mTaskFragmentCreationParams, mActivityToken, mActivityIntent,
                mBundle, mSecondaryFragmentToken, mAnimationParams, mIsolatedNav, mDimOnTask,
                mMoveToBottomIfClearWhenLaunch);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof TaskFragmentOperation)) {
            return false;
        }
        final TaskFragmentOperation other = (TaskFragmentOperation) obj;
        return mOpType == other.mOpType
                && Objects.equals(mTaskFragmentCreationParams, other.mTaskFragmentCreationParams)
                && Objects.equals(mActivityToken, other.mActivityToken)
                && Objects.equals(mActivityIntent, other.mActivityIntent)
                && Objects.equals(mBundle, other.mBundle)
                && Objects.equals(mSecondaryFragmentToken, other.mSecondaryFragmentToken)
                && Objects.equals(mAnimationParams, other.mAnimationParams)
                && mIsolatedNav == other.mIsolatedNav
                && mDimOnTask == other.mDimOnTask
                && mMoveToBottomIfClearWhenLaunch == other.mMoveToBottomIfClearWhenLaunch;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Builder to construct the {@link TaskFragmentOperation}. */
    public static final class Builder {

        @OperationType
        private final int mOpType;

        @Nullable
        private TaskFragmentCreationParams mTaskFragmentCreationParams;

        @Nullable
        private IBinder mActivityToken;

        @Nullable
        private Intent mActivityIntent;

        @Nullable
        private Bundle mBundle;

        @Nullable
        private IBinder mSecondaryFragmentToken;

        @Nullable
        private TaskFragmentAnimationParams mAnimationParams;

        private boolean mIsolatedNav;

        private boolean mDimOnTask;

        private boolean mMoveToBottomIfClearWhenLaunch;

        /**
         * @param opType the {@link OperationType} of this {@link TaskFragmentOperation}.
         */
        public Builder(@OperationType int opType) {
            mOpType = opType;
        }

        /**
         * Sets the {@link TaskFragmentCreationParams} for creating a new TaskFragment.
         */
        @NonNull
        public Builder setTaskFragmentCreationParams(
                @Nullable TaskFragmentCreationParams taskFragmentCreationParams) {
            mTaskFragmentCreationParams = taskFragmentCreationParams;
            return this;
        }

        /**
         * Sets an Activity token to this operation.
         */
        @NonNull
        public Builder setActivityToken(@Nullable IBinder activityToken) {
            mActivityToken = activityToken;
            return this;
        }

        /**
         * Sets the Intent to start a new Activity.
         */
        @NonNull
        public Builder setActivityIntent(@Nullable Intent activityIntent) {
            mActivityIntent = activityIntent;
            return this;
        }

        /**
         * Sets a Bundle to this operation.
         */
        @NonNull
        public Builder setBundle(@Nullable Bundle bundle) {
            mBundle = bundle;
            return this;
        }

        /**
         * Sets the secondary fragment token to this operation.
         */
        @NonNull
        public Builder setSecondaryFragmentToken(@Nullable IBinder secondaryFragmentToken) {
            mSecondaryFragmentToken = secondaryFragmentToken;
            return this;
        }

        /**
         * Sets the {@link TaskFragmentAnimationParams} for the given TaskFragment.
         */
        @NonNull
        public Builder setAnimationParams(@Nullable TaskFragmentAnimationParams animationParams) {
            mAnimationParams = animationParams;
            return this;
        }

        /**
         * Sets the activity navigation of this TaskFragment to be isolated.
         */
        @NonNull
        public Builder setIsolatedNav(boolean isolatedNav) {
            mIsolatedNav = isolatedNav;
            return this;
        }

        /**
         * Sets the dimming to apply on the parent Task if any.
         */
        @NonNull
        public Builder setDimOnTask(boolean dimOnTask) {
            mDimOnTask = dimOnTask;
            return this;
        }

        /**
         * Sets whether the TaskFragment should move to bottom of task when any activity below it
         * is launched in clear top mode.
         */
        @NonNull
        public Builder setMoveToBottomIfClearWhenLaunch(boolean moveToBottomIfClearWhenLaunch) {
            mMoveToBottomIfClearWhenLaunch = moveToBottomIfClearWhenLaunch;
            return this;
        }

        /**
         * Constructs the {@link TaskFragmentOperation}.
         */
        @NonNull
        public TaskFragmentOperation build() {
            return new TaskFragmentOperation(mOpType, mTaskFragmentCreationParams, mActivityToken,
                    mActivityIntent, mBundle, mSecondaryFragmentToken, mAnimationParams,
                    mIsolatedNav, mDimOnTask, mMoveToBottomIfClearWhenLaunch);
        }
    }
}
