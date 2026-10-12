// Galaxy1Recomp - Etapa 1: leitor de disco Wii (.iso / .wbfs)
// Abre o disco, decifra a partição de dados, lista arquivos e extrai o main.dol.
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <string>
#include <vector>
#include <memory>
#include <algorithm>
#include <unistd.h>

namespace {

inline uint32_t be32(const uint8_t* p) { return (uint32_t)p[0] << 24 | (uint32_t)p[1] << 16 | (uint32_t)p[2] << 8 | p[3]; }
inline uint16_t be16(const uint8_t* p) { return (uint16_t)(p[0] << 8 | p[1]); }

// ---------------------------------------------------------------- AES-128 (só decifrar)
struct AES128 {
    uint8_t sb[256], isb[256], rk[176];

    static uint8_t xt(uint8_t x) { return (uint8_t)((x << 1) ^ ((x & 0x80) ? 0x1B : 0)); }
    static uint8_t gm(uint8_t x, uint8_t y) {
        uint8_t r = 0;
        while (y) { if (y & 1) r ^= x; x = xt(x); y >>= 1; }
        return r;
    }
    static uint8_t rl(uint8_t v, int s) { return (uint8_t)((v << s) | (v >> (8 - s))); }

    explicit AES128(const uint8_t key[16]) {
        uint8_t p = 1, q = 1;
        do {
            p = p ^ (uint8_t)(p << 1) ^ ((p & 0x80) ? 0x1B : 0);
            q ^= (uint8_t)(q << 1); q ^= (uint8_t)(q << 2); q ^= (uint8_t)(q << 4);
            if (q & 0x80) q ^= 0x09;
            sb[p] = q ^ rl(q, 1) ^ rl(q, 2) ^ rl(q, 3) ^ rl(q, 4) ^ 0x63;
        } while (p != 1);
        sb[0] = 0x63;
        for (int i = 0; i < 256; i++) isb[sb[i]] = (uint8_t)i;

        memcpy(rk, key, 16);
        uint8_t rcon = 1;
        for (int i = 16; i < 176; i += 4) {
            uint8_t t[4] = {rk[i - 4], rk[i - 3], rk[i - 2], rk[i - 1]};
            if (i % 16 == 0) {
                uint8_t t0 = t[0];
                t[0] = sb[t[1]] ^ rcon; t[1] = sb[t[2]]; t[2] = sb[t[3]]; t[3] = sb[t0];
                rcon = xt(rcon);
            }
            for (int j = 0; j < 4; j++) rk[i + j] = rk[i - 16 + j] ^ t[j];
        }
    }

    void decryptBlock(const uint8_t in[16], uint8_t out[16]) const {
        uint8_t s[16], t[16];
        for (int i = 0; i < 16; i++) s[i] = in[i] ^ rk[160 + i];
        for (int r = 9; r >= 0; r--) {
            for (int c = 0; c < 4; c++)
                for (int rr = 0; rr < 4; rr++) t[rr + 4 * ((c + rr) % 4)] = s[rr + 4 * c];
            for (int i = 0; i < 16; i++) s[i] = isb[t[i]] ^ rk[r * 16 + i];
            if (r > 0) {
                for (int c = 0; c < 4; c++) {
                    uint8_t a0 = s[4 * c], a1 = s[4 * c + 1], a2 = s[4 * c + 2], a3 = s[4 * c + 3];
                    s[4 * c]     = gm(a0, 14) ^ gm(a1, 11) ^ gm(a2, 13) ^ gm(a3, 9);
                    s[4 * c + 1] = gm(a0, 9)  ^ gm(a1, 14) ^ gm(a2, 11) ^ gm(a3, 13);
                    s[4 * c + 2] = gm(a0, 13) ^ gm(a1, 9)  ^ gm(a2, 14) ^ gm(a3, 11);
                    s[4 * c + 3] = gm(a0, 11) ^ gm(a1, 13) ^ gm(a2, 9)  ^ gm(a3, 14);
                }
            }
        }
        memcpy(out, s, 16);
    }

