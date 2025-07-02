#!/bin/bash

# Remove -lrt and -lpthread from the linker arguments
ARGS=()
for arg in "$@"; do
  if [[ "$arg" != "-lrt" && "$arg" != "-lpthread" ]]; then
    ARGS+=("$arg")
  fi
done

# Call the actual linker
# TODO: Use the correct path to the Android NDK clang++ binary
exec /Users/isdzulqor/Library/Android/sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android21-clang++ "${ARGS[@]}"