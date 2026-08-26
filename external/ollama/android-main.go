package main

import "C"
import (
	"context"
	"fmt"
	"log"
	"os"
	"unsafe"

	"github.com/ollama/ollama/cmd"
	"github.com/ollama/ollama/llm"
	"github.com/ollama/ollama/runner"
)

func init() {
	llm.RunRunnerFunc = func(args []string) error {
		return runner.Execute(args)
	}
}

//export runOllamaWithArgs
func runOllamaWithArgs(argv **C.char, argc C.int) {
	fmt.Println("Running Ollama CLI with custom args...")

	args := make([]string, int(argc))
	argPtrs := (*[1 << 16]*C.char)(unsafe.Pointer(argv))[:argc:argc]
	for i := range args {
		args[i] = C.GoString(argPtrs[i])
	}

	// TODO: tidy up these environment variables
	modelsDir := os.Getenv("OLLAMA_MODELS")
	if modelsDir == "" {
		modelsDir = "/data/data/com.doctorsonwheels.wheelmd/files/.ollama"
	}
	cacheDir := "/data/data/com.doctorsonwheels.wheelmd/cache"
	os.Setenv("OLLAMA_MODELS", modelsDir)
	os.Setenv("OLLAMA_WEB_STATIC_DIR", "/data/data/com.doctorsonwheels.wheelmd/files/public")
	os.Setenv("HOME", "/data/data/com.doctorsonwheels.wheelmd/files")
	os.Setenv("TMPDIR", cacheDir)
	os.Setenv("ANDROID_DATA", "/data")
	os.Setenv("ANDROID_ROOT", "/system")

	cli := cmd.NewCLI()
	cli.SetArgs(args)
	if err := cli.ExecuteContext(context.Background()); err != nil {
		log.Printf("ollama cli error: %v", err)
		os.Exit(1)
	}
}

func main() {} // Required for cgo
