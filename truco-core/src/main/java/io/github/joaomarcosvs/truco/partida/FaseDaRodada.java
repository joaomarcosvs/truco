package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;

/** Em que ponto a rodada está e de quem se espera a próxima ação. */
public sealed interface FaseDaRodada {

    /** Aguardando o jogador jogar uma carta (RG-VAZA-1). */
    record AguardandoJogada(JogadorId jogador) implements FaseDaRodada {

        public AguardandoJogada {
            Objects.requireNonNull(jogador, "jogador");
        }
    }
}
