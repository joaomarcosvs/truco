package io.github.joaomarcosvs.truco.bots;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;
import java.util.Optional;

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

    /**
     * Decide se faz uma ação opcional, fora da própria vez: no 2x2, querer ver as cartas do parceiro que recebeu um
     * pedido de aumento (RG-2X2-9). Vazio é não fazer nada. O padrão escolhe como {@link #decidir}.
     *
     * @param visao o que o jogador vê da partida
     * @param legais as ações opcionais legais agora, nunca vazia
     * @return uma das ações de {@code legais}, ou vazio
     */
    default Optional<Acao> decidirOpcional(VisaoDoJogador visao, List<Acao> legais) {
        return Optional.of(decidir(visao, legais));
    }
}
