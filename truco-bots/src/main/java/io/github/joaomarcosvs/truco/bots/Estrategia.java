package io.github.joaomarcosvs.truco.bots;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;

/**
 * Como um jogador escolhe a próxima ação. Decide só com o que o jogador vê e com as ações legais, nunca com o estado
 * completo da partida, que tem informação oculta.
 */
public interface Estrategia {

    /**
     * Escolhe uma das ações legais.
     *
     * @param visao o que o jogador vê da partida
     * @param legais as ações legais agora, nunca vazia
     * @return uma das ações de {@code legais}
     */
    Acao decidir(VisaoDoJogador visao, List<Acao> legais);
}