    // CBC: decifra "len" bytes (múltiplo de 16) de "in" para "out" (podem ser o mesmo buffer)
    void cbcDecrypt(const uint8_t iv[16], const uint8_t* in, uint8_t* out, size_t len) const {
        uint8_t prev[16], cur[16], dec[16];
        memcpy(prev, iv, 16);
        for (size_t o = 0; o + 16 <= len; o += 16) {
            memcpy(cur, in + o, 16);
            decryptBlock(cur, dec);
            for (int i = 0; i < 16; i++) out[o + i] = dec[i] ^ prev[i];
            memcpy(prev, cur, 16);
        }
    }
};

// ---------------------------------------------------------------- Disco (ISO ou WBFS)
struct Disc {
    int fd = -1;
    bool wbfs = false;
    uint8_t secShift = 0;
    std::vector<uint16_t> wlba;

    bool raw(uint64_t off, void* buf, size_t len) const {
        uint8_t* p = (uint8_t*)buf;
        size_t done = 0;
        while (done < len) {
            ssize_t r = pread(fd, p + done, len - done, (off_t)(off + done));
            if (r <= 0) break;
            done += (size_t)r;
        }
        if (done < len) memset(p + done, 0, len - done);
        return done > 0 || len == 0;
    }

    bool open(int f, std::string& err) {
        fd = f;
        uint8_t h[16];
        if (!raw(0, h, sizeof h)) { err = "não consegui ler o arquivo"; return false; }
        if (memcmp(h, "WBFS", 4) != 0) { wbfs = false; return true; }
        wbfs = true;
        uint8_t hdShift = h[8];
        secShift = h[9];
        if (hdShift < 9 || hdShift > 12 || secShift < 15 || secShift > 26) { err = "cabeçalho WBFS inválido"; return false; }
        if (h[12] == 0) { err = "WBFS sem disco na primeira posição"; return false; }
        uint64_t n = (uint64_t)(143432 * 2) >> (secShift - 15);
        std::vector<uint8_t> tab(n * 2);
        raw((uint64_t(1) << hdShift) + 0x100, tab.data(), tab.size());
        wlba.resize(n);
        for (size_t i = 0; i < n; i++) wlba[i] = be16(&tab[i * 2]);
        return true;
    }

    // Lê usando o endereço do disco Wii (como se fosse uma ISO)
    bool read(uint64_t off, void* buf, size_t len) const {
        if (!wbfs) return raw(off, buf, len);
        uint8_t* p = (uint8_t*)buf;
        uint64_t secSize = uint64_t(1) << secShift;
        size_t done = 0;
        while (done < len) {
            uint64_t cur = off + done;
            uint64_t idx = cur >> secShift, inSec = cur & (secSize - 1);
            size_t chunk = (size_t)std::min<uint64_t>(len - done, secSize - inSec);
            uint16_t lba = idx < wlba.size() ? wlba[idx] : 0;
            if (lba == 0) memset(p + done, 0, chunk);
            else raw((uint64_t(lba) << secShift) + inSec, p + done, chunk);
            done += chunk;
        }
        return true;
    }
};

// ---------------------------------------------------------------- Partição Wii (criptografada)
struct Partition {
    const Disc* disc = nullptr;
    uint64_t dataStart = 0;
    std::unique_ptr<AES128> aes;
    int64_t cachedCluster = -1;
    uint8_t cache[0x7C00];

