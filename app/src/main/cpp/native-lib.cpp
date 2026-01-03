#include <jni.h>
#include <string>
#include <ngspice/sharedspice.h>

#include <android/log.h>

#define LOG_TAG "DroidSpice"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {
    static int cb_send_char(char* msg, int /*id*/, void* /*user*/)
    {
        if (msg) ALOGI("ngspice: %s", msg);
        return 0;
    }

    static int cb_send_stat(char* msg, int /*id*/, void* /*user*/)
    {
        if (msg) ALOGE("ngspice(stat): %s", msg);
        return 0;
    }

    static int cb_controlled_exit(int status, bool /*immediate*/, bool /*quit*/, int /*id*/, void* /*user*/)
    {
        ALOGI("ngspice controlled_exit status=%d", status);
        return 0;
    }

    static int cb_send_data(pvecvaluesall /*all*/, int /*num*/, int /*id*/, void* /*user*/) {
        // Not used for this smoke test; return 0.
        return 0;
    }

    static int cb_send_init(pvecinfoall /*all*/, int /*id*/, void* /*user*/) {
        // Not used for this smoke test; return 0.
        return 0;
    }

    static int cb_bg_thread_running(bool running, int /*id*/, void* /*user*/) {
        ALOGI("ngspice bg_thread_running=%d", running ? 1 : 0);
        return 0;
    }
}

static bool g_spice_inited = false;

static std::string init_and_smoketest_ngspice()
{
    if (!g_spice_inited) {
        // Note: last arg is "user data"; we pass nullptr.
        int rc = ngSpice_Init(
                cb_send_char,
                cb_send_stat,
                cb_controlled_exit,
                cb_send_data,
                cb_send_init,
                cb_bg_thread_running,
                nullptr
        );

        if (rc != 0) {
            ALOGE("ngSpice_Init failed rc=%d", rc);
            return "ngSpice_Init failed rc=" + std::to_string(rc);
        }

        g_spice_inited = true;
        ALOGI("ngSpice_Init OK");
    }

    // A very safe command that doesn't require a netlist:
    int cmdrc = ngSpice_Command((char*)"version");
    if (cmdrc != 0) {
        ALOGE("ngSpice_Command('version') failed rc=%d", cmdrc);
        return "ngSpice_Init OK, but version cmd failed rc=" + std::to_string(cmdrc);
    }

    ALOGI("ngSpice_Command('version') OK");
    return "ngspice OK (Init + version command). Check Logcat for output.";
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_devinrcohen_droidspice_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++\n" + init_and_smoketest_ngspice();
    return env->NewStringUTF(hello.c_str());
}