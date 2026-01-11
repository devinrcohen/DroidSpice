#include <jni.h>

#include <string>
#include <mutex>
#include <atomic>
#include <sstream>
#include <vector>
#include <cctype>
#include <cstdlib>
#include <cstring>

extern "C" {
#include <ngspice/sharedspice.h>
}

//static bool g_initialized = false;
static std::atomic<bool> g_initialized{false};
static std::atomic<bool> g_hasLoadedCircuit{false};

// Mutex for ngspice calls (serialize init/run)
static std::mutex g_spiceMutex;

// Mutex for output aggregation (callbacks may come asynchronously)
static std::mutex g_outMutex;
static std::string g_output;

static void clearOutput()
{
    std::lock_guard<std::mutex> lk(g_outMutex);
    g_output.clear();
}

// updates the static g_output string (guarded by a mutex)
static void appendOutput(const char* s)
{
    if (!s) return;
    std::lock_guard<std::mutex> lk(g_outMutex);
    g_output.append(s);
    // ngspice often sends strings without newline
    if (!g_output.empty() && g_output.back() != '\n') {
        g_output.push_back('\n');
    }
}

// returns the g_output string (guarded by a mutex)
static std::string takeOutputSnapshot()
{
    std::lock_guard<std::mutex> lk(g_outMutex);
    return g_output;
}

/* -------------------- ngspice callbacks -------------------- */

extern "C" int sendChar(char* msg, int /*id*/, void* /*user*/)
{
    char buffer[100];
    sprintf(buffer, "[char]: %s", msg);
    appendOutput(buffer);
    return 0;
}

extern "C" int sendStat(char* msg, int /*id*/, void* /*user*/)
{
    char buffer[100];
    sprintf(buffer, "[stat]: %s", msg);
    appendOutput(buffer);
    return 0;
}

extern "C" int controlledExit(int status, bool /*immediate*/, bool /*quit*/, int /*id*/, void* /*user*/)
{
    appendOutput("\n[ngspice controlledExit]\n");
    std::string s = "[ngspice exited with status " + std::to_string(status) + "]";
    appendOutput(s.c_str());
    //g_initialized = false;
    g_initialized.store(false, std::memory_order_release);
    return 0;
}

extern "C" int sendData(pvecvaluesall /*vec*/, int /*num*/, int /*id*/, void* /*user*/)
{
    return 0;
}

extern "C" int sendInitData(pvecinfoall /*vec*/, int /*id*/, void* /*user*/)
{
    return 0;
}

extern "C" int bgThreadRunning(bool /*running*/, int /*id*/, void* /*user*/)
{
    return 0;
}

/* -------------------- helpers for deck normalization -------------------- */

static std::string normalizeLine(std::string s)
{
    // Strip CR (Windows line endings)
    if (!s.empty() && s.back() == '\r') {
        s.pop_back();
    }

    // Convert UTF-8 NBSP (0xC2 0xA0) to normal space.
    // Some Android IMEs insert NBSP and ngspice may treat it as an illegal token separator.
    for (size_t i = 0; i + 1 < s.size(); ) {
        unsigned char c0 = static_cast<unsigned char>(s[i]);
        unsigned char c1 = static_cast<unsigned char>(s[i + 1]);
        if (c0 == 0xC2 && c1 == 0xA0) {
            s.replace(i, 2, " ");
            i += 1;
        } else {
            i += 1;
        }
    }

    // Trim right-side spaces/tabs
    while (!s.empty() && (s.back() == ' ' || s.back() == '\t')) {
        s.pop_back();
    }

    return s;
}

static bool lineContainsDotEnd(const std::string& line)
{
    // case-insensitive search for ".end"
    std::string low;
    low.reserve(line.size());
    for (unsigned char ch : line) {
        low.push_back(static_cast<char>(std::tolower(ch)));
    }
    return (low.find(".end") != std::string::npos);
}

/* -------------------- JNI -------------------- */

