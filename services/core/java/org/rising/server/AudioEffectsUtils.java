/*
 * Copyright (C) 2023-2024 The RisingOS Android Project
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

import android.content.Context;
import android.media.AudioManager;
import android.media.audiofx.*;
import android.util.Log;

import java.util.List;

public class AudioEffectsUtils {

    private final static int EFFECT_PRIORITY = Integer.MAX_VALUE;

    private static final short[] BASS_BOOST_STRENGTH = {0, 800, 700, 600, 700};
    private static final short[] SPATIAL_STRENGTH = {0, 1100, 1150, 1200, 1100};

    private static final short[] REVERB_PRESETS = {
            PresetReverb.PRESET_NONE,
            PresetReverb.PRESET_PLATE,
            PresetReverb.PRESET_LARGEHALL,
            PresetReverb.PRESET_PLATE,
            PresetReverb.PRESET_MEDIUMROOM
    };

    private final AudioManager mAudioManager;

    private int mCurrentMode = 0;

    private Equalizer mEqualizer;
    private BassBoost mBassBoost;
    private PresetReverb mPresetReverb;
    private Virtualizer mVirtualizer;

    public AudioEffectsUtils(Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void initializeEffects() {
        // Attach audio effects to the global audio output
        mEqualizer = new Equalizer(EFFECT_PRIORITY, 0);
        mBassBoost = new BassBoost(EFFECT_PRIORITY, 0);
        mPresetReverb = new PresetReverb(EFFECT_PRIORITY, 0);
        mVirtualizer = new Virtualizer(EFFECT_PRIORITY, 0);
        mEqualizer.setEnabled(true);
        mBassBoost.setEnabled(true);
        mPresetReverb.setEnabled(true);
        mVirtualizer.setEnabled(true);
    }

    public void releaseEffects() {
        for (Object effect : getAllEffects()) {
            if (effect != null && effect instanceof AudioEffect) {
                releaseEffect(effect);
            }
        }
    }

    public void setEffects(int mode) {
        mCurrentMode = mode;
        try {
            setEffects();
        } catch (Exception e) {}
    }

    private void setEffects() {
        setBandLevels();
        setSpatialAudio();
        setBassBoost();
        setReverb();
    }

    private Object[] getAllEffects() {
        return new Object[]{ mEqualizer, mBassBoost, mPresetReverb, mVirtualizer };
    }

    public boolean getEffectEnabled(Object effect) {
        AudioEffect mEffect = (AudioEffect) effect;
        try {
            return mEffect != null && mEffect.getEnabled();
        } catch (Exception e) {
            return false;
        }
    }
    
    public void releaseEffect(Object effect) {
        if (getEffectEnabled(effect)) {
            AudioEffect mEffect = (AudioEffect) effect;
            mEffect.setEnabled(false);
            mEffect.release();
        }
    }
    
    private void setBandLevels() {
        maybeInitEffect(mEqualizer);
        short[] bandMillibels = new short[mEqualizer.getNumberOfBands()];
        String[] modeName = {"Disabled", "Music", "Game", "Theater", "Smart"};
        for (short i = 0; i < bandMillibels.length; i++) {
            bandMillibels[i] = getBandLevelForMode(i);
            mEqualizer.setBandLevel(i, bandMillibels[i]);
            // Log.d("AudioEffectsUtils", "Applied band level for band " + i + " in mode " + modeName[mCurrentMode] + ": " + bandMillibels[i]);
        }
    }

    private short getBandLevelForMode(short band) {
        int centerBand = mEqualizer.getNumberOfBands() / 2;
        int millibels = mEqualizer.getBandLevelRange()[1];
        int targetLevel = 0;

        switch (mCurrentMode) {
            case 1: // Music
                targetLevel = calculateBandLevel(millibels, band, centerBand, -1.4, -1.3, -1.5);
                break;
            case 2: // Game
                targetLevel = calculateBandLevel(millibels, band, centerBand, -1.5, -1.4, -1.6);
                break;
            case 3: // Theater
                targetLevel = calculateBandLevel(millibels, band, centerBand, -1.6, -1.5, -1.7);
                break;
            case 4: // Smart
                targetLevel = calculateBandLevel(millibels, band, centerBand, -1.4, -1.3, -1.4);
                break;
            default:
                targetLevel = (int) (millibels * 0.95);
                break;
        }

        return (short) targetLevel;
    }

    private int calculateBandLevel(int millibels, short band, int centerBand, double centerFactor, double lowerFactor, double upperFactor) {
        if (band == centerBand) {
            return (int) (millibels * centerFactor);
        } else if (band < centerBand) {
            return (int) (millibels * lowerFactor);
        } else {
            return (int) (millibels * upperFactor);
        }
    }

    private void setBassBoost() {
        maybeInitEffect(mBassBoost);
        mBassBoost.setStrength(BASS_BOOST_STRENGTH[mCurrentMode]);
    }

    private void setReverb() {
        maybeInitEffect(mPresetReverb);
        mPresetReverb.setPreset(REVERB_PRESETS[mCurrentMode]);
    }

    private void setSpatialAudio() {
        maybeInitEffect(mVirtualizer);
        mVirtualizer.setStrength(SPATIAL_STRENGTH[mCurrentMode]);
        mVirtualizer.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_AUTO);
    }

    private void maybeInitEffect(Object effect) {
        if (!getEffectEnabled(effect)) {
            if (effect == null) {
                if (effect instanceof BassBoost) {
                    effect = new BassBoost(EFFECT_PRIORITY, 0);
                } else if (effect instanceof Equalizer) {
                    effect = new Equalizer(EFFECT_PRIORITY, 0);
                } else if (effect instanceof LoudnessEnhancer) {
                    effect = new LoudnessEnhancer(EFFECT_PRIORITY, 0);
                } else if (effect instanceof PresetReverb) {
                    effect = new PresetReverb(EFFECT_PRIORITY, 0);
                } else if (effect instanceof Virtualizer) {
                    effect = new Virtualizer(EFFECT_PRIORITY, 0);
                }
            }
            if (effect instanceof AudioEffect) {
                ((AudioEffect) effect).setEnabled(true);
            }
        }
    }

}
