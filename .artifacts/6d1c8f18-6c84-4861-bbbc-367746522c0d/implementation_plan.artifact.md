# Implementation Plan - Update StepCounterIO Transaction Management

Update the Kotlin `StepCounterIO` implementation to match the transaction management and caching logic from the Python `gshock_api` project. This ensures efficient data retrieval in "peek" mode and correct behavior when clearing history.

## Proposed Changes

### [Infrastructure]

#### [MODIFY] [StepCounterIO.kt](file:///home/izivkov/projects/CasioGShockSmartSync/api/src/main/java/org/avmedia/gshockapi/io/StepCounterIO.kt)

- **State Management**:
    - Add `private var transactionActive: Boolean = false` to the `StepCounterIO` object.
    - Add `private var lastData: StepCounterData? = null` to cache the latest results.
- **Request Logic (`getStepCount`)**:
    - If `peek` is `true`, `transactionActive` is `true`, and `lastData` is not null, return `lastData` immediately.
    - If `peek` is `false` and `transactionActive` is `true`, send `END_TRANSACTION_CMD` and set `transactionActive = false` before starting the new transaction.
    - Set `transactionActive = true` after sending `START_TRANSACTION_CMD`.
- **Response Logic (`onReceived`)**:
    - When a full payload is reassembled and parsed:
        - Store the result in `lastData`.
        - If `!peekMode`, set `transactionActive = false` (the watch closes the transaction after `END_TRANSACTION_CMD`).
- **Parsing Cleanup**:
    - Update `readUnsignedShortOrNull` to remove the `0xFFFF` check, matching Python's behavior of only filtering `0xFFFE` at the call site.
    - Ensure `readUnsignedIntOrNull` is only used where appropriate and handles its own logic for `SENTINEL_DAILY_VALUE` (0xFFFFFFFE).

## Verification Plan

### Automated Tests
- Run `:api:assembleDebug` and `:app:assembleDebug` to ensure compilation.

### Manual Verification
- **Peek Mode Efficiency**: Verify that consecutive calls to `getStepCount(peek = true)` return cached data without triggering new Bluetooth transactions if the transaction is still active.
- **Clear History Reliability**: Verify that `getStepCount(peek = false)` correctly closes any active "peek" transaction before starting a new one, ensuring the watch resets its lifelog buffers as expected.
