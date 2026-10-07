package io.github.joaomarcosvs.truco.regras.paulista;

import io.github.joaomarcosvs.truco.regras.ComposicaoDoBaralho;
import io.github.joaomarcosvs.truco.regras.EscadaDeApostas;
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import io.github.joaomarcosvs.truco.regras.RegrasDeDescarte;
import io.github.joaomarcosvs.truco.regras.RegrasDeVaza;
import io.github.joaomarcosvs.truco.regras.VarianteDeRegras;

/**
 * Regras do Truco Paulista ({@code docs/regras-truco-paulista.md}). É um record sem componentes, então todas as
 * instâncias são iguais.
 */
public record TrucoPaulista() implements VarianteDeRegras {

    private static final ComposicaoDoBaralho COMPOSICAO_DO_BARALHO = new ComposicaoDoBaralhoPaulista();
    private static final OrdemDeForca ORDEM_DE_FORCA = new OrdemDeForcaPaulista();
    private static final RegrasDeDescarte REGRAS_DE_DESCARTE = new RegrasDeDescartePaulista();
    private static final RegrasDeVaza REGRAS_DE_VAZA = new RegrasDeVazaPaulista(ORDEM_DE_FORCA);
    private static final EscadaDeApostas ESCADA_DE_APOSTAS = new EscadaDeApostasPaulista();

    @Override
    public ComposicaoDoBaralho composicaoDoBaralho() {
        return COMPOSICAO_DO_BARALHO;
    }

    @Override
    public OrdemDeForca ordemDeForca() {
        return ORDEM_DE_FORCA;
    }

    @Override
    public RegrasDeDescarte regrasDeDescarte() {
        return REGRAS_DE_DESCARTE;
    }

    @Override
    public RegrasDeVaza regrasDeVaza() {
        return REGRAS_DE_VAZA;
    }

    @Override
    public EscadaDeApostas escadaDeApostas() {
        return ESCADA_DE_APOSTAS;
    }
}
