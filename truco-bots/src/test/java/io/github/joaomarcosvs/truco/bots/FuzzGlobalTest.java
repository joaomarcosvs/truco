package io.github.joaomarcosvs.truco.bots;

import static io.github.joaomarcosvs.truco.bots.Partidas.ANA;
import static io.github.joaomarcosvs.truco.bots.Partidas.BETO;
import static io.github.joaomarcosvs.truco.bots.Partidas.CARLA;
import static io.github.joaomarcosvs.truco.bots.Partidas.DAVI;
import static io.github.joaomarcosvs.truco.bots.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.bots.Partidas.configuracao;
import static io.github.joaomarcosvs.truco.bots.Partidas.configuracaoEmDuplas;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.JogarEncoberta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.acao.VerCartasDoParceiro;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.Aposta;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.Jogada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.Rejeitada;
import io.github.joaomarcosvs.truco.partida.Rodada;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;

/**
 * Fuzz global (CLAUDE.md, seção 6): milhares de partidas completas entre {@link BotAleatorio}, no 1x1 e no 2x2,
 * conduzidas pelo {@link Arbitro} e conferidas só pela API pública. As verificações finas de vazamento entre duas
 * rodadas ficam nas propriedades do core.
 */
class FuzzGlobalTest {

    private static final List<Carta> BARALHO =
            new TrucoPaulista().composicaoDoBaralho().cartas();

    private static final Set<Carta> CARTAS_DO_BARALHO = Set.copyOf(BARALHO);

    private static final Map<String, Carta> POR_NOTACAO = BARALHO.stream().collect(toMap(Carta::toString, identity()));

    /** A notação de qualquer carta, como "10♠" ou "A♥". */
    private static final Pattern NOTACAO = Pattern.compile("(?:10|[2-9AJQK])[♥♠♦♣]");

    /** RG-AUM-1, copiada do documento de regras. */
    private static final Set<Integer> ESCADA = Set.of(1, 3, 6, 9, 12);

    /** RG-PARTIDA-1, copiado do documento de regras. */
    private static final int PONTOS_PARA_VENCER = 12;

    /** Limite de passos de uma partida; bem acima do que uma partida precisa. */
    private static final int LIMITE_DE_PASSOS = 5_000;

    /** Todas as ações possíveis com até 3 cartas na mão, além de posições que nunca existem. */
    private static final List<Acao> TODAS_AS_ACOES = todasAsAcoes();

    @Property(tries = 2_000)
    @Label("Fuzz global (RG-FIM-1, RG-AUM-1): entre BotAleatorio, no 1x1 e no 2x2, toda partida termina; as ações"
            + " legais são aceitas e as outras, rejeitadas; o valor fica na escada; o placar nunca diminui; e as 40"
            + " cartas se conservam")
    void invariantes(@ForAll boolean emDuplas, @ForAll long seed, @ForAll long seedDaAna, @ForAll long seedDoBeto) {
        Random amostra = new Random(seed);

        EstadoDaPartida fim = jogar(emDuplas, seed, seedDaAna, seedDoBeto, (antes, jogador, acao, aplicada) -> {
            EstadoDaPartida depois = aplicada.novoEstado();
            conferirAcaoSorteada(antes, amostra);
            conferirAposta(depois.rodada().aposta());
            for (JogadorId outro : antes.configuracao().jogadoresNaOrdemDaMesa()) {
                EquipeId equipe = depois.configuracao().equipeDe(outro);
                assertThat(depois.placar().pontosDe(equipe))
                        .isGreaterThanOrEqualTo(antes.placar().pontosDe(equipe));
            }
            conferirConservacao(depois.rodada());
        });

        EquipeId vencedora = ((FaseDaRodada.PartidaFinalizada) fim.rodada().fase()).vencedora();
        for (JogadorId jogador : fim.configuracao().jogadoresNaOrdemDaMesa()) {
            EquipeId equipe = fim.configuracao().equipeDe(jogador);
            if (equipe.equals(vencedora)) {
                assertThat(fim.placar().pontosDe(equipe)).isGreaterThanOrEqualTo(PONTOS_PARA_VENCER);
            } else {
                assertThat(fim.placar().pontosDe(equipe)).isLessThan(PONTOS_PARA_VENCER);
            }
        }
    }

