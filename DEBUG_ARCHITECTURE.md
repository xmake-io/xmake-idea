# XMake IntelliJ Plugin - Dynamic Debug Module Architecture

## Overview

This plugin now uses a dynamic debug module architecture to handle CLion-specific debugging functionality while maintaining compatibility with other IntelliJ IDEs.

## Architecture

### Main Plugin (`xmake-idea`)
- **Core functionality**: XMake integration, build system, project management
- **Debug loader**: `DebugModuleLoader` - dynamically loads CLion debug module when needed
- **Fallback support**: Works with non-CLion IDEs without CLion dependencies

### CLion Debug Module (`clion-debug`)
- **Separate JAR**: `xmake-clion-debug.jar` compiled independently
- **CLion-specific**: Uses CLion 2025.3 debugging APIs
- **Self-contained**: Includes all necessary dependencies
- **Optional**: Only loaded when CLion is detected

## How It Works

1. **Detection**: Plugin detects if CLion is available
2. **Loading**: If CLion is found, loads `xmake-clion-debug.jar` dynamically
3. **Delegation**: Debug operations are delegated to the CLion module
4. **Fallback**: If CLion is not available, uses basic fallback functionality

## Building

### Build CLion Debug Module
```bash
./build-clion-debug.sh
```

This script:
1. Compiles the CLion debug module with CLion 2025.3 dependencies
2. Creates `xmake-clion-debug.jar` with all dependencies included
3. Copies the JAR to the main plugin's resources directory

### Build Main Plugin
```bash
./gradlew build
```

The main plugin will include the CLion debug JAR in its resources.

## File Structure

```
xmake-idea/
├── src/main/kotlin/io/xmake/
│   ├── debug/
│   │   ├── XMakeDapDriverConfiguration.kt    # Simplified, uses dynamic loading
│   │   ├── XMakeDebugSession.kt              # Simplified debug session
│   │   └── loader/
│   │       └── DebugModuleLoader.kt          # Dynamic module loader
│   └── run/
│       └── XMakeRunner.kt                    # Simplified, no compatibility layer
├── clion-debug/                               # Separate subproject
│   ├── build.gradle.kts                      # CLion-specific dependencies
│   └── src/main/kotlin/io/xmake/debug/clion/
│       └── ClionDebugModule.kt               # CLion debug implementation
├── resources/
│   └── xmake-clion-debug.jar                 # Generated CLion debug JAR
└── build-clion-debug.sh                      # Build script
```

## Benefits

1. **Version Compatibility**: Different IDE versions can have different debug modules
2. **Simplified Code**: Main plugin doesn't need complex compatibility layers
3. **Modular Design**: Debug functionality is isolated and independent
4. **Optional Loading**: Only loads CLion-specific code when needed
5. **Easy Maintenance**: Debug module can be updated independently

## Usage

### For CLion Users
- Full debugging functionality with variable display, breakpoints, etc.
- Uses CLion's native debugging infrastructure
- Automatic detection and loading of debug module

### For Other IDE Users
- Basic XMake functionality works without CLion dependencies
- No debugging features (or limited fallback debugging)
- Smaller plugin footprint

## Development

### Adding New IDE Support
1. Create a new debug module subproject (e.g., `pycharm-debug`)
2. Implement IDE-specific debug functionality
3. Update `DebugModuleLoader` to detect and load the new module
4. Build and include the new JAR

### Updating CLion Support
1. Modify `clion-debug/build.gradle.kts` for new CLion version
2. Update `ClionDebugModule.kt` for any API changes
3. Rebuild the debug module JAR
4. Test with new CLion version

## Troubleshooting

### Debug Module Not Loading
- Check if CLion plugin is installed and enabled
- Verify `xmake-clion-debug.jar` exists in resources
- Check logs for loading errors

### Build Issues
- Ensure CLion dependencies are available
- Check Java version compatibility
- Verify Gradle configuration

### Runtime Issues
- Check IDE version compatibility
- Verify debug module JAR is not corrupted
- Check class loading errors in logs
