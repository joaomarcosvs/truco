package io.github.joaomarcosvs.truco.bots;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Escolhe uma das ações legais ao acaso, todas com a mesma chance. A escolha vem de um {@link Random} com a seed dada,
 * cujo algoritmo é fixo na especificação do Java: a mesma seed dá as mesmas escolhas em qualquer JVM. Guarda estado (o
 * gerador), então cada jogador precisa da própria instância.
 */
public final class BotAleatorio implements Estrategia {

    private final Random gerador;

    public BotAleatorio(long seed) {
        this.gerador = new Random(seed);
    }

    @Override
    public Acao decidir(VisaoDoJogador visao, List<Acao> legais) {
        Objects.requireNonNull(visao, "visao");
        if (legais.isEmpty()) {
            throw new IllegalArgumentException("Não há ação legal para escolher");
        }
        return legais.get(gerador.nextInt(legais.size()));
    }
}
