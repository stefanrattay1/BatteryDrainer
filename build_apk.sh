#!/bin/bash
#
# Battery Drainer APK Build Script
# Builds debug and/or release APKs
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project directory (script location)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Output directory
OUTPUT_DIR="$SCRIPT_DIR/release-builds"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Battery Drainer APK Builder${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Try to use JDK 17 if available (required for this project)
if [ -d "/usr/lib/jvm/java-1.17.0-openjdk-amd64" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-1.17.0-openjdk-amd64"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "/usr/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Parse arguments
BUILD_TYPE="debug"
CLEAN_BUILD=false
INSTALL_APK=false
OPEN_OUTPUT=false

print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -d, --debug       Build debug APK (default)"
    echo "  -r, --release     Build release APK (requires signing config)"
    echo "  -a, --all         Build both debug and release APKs"
    echo "  -c, --clean       Clean before building"
    echo "  -i, --install     Install APK on connected device after build"
    echo "  -o, --open        Open output folder after build"
    echo "  -h, --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Build debug APK"
    echo "  $0 -r -c              # Clean build release APK"
    echo "  $0 -d -i              # Build and install debug APK"
    echo "  $0 -a                 # Build both debug and release"
}

while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--debug)
            BUILD_TYPE="debug"
            shift
            ;;
        -r|--release)
            BUILD_TYPE="release"
            shift
            ;;
        -a|--all)
            BUILD_TYPE="all"
            shift
            ;;
        -c|--clean)
            CLEAN_BUILD=true
            shift
            ;;
        -i|--install)
            INSTALL_APK=true
            shift
            ;;
        -o|--open)
            OPEN_OUTPUT=true
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

# Check for gradlew
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}Error: gradlew not found. Are you in the project root?${NC}"
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

# Check Java version
echo -e "${YELLOW}Checking Java version...${NC}"
if command -v java &> /dev/null; then
    java -version 2>&1 | head -1
else
    echo -e "${RED}Error: Java not found. Please install JDK 17+${NC}"
    exit 1
fi

# Clean if requested
if [ "$CLEAN_BUILD" = true ]; then
    echo ""
    echo -e "${YELLOW}Cleaning project...${NC}"
    ./gradlew clean
    echo -e "${GREEN}Clean complete!${NC}"
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Build functions
build_debug() {
    echo ""
    echo -e "${YELLOW}Building Debug APK...${NC}"
    ./gradlew assembleDebug
    
    # Copy APK to output directory
    DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$DEBUG_APK" ]; then
        TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        cp "$DEBUG_APK" "$OUTPUT_DIR/BatteryDrainer-debug-$TIMESTAMP.apk"
        cp "$DEBUG_APK" "$OUTPUT_DIR/BatteryDrainer-debug-latest.apk"
        echo -e "${GREEN}✓ Debug APK built successfully!${NC}"
        echo -e "  Location: ${BLUE}$OUTPUT_DIR/BatteryDrainer-debug-latest.apk${NC}"
        
        # Get APK size
        APK_SIZE=$(du -h "$DEBUG_APK" | cut -f1)
        echo -e "  Size: ${BLUE}$APK_SIZE${NC}"
    else
        echo -e "${RED}Error: Debug APK not found!${NC}"
        exit 1
    fi
}

