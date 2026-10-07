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

/**
 * Monta a rodada 1 com vira e mãos escolhidas, para testar as regras em situações exatas. Por padrão o descarte já
 * terminou e a rodada está pronta para a 1ª vaza.
 */
final class Cenario {

    private final Carta vira;
    private JogadorId carteador = ANA;
    private final Map<JogadorId, List<Carta>> maos = new HashMap<>();
    private List<Carta> topoDoBaralho = List.of();
    private boolean emDescarte;
    private boolean semBaralho;

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
        maos.put(jogador, cartas(cartas));
        return this;
    }

    /** As primeiras cartas do baralho restante, que são as compradas no descarte. */
    Cenario baralhoComecandoPor(String cartas) {
        topoDoBaralho = cartas(cartas);
        return this;
    }

    /** Começa a rodada na fase de descarte, ainda sem nada descartado. */
    Cenario emDescarte() {
        emDescarte = true;
        return this;
    }

    /** Deixa o baralho restante vazio, para o caso-limite em que não há carta para comprar. */
    Cenario semBaralhoRestante() {
        semBaralho = true;
        return this;
    }

    EstadoDaPartida montar() {
        ConfiguracaoDaPartida configuracao = configuracao(0);
        List<Carta> usadas = new ArrayList<>(List.of(vira));
        maos.values().forEach(usadas::addAll);
        usadas.addAll(topoDoBaralho);
        if (new HashSet<>(usadas).size() < usadas.size()) {
            throw new IllegalArgumentException("Cenário com cartas repetidas: " + usadas);
        }
        List<Carta> baralhoRestante = new ArrayList<>(topoDoBaralho);
        configuracao.variante().composicaoDoBaralho().cartas().stream()
                .filter(carta -> !usadas.contains(carta))
                .forEach(baralhoRestante::add);
        if (semBaralho) {
            baralhoRestante.clear();
        }
        FaseDaRodada fase = emDescarte
                ? new FaseDaRodada.AguardandoDescarte(configuracao
                        .variante()
                        .regrasDeDescarte()
                        .sequencia(Optional.of(vira))
                        .getFirst())
                : new FaseDaRodada.AguardandoJogada(configuracao.aDireitaDe(carteador));
        Rodada rodada = new Rodada(
                Aposta.inicial(1),
                Optional.of(vira),
                maos,
                baralhoRestante,
                emDescarte ? Descarte.inicial() : Descarte.encerrado(List.of()),
                List.of(),
                List.of(),
                fase);
        return new EstadoDaPartida(configuracao, Placar.zerado(configuracao.equipes()), 1, carteador, rodada);
    }

    private static List<Carta> cartas(String notacoes) {
        return Arrays.stream(notacoes.split(" ")).map(NotacaoDeCartas::carta).toList();
    }
}
