/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.display.color;

import static com.android.server.display.color.DisplayTransformManager.LEVEL_COLOR_MATRIX_COLOR_BALANCE;

import android.content.Context;
import android.graphics.Color;
import android.hardware.display.ColorDisplayManager;
import android.opengl.Matrix;
import android.provider.Settings;

import java.util.Arrays;

/** Control the color transform for global color balance. */
final class ColorBalanceTintController extends TintController {

    public static final String X_REALITY_ENGINE_ENABLED = "x_reality_engine_mode_enabled";

    private final float[] mMatrix = new float[16];
    private float mHue = 0.0f;
    private float mContrast = 1.0f;
    private float mValue = 1.0f;
    private float mSaturation = 1.0f;

    // X-Reality Engine mode presets
    private final int X_REALITY_RED = 237;
    private final int X_REALITY_GREEN = 238;
    private final int X_REALITY_BLUE = 240;
    private final int X_REALITY_HUE = 0;
    private final int X_REALITY_SAT = 275;
    private final int X_REALITY_CONT = 258;
    private final int X_REALITY_VAL = 233;

    @Override
    public void setUp(Context context, boolean needsLinear) {
    }

    @Override
    public float[] getMatrix() {
        return Arrays.copyOf(mMatrix, mMatrix.length);
    }

    @Override
    public void setMatrix(int rgb) {
        Matrix.setIdentityM(mMatrix, 0);
        mMatrix[0] = ((float) Color.red(rgb)) / 255.0f;
        mMatrix[5] = ((float) Color.green(rgb)) / 255.0f;
        mMatrix[10] = ((float) Color.blue(rgb)) / 255.0f;
        applyHue(mMatrix, mHue);
        applyContrast(mMatrix, mContrast);
        applyValue(mMatrix, mValue);
        applySaturation(mMatrix, mSaturation);
    }

    @Override
    public int getLevel() {
        return LEVEL_COLOR_MATRIX_COLOR_BALANCE;
    }

    @Override
    public boolean isAvailable(Context context) {
        return ColorDisplayManager.isColorTransformAccelerated(context);
    }

    public void updateBalance(Context context, int userId) {
        boolean xRealityEnabled = Settings.Secure.getIntForUser(context.getContentResolver(), X_REALITY_ENGINE_ENABLED, 0, userId) != 0;
        int red = Settings.Secure.getIntForUser(context.getContentResolver(), channelToKey(0), 255, userId);
        int green = Settings.Secure.getIntForUser(context.getContentResolver(), channelToKey(1), 255, userId);
        int blue = Settings.Secure.getIntForUser(context.getContentResolver(), channelToKey(2), 255, userId);
        if (xRealityEnabled) {
            red = X_REALITY_RED;
            green = X_REALITY_GREEN;
            blue = X_REALITY_BLUE;
            mHue = X_REALITY_HUE;
            mContrast = X_REALITY_CONT / 255.0f;
            mValue = X_REALITY_VAL / 255.0f;
            mSaturation = X_REALITY_SAT / 255.0f;
        } else {
            /* 
            * for upstream purposes, users prefer live display atm and were unsure if exposing these properties 
            * for production is a good idea so skip exposing these until were ready
            mHue = Settings.Secure.getIntForUser(context.getContentResolver(), "display_color_balance_hue", 0, userId) / 255.0f * 360.0f;
            mContrast = Settings.Secure.getIntForUser(context.getContentResolver(), "display_color_balance_contrast", 255, userId) / 255.0f;
            mValue = Settings.Secure.getIntForUser(context.getContentResolver(), "display_color_balance_value", 255, userId) / 255.0f;
            mSaturation = Settings.Secure.getIntForUser(context.getContentResolver(), "display_color_balance_saturation", 255, userId) / 255.0f;
            */
            mHue = 0;
            mContrast = 1.0f;
            mValue = 1.0f;
            mSaturation = 1.0f;
        }
        int rgb = Color.rgb(red, green, blue);
        setMatrix(rgb);
    }

    public static String channelToKey(int channel) {
        switch (channel) {
            case 0:
                return Settings.Secure.DISPLAY_COLOR_BALANCE_RED;
            case 1:
                return Settings.Secure.DISPLAY_COLOR_BALANCE_GREEN;
            case 2:
                return Settings.Secure.DISPLAY_COLOR_BALANCE_BLUE;
            default:
                throw new IllegalArgumentException("Unknown channel: " + channel);
        }
    }

    private void applyHue(float[] matrix, float hue) {
        float angle = hue * (float)Math.PI / 180;
        float cosA = (float)Math.cos(angle);
        float sinA = (float)Math.sin(angle);

        float[] hueMatrix = {
            0.213f + cosA * 0.787f - sinA * 0.213f, 0.715f - cosA * 0.715f - sinA * 0.715f, 0.072f - cosA * 0.072f + sinA * 0.928f, 0,
            0.213f - cosA * 0.213f + sinA * 0.143f, 0.715f + cosA * 0.285f + sinA * 0.140f, 0.072f - cosA * 0.072f - sinA * 0.283f, 0,
            0.213f - cosA * 0.213f - sinA * 0.787f, 0.715f - cosA * 0.715f + sinA * 0.715f, 0.072f + cosA * 0.928f + sinA * 0.072f, 0,
            0, 0, 0, 1
        };

        Matrix.multiplyMM(matrix, 0, hueMatrix, 0, matrix, 0);
    }

    private void applyContrast(float[] matrix, float contrast) {
        float scale = contrast;
        float translate = (1 - scale) / 2;

        float[] contrastMatrix = {
            scale, 0, 0, 0,
            0, scale, 0, 0,
            0, 0, scale, 0,
            translate, translate, translate, 1
        };

        Matrix.multiplyMM(matrix, 0, contrastMatrix, 0, matrix, 0);
    }

    private void applyValue(float[] matrix, float value) {
        float scale = value;

        float[] valueMatrix = {
            scale, 0, 0, 0,
            0, scale, 0, 0,
            0, 0, scale, 0,
            0, 0, 0, 1
        };

        Matrix.multiplyMM(matrix, 0, valueMatrix, 0, matrix, 0);
    }

    private void applySaturation(float[] matrix, float saturation) {
        float rw = 0.3086f;
        float gw = 0.6094f;
        float bw = 0.0820f;

        float invSat = 1.0f - saturation;
        float R = invSat * rw;
        float G = invSat * gw;
        float B = invSat * bw;

        float[] saturationMatrix = {
            R + saturation, G, B, 0,
            R, G + saturation, B, 0,
            R, G, B + saturation, 0,
            0, 0, 0, 1
        };

        Matrix.multiplyMM(matrix, 0, saturationMatrix, 0, matrix, 0);
    }
}
