package com.nuvio.app.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NuvioNavigatorExitToDetailsTest {
    private val details = DetailRoute(type = "series", id = "tt0944947", title = "Show")
    private val player = PlayerRoute(launchId = 7L, title = "Show")
    private val isDetails: (AppRoute) -> Boolean = { it is DetailRoute && it.id == details.id }

    @Test
    fun popsBackToTheDetailsAlreadyInTheStack() {
        val stack = NavBackStack<NavKey>(TabsRoute, details, StreamRoute(launchId = 3L), player)
        val navigator = NuvioNavigator(stack)

        assertTrue(navigator.popBackToOrReplace(player, details, isDetails))

        assertEquals(listOf<NavKey>(TabsRoute, details), stack.toList())
    }

    @Test
    fun replacesThePlayerWithDetailsWhenTheyAreMissing() {
        // Continue watching opens the player straight from the home screen.
        val stack = NavBackStack<NavKey>(TabsRoute, player)
        val navigator = NuvioNavigator(stack)

        assertTrue(navigator.popBackToOrReplace(player, details, isDetails))

        assertEquals(listOf<NavKey>(TabsRoute, details), stack.toList())
    }

    @Test
    fun ignoresDetailsOfAnotherTitle() {
        val other = DetailRoute(type = "movie", id = "tt0111161")
        val stack = NavBackStack<NavKey>(TabsRoute, other, player)
        val navigator = NuvioNavigator(stack)

        assertTrue(navigator.popBackToOrReplace(player, details, isDetails))

        assertEquals(listOf<NavKey>(TabsRoute, other, details), stack.toList())
    }

    @Test
    fun doesNothingWhenThePlayerIsNotOnTopOrNavigationIsNative() {
        val stack = NavBackStack<NavKey>(TabsRoute, details, player)
        assertFalse(NuvioNavigator(stack).popBackToOrReplace(PlayerRoute(launchId = 8L), details, isDetails))
        assertFalse(
            NuvioNavigator(stack, onExternalNavigate = { _, _ -> }).popBackToOrReplace(player, details, isDetails),
        )
        assertEquals(listOf<NavKey>(TabsRoute, details, player), stack.toList())
    }
}
