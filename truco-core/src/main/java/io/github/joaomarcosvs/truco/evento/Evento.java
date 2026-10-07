package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.JogadorId;

/** Algo que aconteceu na partida, com a indicação de quem pode vê-lo (RG-VIS-1, RG-VIS-2). */
public sealed interface Evento
        permits AumentoAceito,
                AumentoPedido,
                CartaEncobertaJogada,
                CartaJogada,
                CartasDistribuidas,
                JogadorCorreu,
                PlacarAtualizado,
                RodadaAnulada,
                RodadaFinalizada,
                RodadaIniciada,
                VazaFinalizada {

    /** Quem pode ver o evento. */
    Visibilidade visibilidade();

    /** Se o evento pode ser entregue ao jogador. */
    default boolean visivelPara(JogadorId jogador) {
        return visibilidade().permite(jogador);
    }
}
