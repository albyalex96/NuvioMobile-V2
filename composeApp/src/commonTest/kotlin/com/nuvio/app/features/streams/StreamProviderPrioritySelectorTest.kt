package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamProviderPrioritySelectorTest {

    private val first = "addon:first:https://first.example/manifest.json"
    private val second = "addon:second:https://second.example/manifest.json"
    private val third = "addon:third:https://third.example/manifest.json"
    private val config = StreamProviderPriorityConfig(order = listOf(first, second, third))

    @Test
    fun `waits for a higher ranked provider even when a lower one answered first`() {
        val evaluation = evaluate(
            group(first, loading = true),
            group(second, stream(second, "second")),
        )

        assertNull(evaluation.stream)
        assertEquals(first, evaluation.waitingFor)
        assertFalse(evaluation.isDecided)
    }

    @Test
    fun `picks the higher ranked provider once it answers`() {
        val evaluation = evaluate(
            group(second, stream(second, "second")),
            group(first, stream(first, "first")),
        )

        assertEquals("first", evaluation.stream?.name)
        assertEquals(listOf("first", "second"), evaluation.readyStreams.map { it.name })
        assertTrue(evaluation.isDecided)
    }

    @Test
    fun `falls back to the next provider when the higher one has no streams`() {
        val evaluation = evaluate(
            group(first),
            group(second, stream(second, "second")),
            group(third, loading = true),
        )

        assertEquals("second", evaluation.stream?.name)
    }

    @Test
    fun `skips a provider whose streams are not playable`() {
        val evaluation = evaluate(
            group(first, stream(first, "unplayable", url = null)),
            group(second, stream(second, "second")),
        )

        assertEquals("second", evaluation.stream?.name)
    }

    @Test
    fun `after the timeout takes the best ready stream without waiting`() {
        val evaluation = evaluate(
            group(first, loading = true),
            group(third, stream(third, "third")),
            group(second, stream(second, "second")),
            timeoutElapsed = true,
        )

        assertEquals("second", evaluation.stream?.name)
    }

    @Test
    fun `is decided without a stream when every provider finished empty`() {
        val evaluation = evaluate(group(first), group(second))

        assertNull(evaluation.stream)
        assertTrue(evaluation.isDecided)
    }

    @Test
    fun `disabled providers are ignored and not awaited`() {
        val evaluation = evaluate(
            group(first, loading = true),
            group(second, stream(second, "second")),
            config = config.copy(disabled = setOf(first)),
        )

        assertEquals("second", evaluation.stream?.name)
    }

    @Test
    fun `excluded categories are ignored`() {
        val evaluation = evaluate(
            group(first, stream(first, "first")),
            config = config.copy(categories = emptySet()),
        )

        assertNull(evaluation.stream)
        assertTrue(evaluation.isDecided)
    }

    @Test
    fun `new providers rank after the ordered ones`() {
        val newcomer = "addon:new:https://new.example/manifest.json"
        val evaluation = evaluate(
            group(newcomer, stream(newcomer, "new")),
            group(third, stream(third, "third")),
        )

        assertEquals("third", evaluation.stream?.name)
    }

    @Test
    fun `pending provider keys of a grouped source are awaited individually`() {
        val repositoryGroup = AddonStreamGroup(
            addonName = "Repository",
            addonId = "repo",
            streams = listOf(stream(second, "second", addonId = "repo")),
            isLoading = true,
            pendingProviderKeys = setOf(first),
        )

        val waiting = evaluate(repositoryGroup)
        assertEquals(first, waiting.waitingFor)

        val answered = evaluate(repositoryGroup.copy(pendingProviderKeys = setOf(third)))
        assertEquals("second", answered.stream?.name)
    }

    @Test
    fun `ordered providers keep the saved order and append new ones`() {
        val providers = listOf(
            StreamPriorityProvider(third, "Third", StreamProviderCategory.ADDONS),
            StreamPriorityProvider("addon:new:x", "New", StreamProviderCategory.ADDONS),
            StreamPriorityProvider(first, "First", StreamProviderCategory.ADDONS),
        )

        val ordered = config.orderedProviders(providers).map { it.key }

        assertEquals(listOf(first, third, "addon:new:x"), ordered)
    }

    @Test
    fun `saving a visible order keeps providers that are not installed here`() {
        val updated = config.withVisibleOrder(listOf(third, first))

        assertEquals(listOf(third, first, second), updated.order)
    }

    @Test
    fun `config survives encoding and rejects unknown timeouts`() {
        val encoded = config.copy(timeoutSeconds = 30, applyToFirstPlayback = false).encode()

        assertEquals(config.copy(timeoutSeconds = 30, applyToFirstPlayback = false), StreamProviderPriorityConfig.decode(encoded))
        assertEquals(
            DEFAULT_STREAM_PROVIDER_PRIORITY_TIMEOUT_SECONDS,
            StreamProviderPriorityConfig.decode("""{"timeoutSeconds":7}""")?.timeoutSeconds,
        )
        assertNull(StreamProviderPriorityConfig.decode("not json"))
    }

    private fun evaluate(
        vararg groups: AddonStreamGroup,
        config: StreamProviderPriorityConfig = this.config,
        timeoutElapsed: Boolean = false,
    ): StreamProviderPriorityEvaluation = StreamProviderPrioritySelector.evaluate(
        groups = groups.toList(),
        config = config,
        timeoutElapsed = timeoutElapsed,
    )

    private fun group(key: String, vararg streams: StreamItem, loading: Boolean = false) = AddonStreamGroup(
        addonName = key,
        addonId = key,
        streams = streams.toList(),
        isLoading = loading,
    )

    private fun stream(
        key: String,
        name: String,
        url: String? = "https://example.com/$name.m3u8",
        addonId: String = key,
    ) = StreamItem(
        name = name,
        url = url,
        addonName = key,
        addonId = addonId,
        providerKey = key.takeIf { addonId != key },
    )
}
