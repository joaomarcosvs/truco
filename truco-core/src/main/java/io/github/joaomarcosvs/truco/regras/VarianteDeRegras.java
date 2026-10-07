package io.github.joaomarcosvs.truco.regras;

/** Regras de uma variante de truco, montadas com peças pequenas, uma para cada responsabilidade. */
public interface VarianteDeRegras {

    /** Quais cartas existem no baralho. */
    ComposicaoDoBaralho composicaoDoBaralho();

    /** Como as cartas se comparam na disputa de uma vaza. */
    OrdemDeForca ordemDeForca();

    /** Como as vazas são jogadas e decidem a rodada. */
    RegrasDeVaza regrasDeVaza();

    /** Quanto a rodada pode valer. */
    EscadaDeApostas escadaDeApostas();
}