    bool read(uint64_t off, void* buf, size_t len) {
        uint8_t* p = (uint8_t*)buf;
        size_t done = 0;
        uint8_t rawc[0x8000];
        while (done < len) {
            uint64_t cur = off + done;
            int64_t cl = (int64_t)(cur / 0x7C00);
            size_t in = (size_t)(cur % 0x7C00);
            if (cl != cachedCluster) {
                disc->read(dataStart + (uint64_t)cl * 0x8000, rawc, 0x8000);
                uint8_t iv[16];
                memcpy(iv, rawc + 0x3D0, 16);
                aes->cbcDecrypt(iv, rawc + 0x400, cache, 0x7C00);
                cachedCluster = cl;
            }
            size_t chunk = std::min<size_t>(len - done, 0x7C00 - in);
            memcpy(p + done, cache + in, chunk);
            done += chunk;
        }
        return true;
    }
};

bool openDataPartition(const Disc& d, const uint8_t commonKey[16], Partition& part, std::string& err) {
    uint8_t grp[32];
    d.read(0x40000, grp, sizeof grp);
    for (int g = 0; g < 4; g++) {
        uint32_t cnt = be32(grp + g * 8);
        uint64_t tabOff = (uint64_t)be32(grp + g * 8 + 4) << 2;
        if (cnt == 0 || cnt > 16 || tabOff == 0) continue;
        for (uint32_t i = 0; i < cnt; i++) {
            uint8_t e[8];
            d.read(tabOff + i * 8, e, 8);
            uint32_t type = be32(e + 4);
            if (type != 0) continue; // 0 = partição de dados do jogo
            uint64_t pOff = (uint64_t)be32(e) << 2;
            uint8_t t[0x2C0];
            d.read(pOff, t, sizeof t);
            uint8_t iv[16] = {0};
            memcpy(iv, t + 0x1DC, 8);
            uint8_t titleKey[16];
            AES128(commonKey).cbcDecrypt(iv, t + 0x1BF, titleKey, 16);
            part.disc = &d;
            part.dataStart = pOff + ((uint64_t)be32(t + 0x2B8) << 2);
            part.aes.reset(new AES128(titleKey));
            part.cachedCluster = -1;
            return true;
        }
    }
    err = "partição de dados não encontrada";
    return false;
}

// ---------------------------------------------------------------- FST
struct FileEnt { std::string path; uint64_t off; uint32_t size; };

bool loadFst(Partition& p, const uint8_t* boot, std::vector<FileEnt>& out, std::string& err) {
    uint64_t fstOff = (uint64_t)be32(boot + 0x424) << 2;
    uint32_t fstSize = be32(boot + 0x428) << 2;
    if (fstSize < 12 || fstSize > (32u << 20)) { err = "FST com tamanho inválido"; return false; }
    std::vector<uint8_t> fst(fstSize);
    p.read(fstOff, fst.data(), fstSize);
    uint32_t n = be32(&fst[8]);
    if (n < 1 || (uint64_t)n * 12 > fstSize) { err = "FST corrompida"; return false; }
    const char* names = (const char*)&fst[(size_t)n * 12];
    size_t namesLen = fstSize - (size_t)n * 12;
    std::vector<std::pair<uint32_t, std::string>> stack;
    for (uint32_t i = 1; i < n; i++) {
        while (!stack.empty() && i >= stack.back().first) stack.pop_back();
        const uint8_t* e = &fst[(size_t)i * 12];
        uint32_t no = be32(e) & 0xFFFFFF;
        if (no >= namesLen) { err = "nome fora da FST"; return false; }
        std::string name(names + no, strnlen(names + no, namesLen - no));
        if (e[0]) stack.push_back({be32(e + 8), name});
        else {
            std::string path;
            for (auto& s : stack) path += s.second + "/";
            path += name;
            out.push_back({path, (uint64_t)be32(e + 4) << 2, be32(e + 8)});
        }
    }
    return true;
}

bool parseHex(const std::string& s, uint8_t out[16]) {
    std::string h;
    for (char c : s) if (!isspace((unsigned char)c)) h += c;
    if (h.size() != 32) return false;
    for (int i = 0; i < 16; i++) {
        unsigned v;
        if (sscanf(h.substr(i * 2, 2).c_str(), "%2x", &v) != 1) return false;
        out[i] = (uint8_t)v;
    }
    return true;
}

bool writeFile(const std::string& path, const void* data, size_t len) {
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) return false;
    bool ok = fwrite(data, 1, len, f) == len;
    fclose(f);
    return ok;
}

} // namespace

