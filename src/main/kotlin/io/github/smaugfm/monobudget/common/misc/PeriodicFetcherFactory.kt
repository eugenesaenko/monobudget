package io.github.smaugfm.monobudget.common.misc

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

@Single
class PeriodicFetcherFactory(private val scope: CoroutineScope) {
    fun <T> create(name: String, fetch: suspend () -> T) = PeriodicFetcher(name, 1.hours, fetch)

    inner class PeriodicFetcher<T>(
        name: String,
        interval: Duration,
        fetch: suspend () -> T
    ) {
        private val initial = CompletableDeferred<T>()

        @Volatile
        private var data: Deferred<T> = initial

        suspend fun getData() = withTimeout(5.seconds) {
            data.await()
        }

        init {
            log.info { "Launching periodic fetcher for $name" }
            scope.launch {
                while (true) {
                    log.trace { "$name fetching..." }
                    val result = try {
                        fetch()
                    } catch (e: Throwable) {
                        log.error(e) { "Error fetching $name: " }
                        delay(interval)
                        continue
                    }
                    if (data === initial) {
                        initial.complete(result)
                    }
                    data = CompletableDeferred(result)
                    log.trace { "$name fetched: $result" }
                    delay(interval)
                }
            }
        }
    }
}
