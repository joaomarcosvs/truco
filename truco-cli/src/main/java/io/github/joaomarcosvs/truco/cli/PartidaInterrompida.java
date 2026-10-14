package io.github.joaomarcosvs.truco.cli;

/** O jogador encerrou a entrada (fim do arquivo ou "sair") no meio da partida. */
final class PartidaInterrompida extends RuntimeException {

    private static final long serialVersionUID = 1L;

    PartidaInterrompida() {
        super("Partida interrompida pelo jogador");
    }
}
