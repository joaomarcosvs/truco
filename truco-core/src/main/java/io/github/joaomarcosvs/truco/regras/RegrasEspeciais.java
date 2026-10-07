package io.github.joaomarcosvs.truco.regras;

import io.github.joaomarcosvs.truco.partida.Placar;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;

/**
 * Rodadas especiais, como a Rodada de Onze e a Rodada Escurinho no Truco Paulista (RG-ONZE-*, RG-ESCURINHO-1): qual é o
 * tipo da rodada e o que cada tipo permite.
 */
public interface RegrasEspeciais {

    /** O tipo da rodada que começa com o placar dado. */
    TipoDeRodada tipoDaRodada(Placar placar);

    /** Se pode haver pedido de aumento nesse tipo de rodada. */
    boolean permiteAumento(TipoDeRodada tipo);

    /** Se pode haver carta encoberta nesse tipo de rodada. */
    boolean permiteEncoberta(TipoDeRodada tipo);

    /** Se há descarte nesse tipo de rodada. */
    boolean permiteDescarte(TipoDeRodada tipo);

    /** Se o jogador vê as próprias cartas nesse tipo de rodada. */
    boolean maoVisivel(TipoDeRodada tipo);

    /** Quanto vale a Rodada de Onze quando a equipe com 11 decide jogar. */
    int valorAoJogarARodadaDeOnze();

    /** Quanto o adversário ganha quando a equipe com 11 decide correr da Rodada de Onze. */
    int valorAoCorrerDaRodadaDeOnze();
}
