# AirGesture Project - Agent Development Guide

## Project Overview
AirGesture is an Android application that uses MediaPipe hand tracking to recognize gestures and perform system actions like page navigation, scrolling, screenshots, and app switching.

## Build Commands

### Building the Application
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Build and install on connected device
./gradlew installDebug

# Build with specific ABI (faster builds during development)
./gradlew assembleDebug -Pandroid.injected.abi.filters=arm64-v8a
```

### Running Tests
```bash
# Run all unit tests
./gradlew test

# Run unit tests for debug variant
./gradlew testDebugUnitTest

# Run specific test class
./gradlew test --tests "com.device.airgesture.ExampleUnitTest"

# Run specific test method
./gradlew test --tests "com.device.airgesture.ExampleUnitTest.addition_isCorrect"

# Run instrumented tests on connected device
./gradlew connectedAndroidTest

# Run tests with coverage
./gradlew createDebugCoverageReport
```

### Code Quality & Linting
```bash
# Run lint checks
./gradlew lint

# Run lint and auto-fix safe issues
./gradlew lintFix

# Generate lint report
./gradlew lintDebug

# Check for build issues
./gradlew check
```

## Code Style Guidelines

### Kotlin Style
- **Indentation**: 4 spaces (no tabs)
- **Max line length**: 120 characters
- **Naming conventions**:
  - Classes: PascalCase (e.g., `GestureRecognitionService`)
  - Functions: camelCase (e.g., `detectGesture()`)
  - Constants: UPPER_SNAKE_CASE (e.g., `NOTIFICATION_ID`)
  - Properties: camelCase (e.g., `gestureManager`)
  - Packages: lowercase (e.g., `com.device.airgesture.action`)

### Import Organization
```kotlin
// Order of imports (separated by blank lines):
import android.*                    // Android framework
import androidx.*                   // AndroidX libraries
import com.google.*                // Third-party libraries
import com.device.airgesture.*      // Project imports
import kotlinx.*                    // Kotlin extensions
import java.*                       // Java standard library
import javax.*                      // Java extensions
```

### Class Structure
```kotlin
class ExampleClass {
    // 1. Companion object
    companion object {
        private const val TAG = "ExampleClass"
    }
    
    // 2. Properties (private first, then internal, then public)
    private var privateProperty: String? = null
    internal var internalProperty: Int = 0
    var publicProperty: Boolean = false
    
    // 3. Init blocks
    init {
        // Initialization code
    }
    
    // 4. Secondary constructors
    constructor(param: String) : this() {
        // Constructor code
    }
    
    // 5. Override functions
    override fun onCreate() {
        // Override implementation
    }
    
    // 6. Public functions
    fun publicMethod() {
        // Public method implementation
    }
    
    // 7. Internal functions
    internal fun internalMethod() {
        // Internal method implementation
    }
    
    // 8. Private functions
    private fun privateMethod() {
        // Private method implementation
    }
}
```

### Error Handling
```kotlin
// Always use try-catch for risky operations
try {
    riskyOperation()
} catch (e: SpecificException) {
    LogUtil.e(TAG, "Specific error occurred", e)
    // Handle specific case
} catch (e: Exception) {
    LogUtil.e(TAG, "Unexpected error", e)
    // Handle general case
}

// For service operations, always check availability
executor?.let { exec ->
    if (exec.isAvailable()) {
        exec.execute(action)
    } else {
        LogUtil.w(TAG, "Executor not available")
    }
} ?: LogUtil.w(TAG, "Executor not set")
```

### Resource Management
- Always release resources in `onDestroy()` or `finally` blocks
- Use `?.let { }` for nullable resources
- Close streams, cursors, and native resources explicitly

### API Level Checks
```kotlin
// Always check API level for version-specific features
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    // Use Android 13+ specific API
} else {
    // Fallback for older versions
}
```

## Project-Specific Guidelines

### Service Communication
- Use `Context.RECEIVER_NOT_EXPORTED` for internal broadcasts (Android 13+)
- Prefer service binding over broadcasts for inter-service communication
- Always handle service lifecycle properly

### Gesture Recognition
- C++ gesture analyzer returns: -2 (DOWN), -1 (LEFT), 0 (NONE), 1 (RIGHT), 2 (UP)
- MediaPipe hand landmarks: 21 points per hand, index 5 is INDEX_MCP
- Gesture detection threshold constants are in `GestureAnalyzer.h`

### Permission Handling
Required permissions:
- `CAMERA` - For hand tracking
- `SYSTEM_ALERT_WINDOW` - For overlay display
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CAMERA` - For background camera access
- `POST_NOTIFICATIONS` - For foreground service notification (Android 13+)
- Accessibility Service - For performing system actions

### Native Code (C++)
- Located in `app/src/main/cpp/`
- Use `LOGD` macro for debug logging
- JNI methods must match Java package structure exactly
- Build with CMake, configured in `app/build.gradle`

## Common Tasks

### Adding a New Gesture
1. Add enum value to `Gesture.kt`
2. Add detection logic in `GestureAnalyzer.cpp`
3. Map gesture to action in `GestureActionManager.kt`
4. Update `GestureRecognitionService.kt` to handle new gesture code

### Adding a New Action
1. Add enum value to `Action.kt`
2. Implement execution in `KeyActionExecutor.kt`
3. Add default mapping in `GestureActionManager.defaultGestureToAction`
4. Update configuration UI in `GestureConfigActivity.kt`

## Debugging Tips
- Use `LogUtil` for consistent logging with tag prefix "AirGesture_"
- Check logcat with: `adb logcat | grep AirGesture_`
- For native code: `adb logcat | grep GestureCpp`
- Enable USB debugging and use Android Studio's debugger for breakpoints

## Important Notes
- App targets SDK 36, minSdk 24
- Uses AndroidX and Compose for UI
- MediaPipe models stored in `assets/` folder
- Configuration stored in SharedPreferences
- Service must run as foreground service for camera access