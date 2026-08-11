/*
 * Small ART-side compatibility layer for R0DUMP.
 *
 * The upstream R0DUMP patch was exported from a tree with an additional
 * Far0t runtime.  Keep the public ABI used by the Java and ART call sites in
 * one translation unit so the feature can be enabled incrementally on newer
 * ART revisions.
 */

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <ctime>
#include <limits>
#include <mutex>
#include <set>
#include <string>
#include <vector>

#include "android-base/stringprintf.h"
#include "art_method-inl.h"
#include "base/logging.h"
#include "dex/dex_file-inl.h"
#include "dex/standard_dex_file.h"
#include "jvalue.h"
#include "thread-current-inl.h"

namespace art HIDDEN {

extern "C" bool isR0DumpStrategyEnabled(uint32_t strategy);

namespace {

constexpr uint32_t kStrategyDexLoad = 1u << 5;
constexpr uint32_t kStrategyForceBackfill = 1u << 11;
constexpr uint32_t kStrategyForceBackfillBefore = 1u << 12;
constexpr uint32_t kStrategyForceBackfillAfter = 1u << 13;
constexpr size_t kMaxDexExportSize = 512u * 1024u * 1024u;
constexpr size_t kMaxCodeItemSize = 4u * 1024u * 1024u;

std::mutex gR0DumpMutex;
std::mutex gR0DumpIoMutex;
std::atomic<bool> gR0DumpEnabled{false};
std::atomic<bool> gR0DumpTerminal{false};
std::atomic<uint32_t> gR0DumpStrategyMask{0u};
std::string gR0DumpOutputRoot = "/sdcard/Download/R0DUMP";
std::string gR0DumpPackageName = "unknown";
std::string gR0DumpProcessName = "unknown";
std::string gR0DumpRunId = "legacy";
std::string gR0DumpStopReason;
std::string gR0DumpPhase = "unknown";
std::string gR0DumpClassLoadersJson = "[]";
std::atomic<uint64_t> gR0DumpMaxRecords{50000u};
std::atomic<uint64_t> gR0DumpMaxSeconds{300u};
std::atomic<bool> gR0DumpStopAfterComplete{true};
std::atomic<time_t> gR0DumpStartedAt{0};
std::atomic<uint64_t> gR0DumpMethodRecords{0u};
std::atomic<uint64_t> gR0DumpDexFiles{0u};
std::atomic<uint64_t> gR0DumpDexDataFiles{0u};
std::atomic<uint64_t> gR0DumpDuplicates{0u};
std::atomic<uint64_t> gR0DumpInvalid{0u};
std::atomic<uint64_t> gR0DumpNonstandardDexSkipped{0u};
std::atomic<uint64_t> gR0DumpClassLoaderCandidates{0u};
std::atomic<uint64_t> gR0DumpClassLoadersWalked{0u};
std::atomic<uint64_t> gR0DumpClassLoaderDexElements{0u};
std::atomic<uint64_t> gR0DumpClassLoaderUniqueCookies{0u};
std::atomic<uint64_t> gR0DumpLoadedClassTableClasses{0u};
std::atomic<uint64_t> gR0DumpManifestComponentClasses{0u};
std::atomic<uint64_t> gR0DumpManifestSeedDumped{0u};
std::atomic<bool> gR0DumpRawMirror{false};
std::atomic<bool> gR0DumpAsync{false};
uint64_t gR0DumpForceMaxMethods = 200u;
std::set<std::string> gR0DumpDexKeys;
std::set<std::string> gR0DumpDexDataKeys;
std::set<std::string> gR0DumpMethodKeys;
std::atomic<uint64_t> gR0DumpFixedDexFiles{0u};
std::atomic<uint64_t> gR0DumpContainerFiles{0u};
std::atomic<uint64_t> gR0DumpReconstructionFailures{0u};
std::atomic<uint64_t> gR0DumpForceBackfillAttempts{0u};
std::atomic<uint64_t> gR0DumpForceBackfillSuccess{0u};
std::atomic<uint64_t> gR0DumpForceBackfillFailed{0u};
std::atomic<uint64_t> gR0DumpForceBackfillSkippedByGuard{0u};
std::atomic<uint64_t> gR0DumpForceBackfillInvokedUnchanged{0u};
std::atomic<uint64_t> gR0DumpForceBackfillInvokeExceptions{0u};
thread_local bool gR0DumpForceBackfillInProgress = false;
thread_local std::string gR0DumpForceBackfillBeforeHash;
thread_local std::string gR0DumpForceBackfillAfterHash;
thread_local bool gR0DumpForceBackfillChanged = false;

void WriteStatus(const char* phase);

std::string Sanitize(std::string value) {
  if (value.empty()) {
    return "unknown";
  }
  for (char& c : value) {
    const bool ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
    if (!ok) {
      c = '_';
    }
  }
  return value;
}

std::string ProcessName() {
  char buffer[256] = {};
  int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
  if (fd < 0) {
    return "unknown";
  }
  const ssize_t length = TEMP_FAILURE_RETRY(read(fd, buffer, sizeof(buffer) - 1u));
  close(fd);
  if (length <= 0) {
    return "unknown";
  }
  buffer[length] = '\0';
  return Sanitize(std::string(buffer));
}

bool Mkdirs(const std::string& path) {
  if (path.empty()) {
    return false;
  }
  std::string current;
  if (path[0] == '/') {
    current = "/";
  }
  size_t start = path[0] == '/' ? 1u : 0u;
  while (start <= path.size()) {
    const size_t slash = path.find('/', start);
    const size_t end = slash == std::string::npos ? path.size() : slash;
    if (end > start) {
      if (!current.empty() && current.back() != '/') {
        current.push_back('/');
      }
      current.append(path, start, end - start);
      if (mkdir(current.c_str(), 0770) != 0 && errno != EEXIST) {
        return false;
      }
    }
    if (slash == std::string::npos) {
      break;
    }
    start = slash + 1u;
  }
  return true;
}

bool PrepareOutputDir(const std::string& path) {
  return Mkdirs(path) && access(path.c_str(), W_OK) == 0;
}

std::string OutputDir() {
  std::string root;
  std::string package_name;
  std::string process_name;
  std::string run_id;
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    root = gR0DumpOutputRoot;
    package_name = gR0DumpPackageName;
    process_name = gR0DumpProcessName;
    run_id = gR0DumpRunId;
  }
  if (root.empty()) {
    root = "/sdcard/Download/R0DUMP";
  }
  const std::string package_component = Sanitize(package_name);
  const std::string process_component = Sanitize(
      process_name.empty() ? ProcessName() : process_name);
  const std::string run_component = Sanitize(run_id);
  const std::string configured = root + "/" + package_component + "/" +
      run_component + "/" + process_component;
  if (PrepareOutputDir(configured)) {
    return configured;
  }
  // A normal application cannot write Download directly on modern Android.
  // Prefer its own external/private files area before the legacy shell path.
  const std::string external_fallback = "/sdcard/Android/data/" + package_component +
      "/files/r0dump/" + run_component + "/" + process_component;
  if (PrepareOutputDir(external_fallback)) {
    return external_fallback;
  }
  const std::string data_fallback = "/data/user/0/" + package_component +
      "/files/r0dump/" + run_component + "/" + process_component;
  if (PrepareOutputDir(data_fallback)) {
    return data_fallback;
  }
  const std::string legacy_fallback = "/data/local/tmp/R0DUMP/" + package_component +
      "/" + run_component + "/" + process_component;
  Mkdirs(legacy_fallback);
  return legacy_fallback;
}

uint64_t Fnv1a(const void* data, size_t size) {
  const auto* bytes = reinterpret_cast<const uint8_t*>(data);
  uint64_t value = 1469598103934665603ull;
  for (size_t i = 0; i < size; ++i) {
    value ^= bytes[i];
    value *= 1099511628211ull;
  }
  return value;
}

std::string Hex64(uint64_t value) {
  return android::base::StringPrintf("%016llx", static_cast<unsigned long long>(value));
}

std::string JsonEscape(const std::string& value) {
  std::string out;
  out.reserve(value.size() + 8u);
  for (const char c : value) {
    switch (c) {
      case '\\': out += "\\\\"; break;
      case '"': out += "\\\""; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default:
        if (static_cast<unsigned char>(c) < 0x20u) {
          out += android::base::StringPrintf("\\u%04x", static_cast<unsigned char>(c));
        } else {
          out.push_back(c);
        }
    }
  }
  return out;
}

std::string Base64(const uint8_t* data, size_t size) {
  static constexpr char kAlphabet[] =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  std::string out;
  out.reserve(((size + 2u) / 3u) * 4u);
  for (size_t i = 0; i < size; i += 3u) {
    const uint32_t a = data[i];
    const uint32_t b = i + 1u < size ? data[i + 1u] : 0u;
    const uint32_t c = i + 2u < size ? data[i + 2u] : 0u;
    out.push_back(kAlphabet[(a >> 2u) & 0x3fu]);
    out.push_back(kAlphabet[((a << 4u) | (b >> 4u)) & 0x3fu]);
    out.push_back(i + 1u < size ? kAlphabet[((b << 2u) | (c >> 6u)) & 0x3fu] : '=');
    out.push_back(i + 2u < size ? kAlphabet[c & 0x3fu] : '=');
  }
  return out;
}

bool WriteAll(const std::string& path, const void* data, size_t size, bool append) {
  const int flags = O_CREAT | O_WRONLY | (append ? O_APPEND : O_TRUNC) | O_CLOEXEC;
  const int fd = open(path.c_str(), flags, 0660);
  if (fd < 0) {
    return false;
  }
  const auto* cursor = reinterpret_cast<const uint8_t*>(data);
  size_t remaining = size;
  while (remaining != 0u) {
    const ssize_t written = TEMP_FAILURE_RETRY(write(fd, cursor, remaining));
    if (written <= 0) {
      close(fd);
      return false;
    }
    cursor += written;
    remaining -= static_cast<size_t>(written);
  }
  if (!append) {
    fsync(fd);
  }
  close(fd);
  return true;
}

bool WriteAtomic(const std::string& path, const std::string& data) {
  const std::string temporary = path + ".tmp." + std::to_string(getpid());
  std::lock_guard<std::mutex> lock(gR0DumpIoMutex);
  if (!WriteAll(temporary, data.data(), data.size(), false)) {
    unlink(temporary.c_str());
    return false;
  }
  if (rename(temporary.c_str(), path.c_str()) != 0) {
    unlink(temporary.c_str());
    return false;
  }
  return true;
}

// ART may expose a DEX container or a sparse mapped range.  Check mappings
// before touching memory so a malformed header cannot turn a dump request into
// a process crash.
bool IsReadableMemoryRange(const void* data, size_t size) {
  if (data == nullptr || size == 0u) {
    return false;
  }
  const uintptr_t start = reinterpret_cast<uintptr_t>(data);
  if (size > std::numeric_limits<uintptr_t>::max() - start) {
    return false;
  }
  const uintptr_t end = start + size;
  FILE* maps = fopen("/proc/self/maps", "re");
  if (maps == nullptr) {
    return false;
  }
  uintptr_t cursor = start;
  char line[512];
  while (fgets(line, sizeof(line), maps) != nullptr) {
    unsigned long long map_start_raw = 0;
    unsigned long long map_end_raw = 0;
    char perms[5] = {};
    if (sscanf(line, "%llx-%llx %4s", &map_start_raw, &map_end_raw, perms) != 3) {
      continue;
    }
    const uintptr_t map_start = static_cast<uintptr_t>(map_start_raw);
    const uintptr_t map_end = static_cast<uintptr_t>(map_end_raw);
    if (map_end <= cursor) {
      continue;
    }
    if (map_start > cursor || perms[0] != 'r') {
      fclose(maps);
      return false;
    }
    if (map_end >= end) {
      fclose(maps);
      return true;
    }
    cursor = map_end;
  }
  fclose(maps);
  return false;
}

bool WriteSparseMappedRange(const std::string& path, const uint8_t* begin, size_t size,
                            bool require_dex_header = true) {
  if (begin == nullptr || size == 0u || size > kMaxDexExportSize) {
    return false;
  }
  const uintptr_t start = reinterpret_cast<uintptr_t>(begin);
  if (size > std::numeric_limits<uintptr_t>::max() - start) {
    return false;
  }
  const int fd = open(path.c_str(), O_CREAT | O_WRONLY | O_TRUNC | O_CLOEXEC, 0660);
  if (fd < 0) {
    return false;
  }
  if (ftruncate(fd, static_cast<off_t>(size)) != 0) {
    close(fd);
    return false;
  }
  const uintptr_t end = start + size;
  FILE* maps = fopen("/proc/self/maps", "re");
  if (maps == nullptr) {
    close(fd);
    return false;
  }
  bool copied = false;
  char line[512];
  while (fgets(line, sizeof(line), maps) != nullptr) {
    unsigned long long map_start_raw = 0;
    unsigned long long map_end_raw = 0;
    char perms[5] = {};
    if (sscanf(line, "%llx-%llx %4s", &map_start_raw, &map_end_raw, perms) != 3 ||
        perms[0] != 'r') {
      continue;
    }
    const uintptr_t map_start = static_cast<uintptr_t>(map_start_raw);
    const uintptr_t map_end = static_cast<uintptr_t>(map_end_raw);
    if (map_end <= start || map_start >= end || map_end <= map_start) {
      continue;
    }
    const uintptr_t copy_start = std::max(map_start, start);
    const uintptr_t copy_end = std::min(map_end, end);
    if (copy_end <= copy_start) {
      continue;
    }
    const uint8_t* cursor = reinterpret_cast<const uint8_t*>(copy_start);
    size_t remaining = static_cast<size_t>(copy_end - copy_start);
    off_t file_offset = static_cast<off_t>(copy_start - start);
    while (remaining != 0u) {
      const size_t chunk = std::min<size_t>(remaining, 1024u * 1024u);
      const ssize_t written = TEMP_FAILURE_RETRY(pwrite(fd, cursor, chunk, file_offset));
      if (written <= 0) {
        fclose(maps);
        close(fd);
        return false;
      }
      cursor += written;
      file_offset += written;
      remaining -= static_cast<size_t>(written);
    }
    copied = true;
  }
  fclose(maps);
  // ftruncate created zero-filled holes for unreadable gaps.  A header is the
  // minimum proof that the output is useful; callers perform format checks.
  const bool header_ok = copied && (!require_dex_header || IsReadableMemoryRange(
      begin, std::min<size_t>(size, sizeof(DexFile::Header))));
  if (header_ok) {
    fsync(fd);
  }
  close(fd);
  return header_ok;
}

const char* StrategyName(uint32_t strategy) {
  switch (strategy) {
    case 1u << 0: return "CLASS_WALK";
    case 1u << 1: return "APP_CREATE";
    case 1u << 2: return "ACTIVITY_CREATE";
    case 1u << 3: return "REAL_INVOKE";
    case 1u << 4: return "LOAD_METHOD";
    case 1u << 5: return "DEX_LOAD";
    case 1u << 6: return "REGISTER_DEX";
    case 1u << 7: return "IN_MEMORY_DEX";
    case 1u << 8: return "DEFINE_CLASS";
    case 1u << 9: return "LOAD_CLASS";
    case 1u << 10: return "RESOLVE_METHOD";
    case 1u << 11: return "FORCE_BACKFILL";
    case 1u << 12: return "FORCE_BACKFILL_BEFORE";
    case 1u << 13: return "FORCE_BACKFILL_AFTER";
    case 1u << 14: return "OPEN_COMMON";
    case 1u << 15: return "OPEN_DEX_FILES_FROM_OAT";
    case 1u << 16: return "VDEX_OPEN_ALL_DEX_FILES";
    case 1u << 17: return "OAT_DEX_FILE_OPEN";
    case 1u << 18: return "VERIFY_CLASS";
    case 1u << 19: return "CLASS_INIT_BEFORE";
    case 1u << 20: return "CLASS_INIT_AFTER";
    case 1u << 21: return "INTERPRETER_EXECUTE";
    case 1u << 22: return "JIT_METHOD_ENTERED";
    case 1u << 23: return "JIT_COMPILE";
    case 1u << 24: return "REFLECT_METHOD_INVOKE";
    case 1u << 25: return "INSTRUMENT_METHOD_ENTER";
    case 1u << 26: return "INSTRUMENT_METHOD_EXIT";
    case 1u << 27: return "JAVA_CLASS_LOADER_ROUTE";
    case 1u << 28: return "JAVA_DEXFILE_ROUTE";
    case 1u << 29: return "DEFINE_CLASS_NATIVE";
    case 1u << 30: return "OAT_REGISTER";
    case 1u << 31: return "IMAGE_SPACE_DEX";
    default: return "UNKNOWN";
  }
}

bool ReserveMethodRecord() {
  bool stopped = false;
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    if (!gR0DumpEnabled.load(std::memory_order_relaxed)) {
      return false;
    }
    const uint64_t max_records = gR0DumpMaxRecords.load(std::memory_order_relaxed);
    const uint64_t max_seconds = gR0DumpMaxSeconds.load(std::memory_order_relaxed);
    const time_t started_at = gR0DumpStartedAt.load(std::memory_order_relaxed);
    const time_t now = time(nullptr);
    const bool records_limited = max_records != 0u
        && gR0DumpMethodRecords.load(std::memory_order_relaxed) >= max_records;
    const bool time_limited = max_seconds != 0u && started_at != 0 && now >= started_at
        && static_cast<uint64_t>(now - started_at) >= max_seconds;
    if (records_limited || time_limited) {
      gR0DumpEnabled.store(false, std::memory_order_relaxed);
      gR0DumpTerminal.store(true, std::memory_order_relaxed);
      gR0DumpStopReason = records_limited ? "max_records" : "max_seconds";
      stopped = true;
    } else {
      // Reserve while holding the state lock.  Method callbacks can arrive
      // concurrently from instrumentation/JIT threads.
      gR0DumpMethodRecords.fetch_add(1u, std::memory_order_relaxed);
    }
  }
  if (stopped) {
    WriteStatus("stopped_by_limit");
  }
  return !stopped;
}

