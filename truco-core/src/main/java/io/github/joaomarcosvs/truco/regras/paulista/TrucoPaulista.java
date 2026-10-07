package io.github.joaomarcosvs.truco.regras.paulista;

import io.github.joaomarcosvs.truco.regras.ComposicaoDoBaralho;
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import io.github.joaomarcosvs.truco.regras.VarianteDeRegras;

/**
 * Regras do Truco Paulista ({@code docs/regras-truco-paulista.md}). É um record sem componentes, então todas as
 * instâncias são iguais.
 */
public record TrucoPaulista() implements VarianteDeRegras {

    private static final ComposicaoDoBaralho COMPOSICAO_DO_BARALHO = new ComposicaoDoBaralhoPaulista();
    private static final OrdemDeForca ORDEM_DE_FORCA = new OrdemDeForcaPaulista();

    @Override
    public ComposicaoDoBaralho composicaoDoBaralho() {
        return COMPOSICAO_DO_BARALHO;
    }

    @Override
    public OrdemDeForca ordemDeForca() {
        return ORDEM_DE_FORCA;
    }
}
