package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.CARLA;
import static io.github.joaomarcosvs.truco.partida.Partidas.DAVI;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.NotacaoDeCartas;
import io.github.joaomarcosvs.truco.regras.VarianteDeRegras;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Monta uma rodada com vira, mãos e placar escolhidos, para testar as regras em situações exatas. Por padrão a variante
 * é o Truco Paulista, o placar é 0 a 0, o descarte já terminou e a rodada está pronta para a 1ª vaza (ou para a
 * decisão, se for de Onze).
 */
final class Cenario {

    private final VarianteDeRegras variante;
    private final Optional<Carta> vira;
    private JogadorId carteador = ANA;
    private final Map<JogadorId, List<Carta>> maos = new HashMap<>();
    private List<Carta> topoDoBaralho = List.of();
    private boolean emDescarte;
    private boolean semBaralho;
    private boolean emDuplas;
    private Placar placar = new Placar(Map.of(EQUIPE_DA_ANA, 0, EQUIPE_DO_BETO, 0));

    private Cenario(VarianteDeRegras variante, Optional<Carta> vira) {
        this.variante = variante;
        this.vira = vira;
    }

    /** Cenário de Truco Paulista com a vira dada e Ana como carteadora, a menos que se indique outro carteador. */
    static Cenario comVira(String vira) {
        return new Cenario(new TrucoPaulista(), Optional.of(carta(vira)));
    }

    /** Cenário de uma variante sem vira, como a de manilhas fixas, com Ana como carteadora. */
    static Cenario semVira(VarianteDeRegras variante) {
        return new Cenario(variante, Optional.empty());
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

    /** Partida 2x2: Ana e Carla contra Beto e Davi, sentados nessa ordem (RG-2X2-1). As 4 mãos precisam ser dadas. */
    Cenario emDuplas() {
        emDuplas = true;
        return this;
    }

    /** Deixa o baralho restante vazio, para o caso-limite em que não há carta para comprar. */
    Cenario semBaralhoRestante() {
        semBaralho = true;
        return this;
    }

    EstadoDaPartida montar() {
        ConfiguracaoDaPartida configuracao = emDuplas
                ? new ConfiguracaoDaPartida(
                        List.of(
                                new Equipe(EQUIPE_DA_ANA, List.of(ANA, CARLA)),
                                new Equipe(EQUIPE_DO_BETO, List.of(BETO, DAVI))),
                        variante,
                        0)
                : new ConfiguracaoDaPartida(List.of(Equipe.individual(ANA), Equipe.individual(BETO)), variante, 0);
        List<Carta> usadas = new ArrayList<>(vira.stream().toList());
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
        // O tipo da rodada sai do placar, como no motor (RG-ONZE-1, RG-ESCURINHO-1).
        TipoDeRodada tipo = configuracao.variante().regrasEspeciais().tipoDaRodada(placar);
        List<Carta> sequenciaDeDescarte = variante.regrasDeDescarte().sequencia(vira);
        boolean comDescarte =
                emDescarte && variante.regrasEspeciais().permiteDescarte(tipo) && !sequenciaDeDescarte.isEmpty();
        FaseDaRodada fase;
        if (comDescarte) {
            fase = new FaseDaRodada.AguardandoDescarte(sequenciaDeDescarte.getFirst());
        } else if (tipo instanceof TipoDeRodada.DeOnze onze) {
            // RG-ONZE-1 e RG-2X2-12: decide quem da equipe com 11 joga primeiro na 1ª vaza.
            fase = new FaseDaRodada.DecisaoRodadaDeOnze(
                    configuracao.jogadoresAPartirDe(configuracao.aDireitaDe(carteador)).stream()
                            .filter(jogador -> configuracao.equipeDe(jogador).equals(onze.equipeComOnze()))
                            .findFirst()
                            .orElseThrow());
        } else {
            fase = new FaseDaRodada.AguardandoJogada(configuracao.aDireitaDe(carteador));
        }
        Rodada rodada = new Rodada(
                tipo,
                Aposta.inicial(1),
                vira,
                maos,
                baralhoRestante,
                comDescarte ? Descarte.inicial() : Descarte.encerrado(List.of()),
                List.of(),
                List.of(),
                fase);
        return new EstadoDaPartida(configuracao, placar, 1, carteador, rodada);
    }

    /** O placar no começo da rodada, que define se ela é normal, de Onze ou Escurinho. */
    Cenario placar(int pontosDaAna, int pontosDoBeto) {
        placar = new Placar(Map.of(EQUIPE_DA_ANA, pontosDaAna, EQUIPE_DO_BETO, pontosDoBeto));
        return this;
    }

    private static List<Carta> cartas(String notacoes) {
        return Arrays.stream(notacoes.split(" ")).map(NotacaoDeCartas::carta).toList();
    }
}