void ReleaseMethodRecord() {
  gR0DumpMethodRecords.fetch_sub(1u, std::memory_order_relaxed);
}

struct DexExportInfo {
  const uint8_t* begin = nullptr;
  size_t size = 0u;
  size_t header_offset = 0u;
  size_t entry_size = 0u;
  uint32_t checksum = 0u;
  bool container = false;
  std::string location_hash;
};

DexExportInfo GetDexExportInfo(const DexFile* dex_file) {
  DexExportInfo info;
  if (dex_file == nullptr || dex_file->Begin() == nullptr) {
    return info;
  }
  info.begin = dex_file->Begin();
  info.entry_size = dex_file->Size();
  info.checksum = dex_file->GetHeader().checksum_;
  const std::string location = dex_file->GetLocation();
  info.location_hash = Hex64(Fnv1a(location.data(), location.size()));
  // DEX 041 stores several entries in one container.  Begin()/Size() only
  // covers the current entry, while the referenced map/data sections may live
  // in the shared container.  Export the complete range in that case.
  if (dex_file->HasDexContainer()) {
    const auto range = dex_file->GetDexContainerRange();
    if (range.begin() != nullptr && range.size() <= kMaxDexExportSize) {
      info.begin = range.begin();
      info.size = range.size();
      info.header_offset = static_cast<size_t>(dex_file->Begin() - range.begin());
      info.container = true;
      if (range.size() >= sizeof(DexFile::Header) &&
          IsReadableMemoryRange(range.begin(), sizeof(DexFile::Header))) {
        DexFile::Header first_header = {};
        memcpy(&first_header, range.begin(), sizeof(first_header));
        info.checksum = first_header.checksum_;
      }
      // The address is stable for the life of this process/run and is shared by
      // every entry in the same container.  Hashing it avoids reading tens of
      // kilobytes on every hot-path method callback.
      const uintptr_t container_address = reinterpret_cast<uintptr_t>(range.begin());
      uint64_t identity = Fnv1a(&container_address, sizeof(container_address));
      identity ^= static_cast<uint64_t>(range.size()) * 1099511628211ull;
      identity ^= info.checksum;
      info.location_hash = Hex64(identity);
      return info;
    }
  }
  size_t size = dex_file->Size();
  const uintptr_t begin = reinterpret_cast<uintptr_t>(dex_file->Begin());
  const uintptr_t data_begin = reinterpret_cast<uintptr_t>(dex_file->DataBegin());
  if (data_begin >= begin && dex_file->DataSize() != 0u &&
      dex_file->DataSize() <= std::numeric_limits<uintptr_t>::max() - data_begin) {
    const size_t inferred = static_cast<size_t>(data_begin - begin) + dex_file->DataSize();
    if (inferred > size && inferred <= kMaxDexExportSize) {
      size = inferred;
    }
  }
  if (size == 0u) {
    size = dex_file->GetHeader().file_size_;
  }
  info.size = size <= kMaxDexExportSize ? size : 0u;
  return info;
}

