#!/bin/bash

# Colors for console output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to display usage instructions
show_help() {
    echo -e "${BLUE}Vehicle Recognition App - Helper Script${NC}"
    echo ""
    echo -e "Usage: ${YELLOW}./run.sh [COMMAND]${NC}"
    echo ""
    echo "Commands:"
    echo -e "  ${GREEN}build${NC}               Build the project"
    echo -e "  ${GREEN}clean${NC}               Clean the build"
    echo -e "  ${GREEN}install${NC}             Install the app on a connected device/emulator"
    echo -e "  ${GREEN}run${NC}                 Build and install the app on a connected device/emulator"
    echo -e "  ${GREEN}update-jdk${NC}          Update project to use JDK 17"
    echo -e "  ${GREEN}fix-deprecations${NC}    Show recommendations for fixing Gradle deprecation warnings"
    echo -e "  ${GREEN}quiet-build${NC}         Build without showing deprecation warnings"
    echo -e "  ${GREEN}help${NC}                Show this help message"
    echo -e "  ${GREEN}apk${NC}                 Build debug APK"
    echo -e "  ${GREEN}apk-release${NC}         Build release APK"
    echo ""
}

# Check if no arguments provided
if [ $# -eq 0 ]; then
    show_help
    exit 1
fi

# Handle commands
case "$1" in
    build)
        echo -e "${BLUE}Building the project...${NC}"
        ./gradlew build
        ;;
    clean)
        echo -e "${BLUE}Cleaning the build...${NC}"
        ./gradlew clean
        ;;
    install)
        echo -e "${BLUE}Installing the app...${NC}"
        ./gradlew installDebug
        ;;
    run)
        echo -e "${BLUE}Building and installing the app...${NC}"
        ./gradlew build installDebug
        ;;
    update-jdk)
        echo -e "${BLUE}Updating project to use JDK 17...${NC}"
        echo "org.gradle.java.home=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home" > gradle.properties
        echo "org.gradle.warning.mode=all" >> gradle.properties
        echo "android.useAndroidX=true" >> gradle.properties
        echo "android.enableJetifier=true" >> gradle.properties
        echo "org.gradle.jvmargs=-Xmx2048m -Xms512m -XX:MaxMetaspaceSize=512m" >> gradle.properties
        echo -e "${GREEN}JDK 17 configuration updated in gradle.properties${NC}"
        ;;
    fix-deprecations)
        echo -e "${BLUE}Running deprecation recommendations...${NC}"
        ./gradlew identifyDeprecations
        ;;
    quiet-build)
        echo -e "${BLUE}Building without deprecation warnings...${NC}"
        ./gradlew build --warning-mode none
        ;;
    help)
        show_help
        ;;
    apk)
        echo -e "${BLUE}Building Debug APK...${NC}"
        ./gradlew assembleDebug
        echo -e "${GREEN}APK created at: androidApp/build/outputs/apk/debug/androidApp-debug.apk${NC}"
        ;;
    apk-release)
        echo -e "${BLUE}Building Release APK...${NC}"
        ./gradlew assembleRelease
        echo -e "${GREEN}APK created at: androidApp/build/outputs/apk/release/androidApp-release.apk${NC}"
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        show_help
        exit 1
        ;;
esac 