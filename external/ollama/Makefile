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
	@echo "Encrypting all files in web/..."
	mkdir -p web/encrypted
	find web -type f ! -path "web/encrypted/*" | while read filepath; do \
		filename=$$(basename $$filepath); \
		openssl enc -aes-128-ecb -base64 -in "$$filepath" -out "web/encrypted/$$filename" -K 31323334353637383930313233343536; \
	done
	@echo "Generating assets_version.txt with current UNIX timestamp..."
	date +%s > web/encrypted/assets_version.txt
	@echo "Copying encrypted files to Android assets..."
	cp -r web/encrypted/* ../../android/app/src/main/assets/public/