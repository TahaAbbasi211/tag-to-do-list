package com.tag.routine.motion

import com.tag.routine.data.AnimationSetting
import com.tag.routine.ui.motion.AnimationLevel
import com.tag.routine.ui.motion.AnimationPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationPolicyTest {

    private val highRamBytes = 6L * 1024L * 1024L * 1024L
    private val lowRamBytes = 2L * 1024L * 1024L * 1024L

    @Test
    fun `system remove animations overrides every user setting to OFF`() {
        for (setting in AnimationSetting.entries) {
            assertEquals(
                AnimationLevel.OFF,
                AnimationPolicy.resolve(
                    userSetting = setting,
                    systemAnimatorsEnabled = false,
                    animatorDurationScale = 1f,
                    isLowRamDevice = false,
                    totalRamBytes = highRamBytes,
                ),
            )
            assertEquals(
                AnimationLevel.OFF,
                AnimationPolicy.resolve(
                    userSetting = setting,
                    systemAnimatorsEnabled = true,
                    animatorDurationScale = 0f,
                    isLowRamDevice = false,
                    totalRamBytes = highRamBytes,
                ),
            )
        }
    }

    @Test
    fun `auto resolves to FULL on capable devices and REDUCED on low-RAM devices`() {
        assertEquals(
            AnimationLevel.FULL,
            AnimationPolicy.resolve(
                userSetting = AnimationSetting.AUTO,
                systemAnimatorsEnabled = true,
                animatorDurationScale = 1f,
                isLowRamDevice = false,
                totalRamBytes = highRamBytes,
            ),
        )
        assertEquals(
            AnimationLevel.REDUCED,
            AnimationPolicy.resolve(
                userSetting = AnimationSetting.AUTO,
                systemAnimatorsEnabled = true,
                animatorDurationScale = 1f,
                isLowRamDevice = true,
                totalRamBytes = highRamBytes,
            ),
        )
        assertEquals(
            AnimationLevel.REDUCED,
            AnimationPolicy.resolve(
                userSetting = AnimationSetting.AUTO,
                systemAnimatorsEnabled = true,
                animatorDurationScale = 1f,
                isLowRamDevice = false,
                totalRamBytes = lowRamBytes,
            ),
        )
    }

    @Test
    fun `explicit user setting overrides device RAM heuristics when system animators enabled`() {
        assertEquals(
            AnimationLevel.FULL,
            AnimationPolicy.resolve(
                userSetting = AnimationSetting.FULL,
                systemAnimatorsEnabled = true,
                animatorDurationScale = 1f,
                isLowRamDevice = true,
                totalRamBytes = lowRamBytes,
            ),
        )
        assertEquals(
            AnimationLevel.REDUCED,
            AnimationPolicy.resolve(
                userSetting = AnimationSetting.REDUCED,
                systemAnimatorsEnabled = true,
                animatorDurationScale = 1f,
                isLowRamDevice = false,
                totalRamBytes = highRamBytes,
            ),
        )
        assertEquals(
            AnimationLevel.OFF,
            AnimationPolicy.resolve(
                userSetting = AnimationSetting.OFF,
                systemAnimatorsEnabled = true,
                animatorDurationScale = 1f,
                isLowRamDevice = false,
                totalRamBytes = highRamBytes,
            ),
        )
    }
}
