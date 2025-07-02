.PHONY: *

# Makefile for applying patches to the project
# Usage:
#   make patch-file file=patches/ollama/00-prepare-llama-for-android.patch
patch-file:
	@echo "Applying patch: $(file)"
	patch -p1 < $(file)