extern "C"
JNIEXPORT jstring JNICALL
Java_com_devinrcohen_droidspice_MainActivity_initNgspice(JNIEnv* env, jobject /*thiz*/)
{
    std::lock_guard<std::mutex> lock(g_spiceMutex);

    if (/*!g_initialized*/ !g_initialized.load(std::memory_order_acquire)) {
        clearOutput();

        int ret = ngSpice_Init(
                sendChar,
                sendStat,
                controlledExit,
                sendData,
                sendInitData,
                bgThreadRunning,
                nullptr
        );

        if (ret != 0) {
            std::string msg = "ERROR: ngSpice_Init failed, ret=" + std::to_string(ret) + "\n";
            return env->NewStringUTF(msg.c_str());
        }

        //g_initialized = true;
        g_initialized.store(true, std::memory_order_release);
    }

    return env->NewStringUTF("ngspice initialized.\n");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_devinrcohen_droidspice_MainActivity_runOp(JNIEnv* env, jobject /*thiz*/, jstring netlist)
{
    std::lock_guard<std::mutex> lock(g_spiceMutex);


    // let's see the results of every individual command
    auto cmd = [&](const char*s, const bool display_message) {
        if(display_message) appendOutput((std::string("[CMD] ") + s + "\n").c_str());
        ngSpice_Command(const_cast<char*>(s));
    };

    auto circ = [&](std::vector<char *>& cLines, const bool display_message) {
        if(display_message)
            for (const char* line : cLines)
            {
                if (!line) break; // stop at terminator
                appendOutput((std::string("[CIRC] ") + std::string(line) + "\n").c_str());
            }
        int rc = ngSpice_Circ(const_cast<char**>(cLines.data()));
        //appendOutput((("\n[CIRC] ngSpiceCirc rc=") + std::to_string(rc) + "\n").c_str());
        return rc;
    };

    if (/*!g_initialized*/!g_initialized.load(std::memory_order_acquire)) {
        return env->NewStringUTF("ERROR: ngspice not initialized\n");
    }

    const char* nl = env->GetStringUTFChars(netlist, nullptr);
    std::string netlistStr = nl ? nl : "";
    env->ReleaseStringUTFChars(netlist, nl);

    clearOutput();

    // Start from a clean ngspice state.
    // may complain on the first run because no circuit given, so nothing to "destroy" or "reset"
    // So check to see if a circuit has even been loaded yet
    if(g_hasLoadedCircuit.load(std::memory_order_acquire)){
        cmd(const_cast<char *>("destroy all"), false);
        cmd(const_cast<char *>("reset"), false);
    }


    // Build a strict, NULL-terminated deck for ngSpice_Circ:
    // - normalized whitespace
    // - no blank lines
    // - each line ends with '\n'
    std::vector<std::string> lines;
    lines.reserve(64);

    bool hasEnd = false;
    {
        std::stringstream ss(netlistStr);
        std::string line;
        while (std::getline(ss, line)) {
            line = normalizeLine(line);

            // Skip truly blank lines
            if (line.find_first_not_of(" \t") == std::string::npos) {
                continue;
            }

            if (lineContainsDotEnd(line)) {
                hasEnd = true;
            }

            // Ensure newline termination for robustness
            line.push_back('\n');
            lines.push_back(line);
        }
    }

    if (!hasEnd) {
        lines.push_back(std::string(".end\n"));
    }

    // Convert to char** (NULL-terminated)
    std::vector<char*> cLines;
    cLines.reserve(lines.size() + 1);

    for (const auto& l : lines) {
        cLines.push_back(::strdup(l.c_str()));
    }
    cLines.push_back(nullptr);

    int rc = circ(cLines, false);
    appendOutput(("[CIRC] rc=" + std::to_string(rc) + "\n").c_str());

    if (rc == 0) g_hasLoadedCircuit.store(true, std::memory_order_release);

    for (char* p : cLines) {
        if (p) ::free(p);
    }

    // Instrumentation: show load status and dump what ngspice thinks is loaded.
    {
        std::string msg = "\nngSpice_Circ rc=" + std::to_string(rc) + "\n";
        appendOutput(msg.c_str());
    }

    // listing is the quickest "do we have a circuit?" proof
    //cmd(const_cast<char*>("listing"), false);
    if (rc != 0) {
        // If load failed, return now with whatever ngspice said.
        std::string out = takeOutputSnapshot();
        return env->NewStringUTF(out.c_str());
    }

    // Run operating point and print results.
    appendOutput("\"op\" command sent\n==========================\n");
    cmd(const_cast<char*>("op"), false);

    g_output.clear();
    appendOutput("\"print all\" command sent\n==========================\n");
    cmd(const_cast<char*>("print all"), false);
    char* v2request = "v(2)";
    pvector_info v2 = ngGet_Vec_Info(v2request);
    char v2_buffer[100];
    sprintf(v2_buffer, "v(2): %f", v2->v_realdata[0]);
    appendOutput(v2_buffer);
    std::string out = takeOutputSnapshot();
    return env->NewStringUTF(out.c_str());
}

extern "C"
JNIEXPORT jdouble JNICALL
Java_com_devinrcohen_droidspice_MainActivity_getOpVoltage(JNIEnv* env, jobject /*thiz*/, jstring netname)
{
    return 5.0f;
}