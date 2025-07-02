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
exec /Users/isdzulqor/Library/Android/sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android21-clang++ "${ARGS[@]}"