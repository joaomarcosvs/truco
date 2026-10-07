package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.Objects;

/** Em que ponto a rodada está e de quem se espera a próxima ação. */
public sealed interface FaseDaRodada {

    /**
     * Fase de descarte, antes da 1ª vaza (RG-DESC-2): todos respondem se descartam a carta da vez, e só o dono dela pode
     * descartá-la (RG-DESC-4, RG-DESC-10).
     */
    record AguardandoDescarte(Carta cartaDaVez) implements FaseDaRodada {

        public AguardandoDescarte {
            Objects.requireNonNull(cartaDaVez, "cartaDaVez");
        }
    }

    /** Na Rodada de Onze, o jogador da equipe com 11 decide se joga ou corre (RG-ONZE-1). */
    record DecisaoRodadaDeOnze(JogadorId jogador) implements FaseDaRodada {

        public DecisaoRodadaDeOnze {
            Objects.requireNonNull(jogador, "jogador");
        }
    }

    /** Aguardando o jogador jogar uma carta (RG-VAZA-1) ou, antes disso, pedir aumento (RG-AUM-2). */
    record AguardandoJogada(JogadorId jogador) implements FaseDaRodada {

        public AguardandoJogada {
            Objects.requireNonNull(jogador, "jogador");
        }
    }

    /** Aguardando o jogador responder a um pedido de aumento: aceitar, correr ou pedir mais (RG-AUM-3). */
    record AguardandoRespostaDeAumento(JogadorId respondedor, int nivelProposto) implements FaseDaRodada {

        public AguardandoRespostaDeAumento {
            Objects.requireNonNull(respondedor, "respondedor");
        }
    }

    /** A partida acabou: a equipe chegou aos pontos para vencer, e ninguém tem mais ações (RG-FIM-1). */
    record PartidaFinalizada(EquipeId vencedora) implements FaseDaRodada {

        public PartidaFinalizada {
            Objects.requireNonNull(vencedora, "vencedora");
        }
    }
}