std::string DexKey(const DexExportInfo& info) {
  return android::base::StringPrintf(
      "%s:%zu:%08x", info.location_hash.c_str(), info.size, info.checksum);
}

bool IsForceBackfillStrategy(uint32_t strategy) {
  return strategy == kStrategyForceBackfill
      || strategy == kStrategyForceBackfillBefore
      || strategy == kStrategyForceBackfillAfter;
}

std::string CodeItemHash(ArtMethod* method, const DexFile* fallback)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (method == nullptr || method->IsRuntimeMethod()) {
    return "";
  }
  const DexFile* dex_file = method->GetDexFile();
  if (dex_file == nullptr) {
    dex_file = fallback;
  }
  if (dex_file == nullptr || !IsReadableMemoryRange(dex_file->Begin(), sizeof(DexFile::Header))
      || !StandardDexFile::IsMagicValid(dex_file->Begin())) {
    return "";
  }
  CodeItemDataAccessor accessor(method->DexInstructionData());
  if (!accessor.HasCodeItem() || accessor.Insns() == nullptr
      || accessor.CodeItemDataEnd() == nullptr) {
    return "";
  }
  const uintptr_t item = reinterpret_cast<uintptr_t>(accessor.Insns()) - 16u;
  const uintptr_t end = reinterpret_cast<uintptr_t>(accessor.CodeItemDataEnd());
  const DexExportInfo info = GetDexExportInfo(dex_file);
  const uintptr_t begin = reinterpret_cast<uintptr_t>(info.begin);
  if (info.size == 0u || item < begin || end <= item || end - item > kMaxCodeItemSize
      || end - begin > info.size
      || !IsReadableMemoryRange(reinterpret_cast<const void*>(item),
                                 static_cast<size_t>(end - item))) {
    return "";
  }
  return Hex64(Fnv1a(reinterpret_cast<const void*>(item), static_cast<size_t>(end - item)));
}

