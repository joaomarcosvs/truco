package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracao;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.NotacaoDeCartas;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Monta a rodada 1 com vira e mãos escolhidas, para testar as regras em situações exatas. */
final class Cenario {

    private final Carta vira;
    private JogadorId carteador = ANA;
    private final Map<JogadorId, List<Carta>> maos = new HashMap<>();

    private Cenario(Carta vira) {
        this.vira = vira;
    }

    /** Cenário com a vira dada e Ana como carteadora, a menos que se indique outro carteador. */
    static Cenario comVira(String vira) {
        return new Cenario(carta(vira));
    }

    Cenario carteador(JogadorId carteador) {
        this.carteador = carteador;
        return this;
    }

    /** A mão do jogador, em notação curta separada por espaços, como {@code "A♥ 3♠ 4♦"}. */
    Cenario mao(JogadorId jogador, String cartas) {
        maos.put(
                jogador,
                Arrays.stream(cartas.split(" ")).map(NotacaoDeCartas::carta).toList());
        return this;
    }

    /** O estado na rodada 1, aguardando a jogada de quem está à direita do carteador (RG-PARTIDA-4). */
    EstadoDaPartida montar() {
        ConfiguracaoDaPartida configuracao = configuracao(0);
        List<Carta> usadas = new ArrayList<>(List.of(vira));
        maos.values().forEach(usadas::addAll);
        if (new HashSet<>(usadas).size() < usadas.size()) {
            throw new IllegalArgumentException("Cenário com cartas repetidas: " + usadas);
        }
        List<Carta> baralhoRestante = configuracao.variante().composicaoDoBaralho().cartas().stream()
                .filter(carta -> !usadas.contains(carta))
                .toList();
        FaseDaRodada fase = new FaseDaRodada.AguardandoJogada(configuracao.aDireitaDe(carteador));
        Rodada rodada =
                new Rodada(Aposta.inicial(1), Optional.of(vira), maos, baralhoRestante, List.of(), List.of(), fase);
        return new EstadoDaPartida(configuracao, Placar.zerado(configuracao.equipes()), 1, carteador, rodada);
    }
}
