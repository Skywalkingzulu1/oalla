.PHONY: *

build-complete-ollama:
	@echo "Building Ollama from Go source..."
	bash ./build_android.sh
	@echo "Building JNI bridge for Ollama library..."
	bash ./build_bridgeollama.sh
	@echo "Move Ollama & JNI library to Android project..."
	mv libollama.so ../../android/app/src/main/jniLibs/arm64-v8a/libollama.so
	mv libbridgeollama.so ../../android/app/src/main/jniLibs/arm64-v8a/libbridgeollama.so

copy-ollama-web-to-android:
	@echo "Copying Ollama web files to Android project..."
	cp -r web/* ../../android/app/src/main/assets/public/