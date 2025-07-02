#!/bin/bash

# Set the Android NDK path
export NDK_HOME=~/Library/Android/sdk/ndk/26.1.10909125
export TOOLCHAIN=$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64

# Set compiler paths
export CC=$TOOLCHAIN/bin/aarch64-linux-android21-clang
export CXX="$PWD/android-clang-wrapper.sh"  # C++ wrapper to work around Go's build expectations

# Set linker flags for Android shared library
export CGO_LDFLAGS="-ldl -llog -lm -lstdc++"

# Build Go code as Android shared library
CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
  CC=$CC CXX=$CXX \
  go build -buildmode=c-shared -ldflags="-linkmode=external" -o libollama.so android-main.go