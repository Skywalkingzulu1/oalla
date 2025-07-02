#include <unistd.h>
#include <thread>
#include <jni.h>

int pipefd[2];
JavaVM* jvm = nullptr;
jobject activityGlobal = nullptr; // global reference to MainActivity

void startLogThread() {
    std::thread([]() {
        JNIEnv* env;
        jvm->AttachCurrentThread(&env, nullptr);

        jclass cls = env->GetObjectClass(activityGlobal);
        jmethodID logMethod = env->GetMethodID(cls, "logFromNative", "(Ljava/lang/String;)V");

        char buffer[1024];
        while (true) {
            ssize_t len = read(pipefd[0], buffer, sizeof(buffer) - 1);
            if (len > 0) {
                buffer[len] = '\0';
                jstring jmsg = env->NewStringUTF(buffer);
                env->CallVoidMethod(activityGlobal, logMethod, jmsg);
                env->DeleteLocalRef(jmsg);
            }
        }
    }).detach();
}

// TODO: change MainActivity to the actual activity name later on
extern "C" JNIEXPORT void JNICALL
Java_work_isdzulqor_oalla_MainActivity_runOllamaWithArgs(JNIEnv* env, jobject thiz, jobjectArray args) {
    env->GetJavaVM(&jvm);
    activityGlobal = env->NewGlobalRef(thiz);

    int argc = env->GetArrayLength(args);
    char** argv = new char*[argc];

    for (int i = 0; i < argc; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(args, i);
        const char* cstr = env->GetStringUTFChars(jstr, 0);
        argv[i] = strdup(cstr);
        env->ReleaseStringUTFChars(jstr, cstr);
        env->DeleteLocalRef(jstr);
    }

    pipe(pipefd);
    dup2(pipefd[1], STDOUT_FILENO);
    dup2(pipefd[1], STDERR_FILENO);

    startLogThread();

    extern void runOllamaWithArgs(char** argv, int argc); // Go exported func
    runOllamaWithArgs(argv, argc);

    for (int i = 0; i < argc; i++) free(argv[i]);
    delete[] argv;
}