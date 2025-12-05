# Scratchpad Buffer Layout

## Overview
The scratchpad buffer uses a **fixed layout** where each client has a predetermined position in the buffer. This ensures data consistency across app restarts, regardless of client registration order.

## Buffer Layout

| Offset | Size | Client | Description |
|--------|------|--------|-------------|
| 0 | 3 bytes | `AlarmNameStorage` | Stores 6 alarm names using 3-bit encoding (18 bits total = 3 bytes) |
| 3 | 2 bytes | `ActionsStorage` | Stores 9 boolean action flags using 1-bit encoding (9 bits = 2 bytes) |
| **Total** | **5 bytes** | | |

## Architecture

### Fixed Offset Design
Each `ScratchpadClient` implementation declares its fixed position via `getStorageOffset()`:

```kotlin
interface ScratchpadClient {
    fun getStorageOffset(): Int  // Fixed position in buffer
    fun getStorageSize(): Int    // Number of bytes needed
    fun setBuffer(buffer: ByteArray)
    fun getBuffer(): ByteArray
}
```

### Benefits
1. **Order-independent**: Clients can register in any order
2. **Persistent-safe**: Buffer loaded after restart will map correctly to clients
3. **Predictable**: Easy to debug and verify buffer contents
4. **Extensible**: New clients can be added by declaring their offset

### Adding New Clients
To add a new client:

1. Determine the next available offset (currently 5)
2. Implement `ScratchpadClient` interface
3. Override `getStorageOffset()` to return your fixed offset
4. Override `getStorageSize()` to return your required bytes
5. Register with `ScratchpadManager` in your `init` block

Example:
```kotlin
@Singleton
class MyNewStorage @Inject constructor(
    private val manager: ScratchpadManager
) : ScratchpadClient {
    
    init {
        manager.register(this)
    }
    
    override fun getStorageOffset(): Int = 5  // Starts after ActionsStorage
    override fun getStorageSize(): Int = 4     // Needs 4 bytes
    
    // ... implement setBuffer() and getBuffer()
}
```

## Implementation Details

### AlarmNameStorage (Offset: 0, Size: 3 bytes)
- Stores 6 alarm names
- Each name encoded as 3-bit index (0-5 for names, 7 for "no name")
- Packing: 2 names per byte (lower 3 bits + upper 3 bits shifted by 4)
- Total: 6 names × 3 bits = 18 bits = 3 bytes

### ActionsStorage (Offset: 3, Size: 2 bytes)
- Stores 9 boolean action flags
- Each action encoded as 1 bit
- Actions: SET_TIME, REMINDERS, PHONE_FINDER, TAKE_PHOTO, FLASHLIGHT, VOICE_ASSIST, SKIP_TO_NEXT_TRACK, PRAYER_ALARMS, PHONE_CALL
- Total: 9 actions × 1 bit = 9 bits = 2 bytes (rounded up)

## Migration Notes
The previous implementation used dynamic layout based on registration order. This has been replaced with fixed offsets to ensure data integrity across app restarts.