    @Property(tries = 300)
    @Label("Fuzz global (RG-VIS-1 a RG-VIS-3, RG-ENC-3, RG-DESC-10, RG-2X2-15): a visão que cada bot recebe e os"
            + " eventos entregues a ele nunca mostram cartas ocultas; a mão do parceiro, só quando a dupla se vê")
    void nadaVaza(@ForAll boolean emDuplas, @ForAll long seed, @ForAll long seedDaAna, @ForAll long seedDoBeto) {
        conferirVisoes(MOTOR.novaPartida(configuracao(emDuplas, seed)));

        jogar(emDuplas, seed, seedDaAna, seedDoBeto, (antes, jogador, acao, aplicada) -> {
            conferirVisoes(aplicada.novoEstado());
            conferirEventos(aplicada);
        });
    }

    private static ConfiguracaoDaPartida configuracao(boolean emDuplas, long seed) {
        return emDuplas ? configuracaoEmDuplas(seed) : Partidas.configuracao(seed);
    }

    private static List<Acao> todasAsAcoes() {
        List<Acao> acoes = new ArrayList<>();
        for (int indiceNaMao = -1; indiceNaMao <= 3; indiceNaMao++) {
            acoes.add(new JogarCarta(indiceNaMao));
            acoes.add(new JogarEncoberta(indiceNaMao));
            acoes.add(new Descartar(indiceNaMao));
        }
        acoes.addAll(List.of(
                new RecusarDescarte(), new PedirAumento(), new Aceitar(), new Correr(), new VerCartasDoParceiro()));
        return List.copyOf(acoes);
    }

    /**
     * Joga uma partida inteira entre bots; falha se ela passar do limite de passos. No 2x2, a Carla e o Davi jogam com
     * as seeds seguintes às da Ana e do Beto.
     */
    private static EstadoDaPartida jogar(
            boolean emDuplas, long seed, long seedDaAna, long seedDoBeto, ObservadorDaPartida conferencia) {
        Map<JogadorId, Estrategia> bots = emDuplas
                ? Map.of(
                        ANA,
                        new BotAleatorio(seedDaAna),
                        BETO,
                        new BotAleatorio(seedDoBeto),
                        CARLA,
                        new BotAleatorio(seedDaAna + 1),
                        DAVI,
                        new BotAleatorio(seedDoBeto + 1))
                : Map.of(ANA, new BotAleatorio(seedDaAna), BETO, new BotAleatorio(seedDoBeto));
        int[] passos = {0};
        return new Arbitro(MOTOR, bots)
                .jogar(MOTOR.novaPartida(configuracao(emDuplas, seed)), (antes, jogador, acao, aplicada) -> {
                    if (++passos[0] > LIMITE_DE_PASSOS) {
                        fail("A partida passou de %d passos sem terminar", LIMITE_DE_PASSOS);
                    }
                    conferencia.aoAplicar(antes, jogador, acao, aplicada);
                });
    }

    /** Uma ação qualquer, de um jogador qualquer: o motor a aceita se, e só se, ela está em acoesLegais. */
    private static void conferirAcaoSorteada(EstadoDaPartida estado, Random amostra) {
        List<JogadorId> jogadores = estado.configuracao().jogadoresNaOrdemDaMesa();
        JogadorId jogador = jogadores.get(amostra.nextInt(jogadores.size()));
        Acao acao = TODAS_AS_ACOES.get(amostra.nextInt(TODAS_AS_ACOES.size()));
        boolean legal = MOTOR.acoesLegais(estado, jogador).contains(acao);
        assertThat(MOTOR.aplicar(estado, jogador, acao))
                .as("%s tentando %s", jogador, acao)
                .isInstanceOf(legal ? Aplicada.class : Rejeitada.class);
    }