bool ShouldSkipForceBackfill(ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (method == nullptr || method->IsRuntimeMethod() || method->IsAbstract()
      || method->IsProxyMethod() || !method->IsStatic() || method->IsNative()
      || method->IsConstructor()) {
    return true;
  }
  // ArtMethod::Invoke receives no receiver or argument array here.  Restrict
  // this path to methods whose shorty contains only the return type.
  const char* shorty = method->GetShorty();
  if (shorty == nullptr || std::strlen(shorty) != 1u) {
    return true;
  }
  CodeItemDataAccessor accessor(method->DexInstructionData());
  return !accessor.HasCodeItem() || accessor.Insns() == nullptr;
}

class ScopedForceBackfillGuard final {
 public:
  ScopedForceBackfillGuard() {
    gR0DumpForceBackfillInProgress = true;
  }

  ~ScopedForceBackfillGuard() {
    gR0DumpForceBackfillInProgress = false;
    gR0DumpForceBackfillBeforeHash.clear();
    gR0DumpForceBackfillAfterHash.clear();
    gR0DumpForceBackfillChanged = false;
  }
};

bool DumpDexDataMirror(const DexFile* dex_file, const char* suffix, uint32_t strategy) {
  if (!gR0DumpRawMirror.load(std::memory_order_relaxed) || dex_file == nullptr) {
    return true;
  }
  const uint8_t* data_begin = dex_file->DataBegin();
  const size_t data_size = dex_file->DataSize();
  if (data_begin == nullptr || data_size == 0u || data_size > kMaxDexExportSize) {
    return false;
  }
  const uintptr_t address = reinterpret_cast<uintptr_t>(data_begin);
  uint64_t identity = Fnv1a(&address, sizeof(address));
  identity ^= static_cast<uint64_t>(data_size) * 1099511628211ull;
  const std::string key = Hex64(identity) + ":" + std::to_string(data_size);
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    if (!gR0DumpDexDataKeys.insert(key).second) {
      return true;
    }
  }
  const std::string path = OutputDir() + "/dexdata_" + key.substr(0, 16) + "_" +
      std::to_string(data_size) + "_0x00000000_" +
      Sanitize(suffix != nullptr ? suffix : StrategyName(strategy)) + ".bin";
  if (!WriteSparseMappedRange(path, data_begin, data_size, false)) {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    gR0DumpDexDataKeys.erase(key);
    return false;
  }
  gR0DumpDexDataFiles.fetch_add(1u, std::memory_order_relaxed);
  return true;
}

