package io.github.joaomarcosvs.truco.regras;

/** Quantos pontos uma equipe precisa para vencer a partida (RG-PARTIDA-1 e RG-FIM-1 no Truco Paulista). */
public interface PontuacaoDaPartida {

    /** Pontos para vencer: a partida termina assim que uma equipe chega a eles ou passa deles. */
    int pontosParaVencer();
}