    private static void conferirAposta(Aposta aposta) {
        if (!ESCADA.contains(aposta.valor())
                || aposta.pedidoPendente()
                        .filter(pedido -> !ESCADA.contains(pedido.nivelProposto()))
                        .isPresent()) {
            fail("Aposta fora da escada: %s", aposta);
        }
    }

    /** Mãos, baralho restante, vira, descartadas e jogadas: as 40 cartas, sem repetição. */
    private static void conferirConservacao(Rodada rodada) {
        List<Carta> cartas = new ArrayList<>();
        rodada.maos().values().forEach(cartas::addAll);
        cartas.addAll(rodada.baralhoRestante());
        rodada.vira().ifPresent(cartas::add);
        cartas.addAll(rodada.descarte().descartadas());
        rodada.vazas().forEach(vaza -> vaza.jogadas().forEach(jogada -> cartas.add(jogada.carta())));
        rodada.vazaAtual().forEach(jogada -> cartas.add(jogada.carta()));
        if (cartas.size() != BARALHO.size() || !new HashSet<>(cartas).equals(CARTAS_DO_BARALHO)) {
            fail("As cartas da rodada não são as 40 do baralho: %s", cartas);
        }
    }

    /**
     * A visão de cada jogador não mostra nenhuma carta oculta, e mostra a mão do parceiro exatamente quando a dupla se
     * vê. A carta da vez no descarte é pública e sai da conferência (é conferida à parte), porque o que não pode vazar
     * é quem a tem (RG-DESC-10).
     */
    private static void conferirVisoes(EstadoDaPartida estado) {
        Optional<Carta> cartaDaVez = estado.rodada().fase() instanceof FaseDaRodada.AguardandoDescarte(Carta carta)
                ? Optional.of(carta)
                : Optional.empty();
        for (JogadorId jogador : estado.configuracao().jogadoresNaOrdemDaMesa()) {
            VisaoDoJogador visao = MOTOR.visaoDe(estado, jogador);
            assertThat(visao.cartaDaVezNoDescarte()).isEqualTo(cartaDaVez);
            assertThat(visao.maosDosParceiros().keySet()).isEqualTo(parceirosVisiveis(jogador, estado));
            conferirQueNaoMostra(semCartaDaVez(visao), ocultasPara(jogador, estado), "a visão de " + jogador);
        }
    }

    /**
     * Os eventos que cada jogador recebe não mostram as cartas que continuam ocultas para ele depois do passo. Num passo
     * que inicia outra rodada, só os eventos da rodada nova são conferidos: os da rodada que terminou falam de cartas
     * que já voltaram ao baralho.
     */
    private static void conferirEventos(Aplicada aplicada) {
        EstadoDaPartida depois = aplicada.novoEstado();
        for (JogadorId jogador : depois.configuracao().jogadoresNaOrdemDaMesa()) {
            List<Evento> eventos = aplicada.eventos().stream()
                    .filter(evento -> evento.visivelPara(jogador))
                    .toList();
            int inicioDaRodadaNova = 0;
            for (int i = 0; i < eventos.size(); i++) {
                if (eventos.get(i) instanceof RodadaIniciada) {
                    inicioDaRodadaNova = i;
                }
            }
            conferirQueNaoMostra(
                    eventos.subList(inicioDaRodadaNova, eventos.size()),
                    ocultasPara(jogador, depois),
                    "os eventos de " + jogador);
        }
    }

