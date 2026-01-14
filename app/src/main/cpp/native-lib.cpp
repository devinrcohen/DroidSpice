#include <jni.h>

#include <string>
#include <mutex>
#include <atomic>
#include <condition_variable>
#include <sstream>
#include <vector>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <limits>

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

// Mutex for data aggregation
static std::mutex g_dataMutex;
static std::vector<std::string> g_vecNames;
static int g_vecCount = 0;

// Row-major samples:
//   stride=1 (real):   [s0v0, s0v1, ... s0vN-1, s1v0, ...]
//   stride=2 (complex):[s0v0R, s0v0I, s0v1R, s0v1I, ...]
static std::vector<double> g_samples;
static bool g_storeComplex = false;

// Background thread running flag (for analyses that execute async inside ngspice)
static std::mutex g_bgMutex;
static std::condition_variable g_bgCv;
static std::atomic<bool> g_bgRunning{false};

static void setBgRunning(bool running)
{
    {
        std::lock_guard<std::mutex> lk(g_bgMutex);
        g_bgRunning.store(running, std::memory_order_release);
    }
    g_bgCv.notify_all();
}

static void waitBgDone()
{
    std::unique_lock<std::mutex> lk(g_bgMutex);
    g_bgCv.wait(lk, [] { return !g_bgRunning.load(std::memory_order_acquire); });
}

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
    appendOutput(msg);
    return 0;
}

extern "C" int sendStat(char* msg, int /*id*/, void* /*user*/)
{
    appendOutput(msg);
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

extern "C" int sendData(pvecvaluesall vec, int /*num*/, int /*id*/, void* /*user*/)
{
    std::lock_guard<std::mutex> lk(g_dataMutex);

    const int n = vec->veccount;

    if (!g_storeComplex) {
        g_samples.reserve(g_samples.size() + n);
        for (int i = 0; i < n; ++i)
        {
            g_samples.push_back(vec->vecsa[i]->creal); // add data for real only
        }
    } else {
        g_samples.reserve(g_samples.size() + 2*n); // need two slots, for real and imaginary
        for (int i = 0; i < n; ++i)
        {
            g_samples.push_back(vec->vecsa[i]->creal);
            g_samples.push_back(vec->vecsa[i]->cimag);
        }
    }

    return 0;
}

extern "C" int sendInitData(pvecinfoall info, int /*id*/, void* /*user*/)
{
    std::lock_guard<std::mutex> lk(g_dataMutex);

    g_vecNames.clear();
    g_samples.clear();

    g_vecCount = info->veccount;
    g_vecNames.reserve(g_vecCount);

    for (int i=0; i < g_vecCount; ++i)
    {
        g_vecNames.emplace_back(info->vecs[i]->vecname);
    }
    return 0;
}

extern "C" int bgThreadRunning(bool running, int /*id*/, void* /*user*/)
{
    setBgRunning(running);
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
        g_hasLoadedCircuit.store(false, std::memory_order_release);
        setBgRunning(false);
    }

    return env->NewStringUTF("ngspice initialized.\n");
}

static int runCommand(const char* s)
{
    if (!s) return 1;
    return ngSpice_Command(const_cast<char*>(s));
}

static int runCirc(char** deck)
{
    if (!deck) return 1;
    return ngSpice_Circ(deck);
}

static std::vector<char*> buildDeck(const std::string& netlistStr)
{
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
    return cLines;
}

static void freeDeck(std::vector<char*>& deck)
{
    for (char* p : deck) {
        if (p) ::free(p);
    }
    deck.clear();
}

static bool analysisRequiresComplex(const std::string& cmd)
{
    // Simple heuristic: AC analysis produces complex results.
    // (OP and TRAN are real-valued for typical use.)
    std::string low;
    low.reserve(cmd.size());
    for (unsigned char ch : cmd) low.push_back((char)std::tolower(ch));
    // Trim leading spaces
    size_t start = low.find_first_not_of(" \t");
    if (start == std::string::npos) return false;
    low = low.substr(start);
    return (low.rfind("ac", 0) == 0);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_devinrcohen_droidspice_MainActivity_runAnalysis(JNIEnv* env, jobject /*thiz*/, jstring netlist, jstring analysisCmd)
{
    std::lock_guard<std::mutex> lock(g_spiceMutex);

    if (/*!g_initialized*/!g_initialized.load(std::memory_order_acquire)) {
        return env->NewStringUTF("ERROR: ngspice not initialized\n");
    }

    const char* nl = env->GetStringUTFChars(netlist, nullptr);
    std::string netlistStr = nl ? nl : "";
    env->ReleaseStringUTFChars(netlist, nl);

    const char* ac = env->GetStringUTFChars(analysisCmd, nullptr);
    std::string analysisStr = ac ? ac : "";
    env->ReleaseStringUTFChars(analysisCmd, ac);

    clearOutput();

    // Start from a clean ngspice state.
    // may complain on the first run because no circuit given, so nothing to "destroy" or "reset"
    // So check to see if a circuit has even been loaded yet
    // Clean ngspice state only after we've ever loaded a circuit.
    if (g_hasLoadedCircuit.load(std::memory_order_acquire)) {
        runCommand("destroy all");
        runCommand("reset");
        clearOutput();
    }

    // Set stride policy: complex for AC, real otherwise.
    g_storeComplex = analysisRequiresComplex(analysisStr);

    // Build and load deck.
    std::vector<char*> deck = buildDeck(netlistStr);
    int rc = runCirc(deck.data());
    if (rc == 0) g_hasLoadedCircuit.store(true, std::memory_order_release);
    freeDeck(deck);
    if (rc != 0) {
        // If load failed, return now with whatever ngspice said.
        std::string out = takeOutputSnapshot();
        return env->NewStringUTF(out.c_str());
    }

    // Run the requested analysis.
    setBgRunning(false);
    runCommand(analysisStr.c_str());
    waitBgDone();

    std::string out = takeOutputSnapshot();
    return env->NewStringUTF(out.c_str());
}

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_devinrcohen_droidspice_MainActivity_takeSamples(JNIEnv* env, jobject /*thiz*/)
{
    std::vector<double> tmp;
    {
        std::lock_guard<std::mutex> lk(g_dataMutex);
        // Drain quickly without copying.
        tmp.swap(g_samples);
    }

    jdoubleArray arr = env->NewDoubleArray((jsize) tmp.size());
    env->SetDoubleArrayRegion(arr, 0, (jsize)tmp.size(), tmp.data());
    return arr;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_devinrcohen_droidspice_MainActivity_getComplexStride(JNIEnv* /*env*/, jobject /*thiz*/)
{
    // 1 for real-only results, 2 for real+imag (AC)
    return g_storeComplex ? 2 : 1;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_devinrcohen_droidspice_MainActivity_getVecNames(JNIEnv* env, jobject /*thiz*/)
{
    std::lock_guard<std::mutex> lk(g_dataMutex);

    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray((jsize)g_vecNames.size(), strClass, nullptr);
    for (jsize i = 0; i < (jsize)g_vecNames.size(); ++i)
    {
        env->SetObjectArrayElement(arr, i, env->NewStringUTF(g_vecNames[i].c_str()));
    }
    return arr;
}