bool DumpDex(const DexFile* dex_file, const char* suffix, uint32_t strategy) {
  if (dex_file == nullptr || !isR0DumpStrategyEnabled(strategy)) {
    return false;
  }
  const DexExportInfo info = GetDexExportInfo(dex_file);
  const size_t size = info.size;
  if (size == 0u || info.begin == nullptr) {
    return false;
  }
  const uint32_t checksum = info.checksum;
  const std::string& location_hash = info.location_hash;
  const std::string key = DexKey(info);
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    if (!gR0DumpDexKeys.insert(key).second) {
      return true;
    }
  }
  const std::string out_dir = OutputDir();
  const std::string strategy_name = Sanitize(suffix != nullptr ? suffix : StrategyName(strategy));
  const bool is_container = info.container;
  const std::string path = out_dir + "/" + (is_container ? "dexcontainer_" : "dex_") + location_hash + "_" +
      std::to_string(size) + "_0x" + android::base::StringPrintf("%08x", checksum) +
      "_" + strategy_name + ".dex";
  const bool written =
      IsReadableMemoryRange(info.begin, std::min<size_t>(size, sizeof(DexFile::Header))) &&
      WriteSparseMappedRange(path, info.begin, size);
  if (!written) {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    gR0DumpDexKeys.erase(key);
    return false;
  }
  gR0DumpDexFiles.fetch_add(1u, std::memory_order_relaxed);
  if (is_container) {
    gR0DumpContainerFiles.fetch_add(1u, std::memory_order_relaxed);
  }
  // The mirror is intentionally synchronous: the ART pointers are only
  // guaranteed to remain valid during this callback.  The async setting is
  // reported as a synchronous fallback instead of silently dropping data.
  DumpDexDataMirror(dex_file, suffix, strategy);
  return true;
}

void DumpMethod(ArtMethod* method, const DexFile* fallback, uint32_t strategy)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (method == nullptr || !isR0DumpStrategyEnabled(strategy) || method->IsRuntimeMethod()) {
    return;
  }
  const DexFile* dex_file = method->GetDexFile();
  if (dex_file == nullptr) {
    dex_file = fallback;
  }
  const DexExportInfo info = GetDexExportInfo(dex_file);
  const size_t dex_size = info.size;
  if (dex_file == nullptr || dex_size == 0u || info.begin == nullptr) {
    gR0DumpInvalid.fetch_add(1u, std::memory_order_relaxed);
    return;
  }
  if (!IsReadableMemoryRange(dex_file->Begin(), sizeof(DexFile::Header))
      || !StandardDexFile::IsMagicValid(dex_file->Begin())) {
    gR0DumpNonstandardDexSkipped.fetch_add(1u, std::memory_order_relaxed);
    return;
  }
  CodeItemDataAccessor accessor(method->DexInstructionData());
  if (!accessor.HasCodeItem() || accessor.Insns() == nullptr || accessor.CodeItemDataEnd() == nullptr) {
    gR0DumpInvalid.fetch_add(1u, std::memory_order_relaxed);
    return;
  }
  const uint8_t* item = reinterpret_cast<const uint8_t*>(accessor.Insns()) - 16u;
  const uint8_t* end = reinterpret_cast<const uint8_t*>(accessor.CodeItemDataEnd());
  const uint8_t* begin = info.begin;
  const uintptr_t begin_address = reinterpret_cast<uintptr_t>(begin);
  const uintptr_t item_address = reinterpret_cast<uintptr_t>(item);
  const uintptr_t end_address = reinterpret_cast<uintptr_t>(end);
  if (item_address < begin_address || end_address <= item_address
      || item_address - begin_address >= dex_size
      || end_address - begin_address > dex_size
      || end_address - item_address > kMaxCodeItemSize
      || !IsReadableMemoryRange(item, static_cast<size_t>(end_address - item_address))) {
    gR0DumpInvalid.fetch_add(1u, std::memory_order_relaxed);
    return;
  }
  const size_t item_offset = static_cast<size_t>(item_address - begin_address);
  const size_t item_len = static_cast<size_t>(end_address - item_address);
  const std::string dex_key = DexKey(info);
  const uint64_t item_hash = Fnv1a(item, item_len);
  const std::string method_key = dex_key + ":" + std::to_string(method->GetDexMethodIndex()) +
      ":" + std::to_string(item_offset) + ":" + std::to_string(item_len) +
      ":" + Hex64(item_hash);
  bool duplicate = false;
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    duplicate = !gR0DumpMethodKeys.insert(method_key).second;
  }
  if (duplicate) {
    gR0DumpDuplicates.fetch_add(1u, std::memory_order_relaxed);
    return;
  }
  if (!DumpDex(dex_file, StrategyName(strategy), strategy)) {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    gR0DumpMethodKeys.erase(method_key);
    return;
  }
  if (!ReserveMethodRecord()) {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    gR0DumpMethodKeys.erase(method_key);
    return;
  }
  std::string method_name;
  if (method->GetDexFile() != nullptr) {
    method_name = method->PrettyMethod(false);
  }
  const std::string location = dex_file->GetLocation();
  const std::string entry_location_hash = Hex64(Fnv1a(location.data(), location.size()));
  const uint32_t checksum = info.checksum;
  std::string package_name;
  std::string process_name;
  std::string run_id;
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    package_name = gR0DumpPackageName;
    process_name = gR0DumpProcessName;
    run_id = gR0DumpRunId;
  }
  const std::string force_extra = IsForceBackfillStrategy(strategy)
      ? android::base::StringPrintf(
          ",\"force_backfill_before_hash\":\"%s\","
          "\"force_backfill_after_hash\":\"%s\","
          "\"force_backfill_changed\":%s",
          JsonEscape(gR0DumpForceBackfillBeforeHash).c_str(),
          JsonEscape(gR0DumpForceBackfillAfterHash).c_str(),
          gR0DumpForceBackfillChanged ? "true" : "false")
      : "";
  const std::string record = android::base::StringPrintf(
      "{\"version\":2,\"run_id\":\"%s\",\"package\":\"%s\","
      "\"process\":\"%s\",\"strategy\":\"%s\",\"pid\":%d,\"dex_size\":%zu,"
      "\"dex_checksum\":\"0x%08x\",\"dex_location_hash\":\"%s\","
      "\"dex_container\":%s,\"dex_header_offset\":%zu,\"dex_entry_size\":%zu,"
      "\"dex_entry_location_hash\":\"%s\","
      "\"method_idx\":%u,\"code_item_offset\":%zu,\"code_item_len\":%zu,"
      "\"method_name\":\"%s\",\"code_item_hash\":\"%s\","
      "\"code_item_b64\":\"%s\"%s}\n",
      JsonEscape(run_id).c_str(), JsonEscape(package_name).c_str(),
      JsonEscape(process_name).c_str(), StrategyName(strategy), getpid(), dex_size, checksum,
      info.location_hash.c_str(), info.container ? "true" : "false", info.header_offset,
      info.entry_size, entry_location_hash.c_str(), method->GetDexMethodIndex(),
      item_offset, item_len, JsonEscape(method_name).c_str(), Hex64(item_hash).c_str(),
      Base64(item, item_len).c_str(), force_extra.c_str());
  const std::string path = OutputDir() + "/methods_" + std::to_string(getpid()) + ".jsonl";
  bool written = false;
  {
    std::lock_guard<std::mutex> lock(gR0DumpIoMutex);
    written = WriteAll(path, record.data(), record.size(), true);
  }
  if (written) {
    const uint64_t count = gR0DumpMethodRecords.load(std::memory_order_relaxed);
    if (!gR0DumpEnabled.load(std::memory_order_relaxed) || (count % 256u) == 0u) {
      WriteStatus(gR0DumpEnabled.load(std::memory_order_relaxed)
          ? "dumping" : "stopped_by_limit");
    }
  } else {
    ReleaseMethodRecord();
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    gR0DumpMethodKeys.erase(method_key);
  }
}

