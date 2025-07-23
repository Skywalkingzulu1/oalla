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
	@echo "Cleanup old public files in Android assets... and web/encrypted"
	rm -rf ../../android/app/src/main/assets/public/*
	rm -rf web/encrypted/*

	@echo "Encrypting all files in web/..."
	mkdir -p web/encrypted

	# Encrypt each file's content and filename
	find web -type f ! -path "web/encrypted/*" | while read filepath; do \
		filename=$$(basename "$$filepath"); \
		enc_name=$$(echo -n "$$filename" | openssl enc -aes-128-ecb -base64 -K 31323334353637383930313233343536 | tr '/+' '_-'); \
		openssl enc -aes-128-ecb -base64 -in "$$filepath" -out "web/encrypted/$$enc_name" -K 31323334353637383930313233343536; \
	done

	@echo "Generating assets_version.txt with current UNIX timestamp..."
	date +%s > web/encrypted/assets_version.txt

	@echo "Copying encrypted files to Android assets..."
	cp -r web/encrypted/* ../../android/app/src/main/assets/public/