# TODO: Update the NDK version if necessary.
export NDK_HOME=~/Library/Android/sdk/ndk/26.1.10909125
export TOOLCHAIN=$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64

export CC=$TOOLCHAIN/bin/aarch64-linux-android21-clang
export CXX="$PWD/android-clang-wrapper.sh"   # use wrapper here
export CGO_LDFLAGS="-ldl -llog -lm -lstdc++"

CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
  CC=$CC CXX=$CXX \
  go build -buildmode=c-shared -ldflags="-linkmode=external" -o libollama.so android-main.go