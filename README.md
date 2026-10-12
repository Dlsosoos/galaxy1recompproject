# Galaxy1Recomp 🪐

Casca Android (Kotlin + Compose + JNI) para uma recompilação estática do Super Mario Galaxy 1.

- Escolha a sua própria cópia do jogo (.iso ou .wbfs); o app valida o cabeçalho (IDs RMGE01/RMGP01/RMGJ01/RMGK01).
- Abas: Jogar, Mods, Config (~45 opções), Cheats (inclui "Desbloquear tudo").
- O núcleo recompilado (PowerPC Gekko → C++) entra em `app/src/main/cpp/recomp/` e é ligado em `native-lib.cpp`.
- Nenhum conteúdo da Nintendo está incluído; você precisa do seu próprio disco.

## Build
Push na branch `main` → GitHub Actions (`.github/workflows/build.yml`) gera `Galaxy1Recomp.apk`
(aba Actions → Artifacts, e também em Releases).