    /**
     * Os parceiros cuja mão o jogador pode ver agora, escrito de novo a partir das regras: quando a dupla dele responde
     * a um aumento e todos quiseram ver as cartas (RG-2X2-10), e na decisão da Rodada de Onze da dupla com 11
     * (RG-2X2-11). No 1x1, nenhum.
     */
    private static Set<JogadorId> parceirosVisiveis(JogadorId jogador, EstadoDaPartida estado) {
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        List<JogadorId> parceiros = configuracao.parceirosDe(jogador);
        EquipeId equipe = configuracao.equipeDe(jogador);
        boolean seVeem = switch (estado.rodada().fase()) {
            case FaseDaRodada.AguardandoRespostaDeAumento resposta -> {
                List<JogadorId> querem =
                        estado.rodada().aposta().pedidoPendente().orElseThrow().queremVerAsCartas();
                yield configuracao.equipeDe(resposta.respondedor()).equals(equipe)
                        && querem.contains(jogador)
                        && querem.containsAll(parceiros);
            }
            case FaseDaRodada.DecisaoRodadaDeOnze decisao ->
                configuracao.equipeDe(decisao.jogador()).equals(equipe);
            default -> false;
        };
        return seVeem ? Set.copyOf(parceiros) : Set.of();
    }

    /**
     * As cartas que o jogador não pode ver: o baralho restante e as mãos dos outros, menos as dos parceiros quando a
     * dupla se vê (RG-VIS-2, RG-2X2-15), as cartas que os outros jogaram encobertas (RG-ENC-3) e, na Rodada
     * Escurinho, a própria mão (RG-VIS-3).
     */
    private static Set<Carta> ocultasPara(JogadorId jogador, EstadoDaPartida estado) {
        Rodada rodada = estado.rodada();
        Set<Carta> ocultas = new HashSet<>(rodada.baralhoRestante());
        boolean escurinho = rodada.tipo() instanceof TipoDeRodada.Escurinho;
        Set<JogadorId> visiveis = parceirosVisiveis(jogador, estado);
        rodada.maos().forEach((dono, mao) -> {
            if ((!dono.equals(jogador) && !visiveis.contains(dono)) || escurinho) {
                ocultas.addAll(mao);
            }
        });
        Stream.concat(rodada.vazas().stream().flatMap(vaza -> vaza.jogadas().stream()), rodada.vazaAtual().stream())
                .filter(jogada -> jogada.encoberta() && !jogada.jogador().equals(jogador))
                .map(Jogada::carta)
                .forEach(ocultas::add);
        return ocultas;
    }

    private static void conferirQueNaoMostra(Object objeto, Set<Carta> ocultas, String onde) {
        Set<Carta> mostradas = cartasMostradas(objeto.toString());
        mostradas.retainAll(ocultas);
        if (!mostradas.isEmpty()) {
            fail("%s mostra cartas ocultas %s: %s", onde, mostradas, objeto);
        }
    }

    /**
     * As cartas cuja notação aparece no texto. Os símbolos de naipe só aparecem em cartas, então a busca vale para
     * qualquer campo da visão ou do evento, inclusive os que forem criados depois.
     */
    private static Set<Carta> cartasMostradas(String texto) {
        Set<Carta> cartas = new HashSet<>();
        Matcher matcher = NOTACAO.matcher(texto);
        while (matcher.find()) {
            Carta carta = POR_NOTACAO.get(matcher.group());
            if (carta != null) {
                cartas.add(carta);
            }
        }
        return cartas;
    }

    private static VisaoDoJogador semCartaDaVez(VisaoDoJogador visao) {
        return new VisaoDoJogador(
                visao.jogador(),
                visao.jogadoresNaOrdemDaMesa(),
                visao.equipes(),
                visao.placar(),
                visao.numeroDaRodada(),
                visao.carteador(),
                visao.tipoDaRodada(),
                visao.aposta(),
                visao.vira(),
                visao.descartadas(),
                Optional.empty(),
                visao.mao(),
                visao.maosDosParceiros(),
                visao.cartasNaMao(),
                visao.vazas(),
                visao.vazaAtual(),
                visao.vezDe(),
                visao.vencedoraDaPartida());
    }
}
