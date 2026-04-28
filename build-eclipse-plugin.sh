#!/bin/bash
# Builds rejd-all.jar for the Eclipse plugin (x86_64 macOS).
# The shade plugin occasionally includes ARM64 dylibs from the mac-aarch64 JARs.
# This script patches all native dylibs to be x86_64 after the shade build.
set -e

PLUGIN_LIB="GUI Portion/plugin/lib"
M2="$HOME/.m2/repository/org/openjfx"
JFX_VER="17.0.10"
WORK="/tmp/rejd-x86-patch"

echo "==> Building fat JAR with eclipse-bundle profile..."
/opt/homebrew/bin/mvn package -Peclipse-bundle -q

JAR="target/rejd-0.0.1-SNAPSHOT-eclipse-plugin.jar"
echo "==> Patching ARM64 dylibs to x86_64..."

mkdir -p "$WORK"
cp "$JAR" "$WORK/rejd-all.jar"

# Extract x86_64 dylibs from mac-classifier JARs
for module in javafx-graphics javafx-web javafx-media; do
    MAC_JAR="$M2/$module/$JFX_VER/$module-$JFX_VER-mac.jar"
    if [ -f "$MAC_JAR" ]; then
        cd "$WORK"
        jar xf "$MAC_JAR" $(jar tf "$MAC_JAR" | grep "\.dylib$" | tr '\n' ' ') 2>/dev/null || true
        DYLIBS=$(jar tf "$MAC_JAR" | grep "\.dylib$")
        if [ -n "$DYLIBS" ]; then
            jar uf rejd-all.jar $DYLIBS
        fi
        cd - > /dev/null
    fi
done

echo "==> Verifying all dylibs are x86_64..."
cd "$WORK"
jar xf rejd-all.jar $(jar tf rejd-all.jar | grep "\.dylib$" | tr '\n' ' ') 2>/dev/null || true
for f in *.dylib; do
    ARCH=$(file "$f" | grep -o 'x86_64\|arm64')
    if [ "$ARCH" != "x86_64" ]; then
        echo "WARNING: $f is $ARCH (expected x86_64)"
    fi
done
cd - > /dev/null

echo "==> Deploying to $PLUGIN_LIB/rejd-all.jar..."
cp "$WORK/rejd-all.jar" "$PLUGIN_LIB/rejd-all.jar"

echo "==> Clearing JavaFX cache..."
rm -rf "$HOME/.openjfx/cache/$JFX_VER/"

echo "==> Done. rejd-all.jar deployed with x86_64 dylibs."
