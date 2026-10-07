package io.github.joaomarcosvs.truco.partida;

/** Por que uma ação foi rejeitada. */
public enum MotivoDeRejeicao {

    /** Quem agiu não está na partida. */
    JOGADOR_DESCONHECIDO,

    /** A partida já acabou (RG-FIM-1). */
    PARTIDA_FINALIZADA,

    /** Não é a vez do jogador: nenhuma ação dele é legal agora. */
    NAO_E_A_VEZ_DO_JOGADOR,

    /** É a vez do jogador, mas a ação não está entre as legais, como uma posição que não existe na mão. */
    ACAO_INVALIDA
}
