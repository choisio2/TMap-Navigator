package com.aivy.navigator.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AivyDestinationTest {
    @Test
    fun `fromRoute maps known route`() {
        assertEquals(AivyDestination.Memory, AivyDestination.fromRoute("memory"))
    }

    @Test
    fun `fromRoute falls back to home for unknown route`() {
        assertEquals(AivyDestination.Home, AivyDestination.fromRoute("unknown"))
    }

    @Test
    fun `bottom nav hidden for secondary flow`() {
        assertFalse(AivyDestination.shouldShowBottomNav(AivyDestination.Pairing.route))
        assertFalse(AivyDestination.shouldShowBottomNav(AivyDestination.Ocr.route))
    }

    @Test
    fun `bottom nav shown for primary tabs`() {
        assertTrue(AivyDestination.shouldShowBottomNav(AivyDestination.Home.route))
        assertTrue(AivyDestination.shouldShowBottomNav(AivyDestination.Settings.route))
    }
}
