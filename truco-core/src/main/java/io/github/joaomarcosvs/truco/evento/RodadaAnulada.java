package io.github.joaomarcosvs.truco.evento;

/** As três vazas empataram: a rodada foi anulada e ninguém pontua (RG-EMP-5). */
public record RodadaAnulada(int numeroDaRodada) implements Evento {

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
