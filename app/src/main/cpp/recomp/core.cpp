int g1_run(int fd);
return g1_run(fd);
#include <unistd.h>
int g1_run(int fd) {
    // aqui entra a leitura do disco e a execução do jogo
    close(fd);
    return 1;
}
