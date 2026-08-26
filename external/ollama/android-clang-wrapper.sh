#!/bin/bash

# Filter out -lrt and -lpthread
ARGS=()
for arg in "$@"; do
  if [[ "$arg" != "-lrt" && "$arg" != "-lpthread" ]]; then
    ARGS+=("$arg")
  fi
done

# Call the real Android NDK C++ compiler
# TODO: Use the correct path to the NDK toolchain
exec ${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang++ "${ARGS[@]}"