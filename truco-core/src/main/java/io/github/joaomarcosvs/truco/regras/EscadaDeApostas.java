package io.github.joaomarcosvs.truco.regras;

import io.github.joaomarcosvs.truco.partida.Aposta;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import java.util.OptionalInt;

/** Quanto a rodada pode valer e quem pode aumentar (RG-PARTIDA-3 e RG-AUM-* no Truco Paulista). */
public interface EscadaDeApostas {

    /** Quanto a rodada vale ao começar. */
    int valorInicial();

    /** O nível acima do valor, ou vazio se o valor já é o teto. */
    OptionalInt proximoNivel(int valor);

    /** Quanto ganha quem pediu um aumento quando o adversário corre do pedido. */
    int valorAoCorrer(int nivelRecusado);

    /** Quanto ganha o adversário quando um jogador corre na própria vez, sem pedido pendente. */
    int valorAoDesistir(int valorAtual);

    /** Se a equipe pode pedir aumento, dada a aposta atual (sem pedido pendente). */
    boolean podeAumentar(Aposta aposta, EquipeId equipe);
}
