#!/bin/bash

# Path to Android NDK clang++ compiler for target architecture (ARM64)
COMPILER=~/Library/Android/sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android21-clang++

# Compile the JNI bridge file into a shared library (.so)
# -fPIC: Generate position-independent code (required for shared libs)
# -shared: Output a shared object (i.e., .so file)
# -o libbridgeollama.so: Output file name
# jni_bridgeollama.cpp: Source file to compile
# -L. -lollama: Link against libollama.so in the current directory
$COMPILER \
  -fPIC -shared \
  -o libbridgeollama.so \
  jni_bridgeollama.cpp \
  -L. -lollama