// ---------------------------------------------------------------- API usada pelo JNI
// Retorna um relatório. Começa com "OK" em caso de sucesso, ou "ERRO:" caso contrário.
std::string g1_extract(int fd, const std::string& keyHex, const std::string& outDir) {
    struct Closer { int fd; ~Closer() { if (fd >= 0) close(fd); } } closer{fd};
    char buf[256];

    uint8_t ck[16];
    if (!parseHex(keyHex, ck)) return "ERRO: chave comum inválida (precisa de 32 caracteres hexadecimais)";

    Disc d;
    std::string err;
    if (!d.open(fd, err)) return "ERRO: " + err;

    uint8_t hdr[0x20];
    d.read(0, hdr, sizeof hdr);
    std::string id((const char*)hdr, 6);
    if (be32(hdr + 0x18) != 0x5D1C9EA3) return "ERRO: não é um disco de Wii válido";
    if (id.compare(0, 3, "RMG") != 0) return "ERRO: disco não é o Super Mario Galaxy 1 (ID " + id + ")";

    Partition part;
    if (!openDataPartition(d, ck, part, err)) return "ERRO: " + err;

    uint8_t boot[0x440];
    part.read(0, boot, sizeof boot);
    if (be32(boot + 0x18) != 0x5D1C9EA3)
        return "ERRO: não consegui decifrar a partição. A chave comum provavelmente está errada.";

    // ---- main.dol
    uint64_t dolOff = (uint64_t)be32(boot + 0x420) << 2;
    uint8_t dh[0x100];
    part.read(dolOff, dh, sizeof dh);
    uint32_t dolSize = 0;
    std::string secs;
    for (int i = 0; i < 18; i++) {
        uint32_t o = be32(dh + i * 4), a = be32(dh + 0x48 + i * 4), s = be32(dh + 0x90 + i * 4);
        if (s == 0) continue;
        dolSize = std::max(dolSize, o + s);
        snprintf(buf, sizeof buf, "%s%d off=0x%X addr=0x%08X size=0x%X\n", i < 7 ? "text" : "data", i < 7 ? i : i - 7, o, a, s);
        secs += buf;
    }
    uint32_t entry = be32(dh + 0xE0);
    if (dolSize == 0 || dolSize > (64u << 20)) return "ERRO: main.dol inválido";
    std::vector<uint8_t> dol(dolSize);
    part.read(dolOff, dol.data(), dolSize);
    if (!writeFile(outDir + "/main.dol", dol.data(), dol.size())) return "ERRO: não consegui gravar main.dol em " + outDir;

    // ---- lista de arquivos
    std::vector<FileEnt> files;
    if (!loadFst(part, boot, files, err)) return "ERRO: " + err;
    std::string list;
    uint64_t total = 0;
    for (auto& f : files) {
        snprintf(buf, sizeof buf, "%u\t", f.size);
        list += buf + f.path + "\n";
        total += f.size;
    }
    writeFile(outDir + "/files.txt", list.data(), list.size());
    writeFile(outDir + "/dol_sections.txt", secs.data(), secs.size());

    snprintf(buf, sizeof buf,
             "OK\nDisco: %s (%s)\nmain.dol: %u bytes, entrada 0x%08X\nArquivos: %zu (%.1f MB)\nSaída: %s",
             id.c_str(), d.wbfs ? "WBFS" : "ISO", dolSize, entry, files.size(), total / 1048576.0, outDir.c_str());
    return buf;
}

#ifdef G1_TEST
#include <fcntl.h>
#include <iostream>
int main(int argc, char** argv) {
    if (argc < 4) return 2;
    int fd = open(argv[1], O_RDONLY);
    std::cout << g1_extract(fd, argv[2], argv[3]) << std::endl;
    return 0;
}
#endif
