package org.avmedia.gshockapi.io

import kotlinx.coroutines.sync.Mutex

/**
 * The watch link carries one exchange at a time. A LifeLog transfer holds it for the whole transfer and waits for
 * it; a raw or settings read takes it with tryLock and does not start (returns nothing) while a transfer runs.
 */
object WatchExchange {
    val mutex = Mutex()
}