void WriteStatus(const char* phase) {
  const std::string output_dir = OutputDir();
  const std::string path = output_dir + "/status.json";
  std::string phase_name = phase != nullptr && phase[0] != '\0' ? phase : "unknown";
  std::string package_name;
  std::string process_name;
  std::string run_id;
  std::string stop_reason;
  std::string class_loaders_json;
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    package_name = gR0DumpPackageName;
    process_name = gR0DumpProcessName;
    run_id = gR0DumpRunId;
    stop_reason = gR0DumpStopReason;
    class_loaders_json = gR0DumpClassLoadersJson;
    const bool explicit_terminal = phase_name == "complete"
        || phase_name == "stopped" || phase_name == "stopped_by_limit";
    if (gR0DumpTerminal.load(std::memory_order_relaxed) && !explicit_terminal) {
      phase_name = gR0DumpPhase.empty() ? "complete" : gR0DumpPhase;
    } else {
      gR0DumpPhase = phase_name;
    }
  }
  std::string strategies = "[";
  const uint32_t mask = gR0DumpStrategyMask.load();
  bool first = true;
  for (uint32_t bit = 1u; bit != 0u; bit <<= 1u) {
    if ((mask & bit) == 0u) continue;
    if (!first) strategies += ",";
    first = false;
    strategies += "\"" + std::string(StrategyName(bit)) + "\"";
  }
  strategies += "]";
  const std::string status = android::base::StringPrintf(
      "{\"schema_version\":2,\"run_id\":\"%s\",\"package\":\"%s\","
      "\"process\":\"%s\",\"phase\":\"%s\",\"stop_reason\":\"%s\","
      "\"pid\":%d,\"runtime_enabled\":%s,\"started_at\":%lld,\"updated_at\":%lld,"
      "\"output_dir\":\"%s\","
      "\"strategies\":%s,\"dex_files_written\":%llu,\"dexdata_files_written\":%llu,"
      "\"method_records_written\":%llu,"
      "\"fixed_dex_files_written\":%llu,\"container_files_written\":%llu,"
      "\"reconstruction_failures\":%llu,"
      "\"duplicate_methods_skipped\":%llu,\"nonstandard_dex_methods_skipped\":%llu,"
      "\"invalid_methods_skipped\":%llu,"
      "\"async_export_requested\":%s,\"async_export_mode\":\"synchronous_fallback\","
      "\"force_backfill_attempts\":%llu,\"force_backfill_success\":%llu,"
      "\"force_backfill_failed\":%llu,\"force_backfill_skipped_by_guard\":%llu,"
      "\"force_backfill_invoked_unchanged\":%llu,"
      "\"force_backfill_invoke_exceptions\":%llu,"
      "\"classloader_candidates\":%llu,\"classloaders_walked\":%llu,"
      "\"classloader_dex_elements\":%llu,\"classloader_unique_cookies\":%llu,"
      "\"loaded_class_table_classes\":%llu,\"manifest_component_classes\":%llu,"
      "\"manifest_seed_dumped\":%llu,\"classloaders\":%s}\n",
      JsonEscape(run_id).c_str(), JsonEscape(package_name).c_str(),
      JsonEscape(process_name).c_str(), JsonEscape(phase_name).c_str(),
      JsonEscape(stop_reason).c_str(), getpid(), gR0DumpEnabled.load() ? "true" : "false",
      static_cast<long long>(gR0DumpStartedAt.load()), static_cast<long long>(time(nullptr)),
      JsonEscape(output_dir).c_str(), strategies.c_str(),
      static_cast<unsigned long long>(gR0DumpDexFiles.load()),
      static_cast<unsigned long long>(gR0DumpDexDataFiles.load()),
      static_cast<unsigned long long>(gR0DumpMethodRecords.load()),
      static_cast<unsigned long long>(gR0DumpFixedDexFiles.load()),
      static_cast<unsigned long long>(gR0DumpContainerFiles.load()),
      static_cast<unsigned long long>(gR0DumpReconstructionFailures.load()),
      static_cast<unsigned long long>(gR0DumpDuplicates.load()),
      static_cast<unsigned long long>(gR0DumpNonstandardDexSkipped.load()),
      static_cast<unsigned long long>(gR0DumpInvalid.load()),
      gR0DumpAsync.load(std::memory_order_relaxed) ? "true" : "false",
      static_cast<unsigned long long>(gR0DumpForceBackfillAttempts.load()),
      static_cast<unsigned long long>(gR0DumpForceBackfillSuccess.load()),
      static_cast<unsigned long long>(gR0DumpForceBackfillFailed.load()),
      static_cast<unsigned long long>(gR0DumpForceBackfillSkippedByGuard.load()),
      static_cast<unsigned long long>(gR0DumpForceBackfillInvokedUnchanged.load()),
      static_cast<unsigned long long>(gR0DumpForceBackfillInvokeExceptions.load()),
      static_cast<unsigned long long>(gR0DumpClassLoaderCandidates.load()),
      static_cast<unsigned long long>(gR0DumpClassLoadersWalked.load()),
      static_cast<unsigned long long>(gR0DumpClassLoaderDexElements.load()),
      static_cast<unsigned long long>(gR0DumpClassLoaderUniqueCookies.load()),
      static_cast<unsigned long long>(gR0DumpLoadedClassTableClasses.load()),
      static_cast<unsigned long long>(gR0DumpManifestComponentClasses.load()),
      static_cast<unsigned long long>(gR0DumpManifestSeedDumped.load()),
      class_loaders_json.empty() ? "[]" : class_loaders_json.c_str());
  WriteAtomic(path, status);
}

}  // namespace

