package io.github.joaomarcosvs.truco.regras;

/** Regras de uma variante de truco, montadas com peças pequenas, uma para cada responsabilidade. */
public interface VarianteDeRegras {

    /** Quais cartas existem no baralho. */
    ComposicaoDoBaralho composicaoDoBaralho();

    /** Como as cartas se comparam na disputa de uma vaza. */
    OrdemDeForca ordemDeForca();

    /** Como funciona o descarte antes da 1ª vaza. */
    RegrasDeDescarte regrasDeDescarte();

    /** Como as vazas são jogadas e decidem a rodada. */
    RegrasDeVaza regrasDeVaza();

    /** Quanto a rodada pode valer. */
    EscadaDeApostas escadaDeApostas();

    /** Rodadas especiais, como a Rodada de Onze e a Rodada Escurinho. */
    RegrasEspeciais regrasEspeciais();

    /** Quantos pontos vencem a partida. */
    PontuacaoDaPartida pontuacaoDaPartida();
}
