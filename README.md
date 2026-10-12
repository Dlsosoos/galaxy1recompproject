# Galaxy1Recomp 🪐

Casca Android (Kotlin + Compose + JNI) para uma recompilação estática do Super Mario Galaxy 1.

- Escolha a sua própria cópia do jogo (.iso ou .wbfs); o app valida o cabeçalho (IDs RMGE01/RMGP01/RMGJ01/RMGK01).
- Abas: Jogar, Mods, Config (~45 opções), Cheats (inclui "Desbloquear tudo").
- O núcleo recompilado (PowerPC Gekko → C++) entra em `app/src/main/cpp/recomp/` e é ligado em `native-lib.cpp`.
- Nenhum conteúdo da Nintendo está incluído; você precisa do seu próprio disco.

## Build
Push na branch `main` → GitHub Actions (`.github/workflows/build.yml`) gera `Galaxy1Recomp.apk`
(aba Actions → Artifacts, e também em Releases).

## Etapa 1 (pronta): leitor de disco
`app/src/main/cpp/recomp/core.cpp` abre `.iso`/`.wbfs`, decifra a partição de dados (AES-128),
lê a FST e extrai o `main.dol` para `files/game/` junto com `files.txt` (lista de arquivos) e `dol_sections.txt`.
A chave comum NÃO está incluída: use a do seu próprio Wii (arquivo de 16 bytes ou 32 caracteres hexadecimais).

## Próximas etapas
2. Recompilador PowerPC (Gekko) → C++ a partir do `main.dol`.
3. Reimplementar o SDK do Wii (GX, VI, KPAD, AX, DVD) no Android.
4. Ligar tudo em `g1_run` e no `nativeStart`.