extern "C" bool isR0DumpStrategyEnabled(uint32_t strategy) {
  return gR0DumpEnabled.load() && (gR0DumpStrategyMask.load() & strategy) != 0u;
}

extern "C" void configureR0DumpRuntime(const char* output_root,
                                       const char* package_name,
                                       const char* process_name,
                                       const char* run_id,
                                       uint32_t strategy_mask,
                                       uint64_t max_records,
                                       uint64_t max_seconds,
                                       bool stop_after_complete) {
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    if (output_root != nullptr && output_root[0] != '\0') {
      gR0DumpOutputRoot = output_root;
    }
    gR0DumpPackageName = package_name != nullptr ? package_name : "unknown";
    gR0DumpProcessName = process_name != nullptr ? process_name : "unknown";
    gR0DumpRunId = run_id != nullptr && run_id[0] != '\0' ? run_id : "legacy";
    gR0DumpStopReason.clear();
    gR0DumpPhase = "configured";
    gR0DumpClassLoadersJson = "[]";
    gR0DumpDexKeys.clear();
    gR0DumpDexDataKeys.clear();
    gR0DumpMethodKeys.clear();
  }
  if ((strategy_mask & kStrategyForceBackfill) != 0u) {
    strategy_mask |= kStrategyForceBackfillBefore | kStrategyForceBackfillAfter;
  }
  gR0DumpStrategyMask.store(strategy_mask);
  gR0DumpMaxRecords.store(max_records == 0u ? 50000u : max_records);
  gR0DumpMaxSeconds.store(max_seconds == 0u ? 300u : max_seconds);
  gR0DumpStopAfterComplete.store(stop_after_complete);
  gR0DumpStartedAt.store(time(nullptr));
  gR0DumpMethodRecords.store(0u);
  gR0DumpDexFiles.store(0u);
  gR0DumpDexDataFiles.store(0u);
  gR0DumpFixedDexFiles.store(0u);
  gR0DumpContainerFiles.store(0u);
  gR0DumpReconstructionFailures.store(0u);
  gR0DumpForceBackfillAttempts.store(0u);
  gR0DumpForceBackfillSuccess.store(0u);
  gR0DumpForceBackfillFailed.store(0u);
  gR0DumpForceBackfillSkippedByGuard.store(0u);
  gR0DumpForceBackfillInvokedUnchanged.store(0u);
  gR0DumpForceBackfillInvokeExceptions.store(0u);
  gR0DumpDuplicates.store(0u);
  gR0DumpNonstandardDexSkipped.store(0u);
  gR0DumpInvalid.store(0u);
  gR0DumpClassLoaderCandidates.store(0u);
  gR0DumpClassLoadersWalked.store(0u);
  gR0DumpClassLoaderDexElements.store(0u);
  gR0DumpClassLoaderUniqueCookies.store(0u);
  gR0DumpLoadedClassTableClasses.store(0u);
  gR0DumpManifestComponentClasses.store(0u);
  gR0DumpManifestSeedDumped.store(0u);
  gR0DumpTerminal.store(false, std::memory_order_relaxed);
  gR0DumpEnabled.store(true);
  WriteStatus("configured");
}

extern "C" void stopR0DumpRuntime(const char* reason) {
  gR0DumpEnabled.store(false);
  gR0DumpTerminal.store(true, std::memory_order_relaxed);
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    gR0DumpStopReason = reason != nullptr ? reason : "stopped";
  }
  WriteStatus("stopped");
}

extern "C" void configureR0DumpOutputRoot(const char* output_root) {
  std::lock_guard<std::mutex> lock(gR0DumpMutex);
  if (output_root != nullptr && output_root[0] != '\0') {
    gR0DumpOutputRoot = output_root;
  }
}

extern "C" void configureR0DumpForceBackfill(uint64_t max_methods,
                                             [[maybe_unused]] bool only_static,
                                             [[maybe_unused]] bool skip_native,
                                             [[maybe_unused]] bool skip_constructor) {
  std::lock_guard<std::mutex> lock(gR0DumpMutex);
  gR0DumpForceMaxMethods = max_methods;
}

extern "C" void configureR0DumpRawDexDataMirror(bool enabled) {
  gR0DumpRawMirror.store(enabled);
}

extern "C" void configureR0DumpAsyncExport(bool enabled) {
  gR0DumpAsync.store(enabled);
}

