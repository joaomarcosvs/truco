package io.github.joaomarcosvs.truco.regras;

/**
 * Formatos de partida que a variante aceita e o que os parceiros de uma dupla podem ver das cartas um do outro
 * (RG-ESC-1 e RG-2X2-* no Truco Paulista).
 */
public interface RegrasDeDupla {

    /** Se a variante aceita equipes com esse número de jogadores (1 no 1x1, 2 no 2x2). */
    boolean aceitaJogadoresPorEquipe(int jogadores);

    /**
     * Se a dupla que recebe um pedido de aumento pode ver as cartas uma da outra, quando todos os parceiros querem,
     * até responder.
     */
    boolean podeVerCartasAoReceberAumento();

    /** Se, na Rodada de Onze, os parceiros da equipe com 11 veem as cartas um do outro até a decisão. */
    boolean parceirosSeVeemNaRodadaDeOnze();
}
