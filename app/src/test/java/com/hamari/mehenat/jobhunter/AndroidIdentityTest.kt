package com.hamari.mehenat.jobhunter

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidIdentityTest {
    @Test
    fun debugBuildKeepsThePersonalAndroidApplicationId() {
        assertTrue(BuildConfig.APPLICATION_ID.startsWith("com.hamari.mehenat.jobhunter"))
    }
}