extern "C" void noteR0DumpClassLoaderScan(uint64_t candidates,
                                          uint64_t walked,
                                          uint64_t dex_elements,
                                          uint64_t unique_cookies,
                                          uint64_t loaded_class_table_classes,
                                          uint64_t manifest_component_classes,
                                          uint64_t manifest_seed_dumped,
                                          const char* class_loaders_json) {
  gR0DumpClassLoaderCandidates.store(candidates);
  gR0DumpClassLoadersWalked.store(walked);
  gR0DumpClassLoaderDexElements.store(dex_elements);
  gR0DumpClassLoaderUniqueCookies.store(unique_cookies);
  gR0DumpLoadedClassTableClasses.store(loaded_class_table_classes);
  gR0DumpManifestComponentClasses.store(manifest_component_classes);
  gR0DumpManifestSeedDumped.store(manifest_seed_dumped);
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    gR0DumpClassLoadersJson = class_loaders_json != nullptr ? class_loaders_json : "[]";
  }
  WriteStatus(gR0DumpEnabled.load() ? "class_walk" : "stopped");
}

extern "C" void noteR0DumpPhase(const char* phase) {
  if (!gR0DumpEnabled.load(std::memory_order_relaxed)) {
    return;
  }
  WriteStatus(phase != nullptr && phase[0] != '\0' ? phase : "dumping");
}

extern "C" void markR0DumpComplete() {
  const bool stop_after_complete = gR0DumpStopAfterComplete.load();
  if (stop_after_complete) {
    gR0DumpEnabled.store(false);
  }
  gR0DumpTerminal.store(stop_after_complete, std::memory_order_relaxed);
  WriteStatus("complete");
}

extern "C" void dumpR0DumpDexFileByStrategy(const DexFile* dex_file,
                                             const char* suffix,
                                             uint32_t strategy) {
  DumpDex(dex_file, suffix, strategy);
}

extern "C" void dumpR0DumpDexFile(const DexFile* dex_file, const char* suffix) {
  DumpDex(dex_file, suffix, kStrategyDexLoad);
}

extern "C" void dumpR0DumpRawBufferByStrategy(const uint8_t* data,
                                               size_t size,
                                               const char* suffix,
                                               uint32_t strategy) {
  if (!isR0DumpStrategyEnabled(strategy) || data == nullptr || size == 0u) return;
  const std::string path = OutputDir() + "/raw_" +
      Sanitize(suffix != nullptr ? suffix : "buffer") + "_" + std::to_string(size) + ".bin";
  WriteAll(path, data, size, false);
}

extern "C" void dumpR0DumpMethodByStrategy(ArtMethod* method, uint32_t strategy)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DumpMethod(method, nullptr, strategy);
}

extern "C" void dumpR0DumpMethodWithDexFileByStrategy(
    ArtMethod* method, const DexFile* fallback_dex_file, uint32_t strategy)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DumpMethod(method, fallback_dex_file, strategy);
}

extern "C" void forceR0DumpBackfillMethodByStrategy(ArtMethod* method, uint32_t strategy)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DumpMethod(method, nullptr, strategy);
}

extern "C" void forceR0DumpBackfillMethodWithDexFileByStrategy(
    ArtMethod* method, const DexFile* fallback_dex_file, uint32_t strategy)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (!IsForceBackfillStrategy(strategy)
      || !isR0DumpStrategyEnabled(strategy)
      || gR0DumpForceBackfillInProgress) {
    return;
  }
  if (ShouldSkipForceBackfill(method)) {
    gR0DumpForceBackfillSkippedByGuard.fetch_add(1u, std::memory_order_relaxed);
    return;
  }
  {
    std::lock_guard<std::mutex> lock(gR0DumpMutex);
    if (gR0DumpForceMaxMethods != 0u
        && gR0DumpForceBackfillAttempts.load(std::memory_order_relaxed)
            >= gR0DumpForceMaxMethods) {
      return;
    }
    gR0DumpForceBackfillAttempts.fetch_add(1u, std::memory_order_relaxed);
  }
  ScopedForceBackfillGuard force_guard;
  const std::string before_hash = CodeItemHash(method, fallback_dex_file);
  gR0DumpForceBackfillBeforeHash = before_hash;
  gR0DumpForceBackfillAfterHash.clear();
  gR0DumpForceBackfillChanged = false;
  DumpMethod(method, fallback_dex_file, kStrategyForceBackfillBefore);

  Thread* self = Thread::Current();
  bool invoke_exception = false;
  if (self != nullptr) {
    JValue result;
    uint32_t args[1] = {0u};
    const char* shorty = method->GetShorty();
    method->Invoke(self, args, 0u, &result, shorty != nullptr ? shorty : "V");
    if (self->IsExceptionPending()) {
      invoke_exception = true;
      self->ClearException();
    }
  } else {
    invoke_exception = true;
  }

  const std::string after_hash = CodeItemHash(method, fallback_dex_file);
  const bool changed = !before_hash.empty() && !after_hash.empty()
      && before_hash != after_hash;
  gR0DumpForceBackfillAfterHash = after_hash;
  gR0DumpForceBackfillChanged = changed;
  DumpMethod(method, fallback_dex_file, kStrategyForceBackfillAfter);
  if (invoke_exception) {
    gR0DumpForceBackfillInvokeExceptions.fetch_add(1u, std::memory_order_relaxed);
  }
  if (changed) {
    gR0DumpForceBackfillSuccess.fetch_add(1u, std::memory_order_relaxed);
  } else if (invoke_exception) {
    gR0DumpForceBackfillFailed.fetch_add(1u, std::memory_order_relaxed);
  } else {
    gR0DumpForceBackfillInvokedUnchanged.fetch_add(1u, std::memory_order_relaxed);
  }
  WriteStatus(changed ? "force_backfill_changed" : "force_backfill_unchanged");
}

extern "C" void dumpArtMethod(ArtMethod* method, uint32_t strategy)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DumpMethod(method, nullptr, strategy);
}

extern "C" void dumpDexFileByExecute(ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DumpMethod(method, nullptr, 1u << 3);
}

extern "C" void myr0dumpInvoke(ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DumpMethod(method, nullptr, kStrategyForceBackfill);
}

}  // namespace art HIDDEN