build_release() {
    echo ""
    echo -e "${YELLOW}Building Release APK...${NC}"
    
    # Check for signing config
    KEYSTORE_PATH="$SCRIPT_DIR/keystore.jks"
    DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
    
    if [ -f "$KEYSTORE_PATH" ]; then
        echo -e "${GREEN}Using release keystore: $KEYSTORE_PATH${NC}"
    elif [ -f "$DEBUG_KEYSTORE" ]; then
        echo -e "${YELLOW}No release keystore found. Using debug keystore for signing.${NC}"
        KEYSTORE_PATH="$DEBUG_KEYSTORE"
        KEYSTORE_PASS="android"
        KEY_ALIAS="androiddebugkey"
        KEY_PASS="android"
    else
        echo -e "${YELLOW}Creating debug keystore for signing...${NC}"
        mkdir -p "$HOME/.android"
        keytool -genkey -v -keystore "$DEBUG_KEYSTORE" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null || true
        KEYSTORE_PATH="$DEBUG_KEYSTORE"
        KEYSTORE_PASS="android"
        KEY_ALIAS="androiddebugkey"
        KEY_PASS="android"
    fi
    
    ./gradlew assembleRelease
    
    # Copy APK to output directory
    RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
    RELEASE_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
    
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    
    if [ -f "$RELEASE_APK" ]; then
        cp "$RELEASE_APK" "$OUTPUT_DIR/BatteryDrainer-release-$TIMESTAMP.apk"
        cp "$RELEASE_APK" "$OUTPUT_DIR/BatteryDrainer-release-latest.apk"
        echo -e "${GREEN}✓ Release APK built successfully!${NC}"
        echo -e "  Location: ${BLUE}$OUTPUT_DIR/BatteryDrainer-release-latest.apk${NC}"
        
        APK_SIZE=$(du -h "$RELEASE_APK" | cut -f1)
        echo -e "  Size: ${BLUE}$APK_SIZE${NC}"
    elif [ -f "$RELEASE_UNSIGNED" ]; then
        cp "$RELEASE_UNSIGNED" "$OUTPUT_DIR/BatteryDrainer-release-unsigned-$TIMESTAMP.apk"
        echo -e "${YELLOW}Unsigned APK found. Signing with debug keystore...${NC}"
        
        # Sign the APK using apksigner or jarsigner
        SIGNED_APK="$OUTPUT_DIR/BatteryDrainer-release-$TIMESTAMP.apk"
        
        # Try zipalign first if available
        if command -v zipalign &> /dev/null; then
            ALIGNED_APK="$OUTPUT_DIR/BatteryDrainer-aligned-$TIMESTAMP.apk"
            zipalign -v -p 4 "$RELEASE_UNSIGNED" "$ALIGNED_APK" 2>/dev/null || cp "$RELEASE_UNSIGNED" "$ALIGNED_APK"
            APK_TO_SIGN="$ALIGNED_APK"
        else
            APK_TO_SIGN="$RELEASE_UNSIGNED"
        fi
        
        # Sign with apksigner (preferred) or jarsigner
        if command -v apksigner &> /dev/null; then
            apksigner sign --ks "$KEYSTORE_PATH" --ks-pass pass:${KEYSTORE_PASS:-android} --key-pass pass:${KEY_PASS:-android} --ks-key-alias ${KEY_ALIAS:-androiddebugkey} --out "$SIGNED_APK" "$APK_TO_SIGN"
            echo -e "${GREEN}✓ APK signed with apksigner${NC}"
        elif command -v jarsigner &> /dev/null; then
            cp "$APK_TO_SIGN" "$SIGNED_APK"
            jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$KEYSTORE_PATH" -storepass ${KEYSTORE_PASS:-android} -keypass ${KEY_PASS:-android} "$SIGNED_APK" ${KEY_ALIAS:-androiddebugkey}
            echo -e "${GREEN}✓ APK signed with jarsigner${NC}"
        else
            echo -e "${RED}Warning: No signing tool found (apksigner or jarsigner)${NC}"
            cp "$RELEASE_UNSIGNED" "$SIGNED_APK"
        fi
        
        # Clean up aligned APK if it exists
        [ -f "$ALIGNED_APK" ] && [ "$ALIGNED_APK" != "$SIGNED_APK" ] && rm -f "$ALIGNED_APK"
        
        # Copy to latest
        cp "$SIGNED_APK" "$OUTPUT_DIR/BatteryDrainer-release-latest.apk"
        
        echo -e "${GREEN}✓ Signed Release APK ready!${NC}"
        echo -e "  Location: ${BLUE}$OUTPUT_DIR/BatteryDrainer-release-latest.apk${NC}"
        
        APK_SIZE=$(du -h "$SIGNED_APK" | cut -f1)
        echo -e "  Size: ${BLUE}$APK_SIZE${NC}"
    else
        echo -e "${RED}Error: Release APK not found!${NC}"
        exit 1
    fi
}

build_bundle() {
    echo ""
    echo -e "${YELLOW}Building Release App Bundle (AAB) for Play Store...${NC}"
    
    ./gradlew bundleRelease
    
    BUNDLE="app/build/outputs/bundle/release/app-release.aab"
    if [ -f "$BUNDLE" ]; then
        TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        cp "$BUNDLE" "$OUTPUT_DIR/BatteryDrainer-release-$TIMESTAMP.aab"
        cp "$BUNDLE" "$OUTPUT_DIR/BatteryDrainer-release-latest.aab"
        echo -e "${GREEN}✓ App Bundle built successfully!${NC}"
        echo -e "  Location: ${BLUE}$OUTPUT_DIR/BatteryDrainer-release-latest.aab${NC}"
        
        BUNDLE_SIZE=$(du -h "$BUNDLE" | cut -f1)
        echo -e "  Size: ${BLUE}$BUNDLE_SIZE${NC}"
    else
        echo -e "${RED}Error: App Bundle not found!${NC}"
        exit 1
    fi
}

# Execute builds based on type
case $BUILD_TYPE in
    debug)
        build_debug
        ;;
    release)
        build_release
        build_bundle
        ;;
    all)
        build_debug
        build_release
        build_bundle
        ;;
esac

# Install APK if requested
if [ "$INSTALL_APK" = true ]; then
    echo ""
    echo -e "${YELLOW}Installing APK on device...${NC}"
    
    if ! command -v adb &> /dev/null; then
        echo -e "${RED}Error: adb not found. Cannot install APK.${NC}"
    else
        DEVICES=$(adb devices | grep -v "List" | grep "device" | wc -l)
        if [ "$DEVICES" -eq 0 ]; then
            echo -e "${RED}No device connected. Connect a device and try again.${NC}"
        else
            if [ "$BUILD_TYPE" = "release" ]; then
                APK_TO_INSTALL="$OUTPUT_DIR/BatteryDrainer-release-latest.apk"
            else
                APK_TO_INSTALL="$OUTPUT_DIR/BatteryDrainer-debug-latest.apk"
            fi
            
            if [ -f "$APK_TO_INSTALL" ]; then
                adb install -r "$APK_TO_INSTALL"
                echo -e "${GREEN}✓ APK installed successfully!${NC}"
            fi
        fi
    fi
fi

# Open output folder if requested
if [ "$OPEN_OUTPUT" = true ]; then
    echo ""
    if command -v xdg-open &> /dev/null; then
        xdg-open "$OUTPUT_DIR"
    elif command -v open &> /dev/null; then
        open "$OUTPUT_DIR"
    elif command -v explorer.exe &> /dev/null; then
        explorer.exe "$(wslpath -w "$OUTPUT_DIR")" 2>/dev/null || explorer.exe "$OUTPUT_DIR"
    fi
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   Build Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Output directory: ${BLUE}$OUTPUT_DIR${NC}"
echo ""
ls -lh "$OUTPUT_DIR"/*.apk "$OUTPUT_DIR"/*.aab 2>/dev/null || true
