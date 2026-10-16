package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.CartasPresentes.ocultasPara;
import static io.github.joaomarcosvs.truco.partida.CartasPresentes.parceirosVisiveis;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.CARLA;
import static io.github.joaomarcosvs.truco.partida.Partidas.DAVI;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.aplicarAceita;
import static io.github.joaomarcosvs.truco.partida.Partidas.daVez;
import static org.assertj.core.api.Assertions.assertThat;

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
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoDescarte;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Partidas com escolhas aleatórias entre as ações legais, no 1x1 e no 2x2, conferindo os invariantes do core a cada
 * passo. Umas jogam alguns passos, com escolhas que o jqwik consegue encolher; outras jogam a partida inteira, até o
 * fim.
 */
class PartidaAleatoriaPropriedadesTest {

    private static final List<Carta> BARALHO =
            new TrucoPaulista().composicaoDoBaralho().cartas();

    /** Todas as ações possíveis com até 3 cartas na mão, além de posições que nunca existem. */
    private static final List<Acao> TODAS_AS_ACOES = List.of(
            new JogarCarta(-1),
            new JogarCarta(0),
            new JogarCarta(1),
            new JogarCarta(2),
            new JogarCarta(3),
            new JogarEncoberta(-1),
            new JogarEncoberta(0),
            new JogarEncoberta(1),
            new JogarEncoberta(2),
            new JogarEncoberta(3),
            new Descartar(-1),
            new Descartar(0),
            new Descartar(1),
            new Descartar(2),
            new Descartar(3),
            new RecusarDescarte(),
            new PedirAumento(),
            new Aceitar(),
            new Correr(),
            new VerCartasDoParceiro());

    /** RG-AUM-1, copiada do documento de regras. */
    private static final Set<Integer> ESCADA = Set.of(1, 3, 6, 9, 12);

    /** Limite de passos de uma partida inteira; bem acima do que uma partida precisa. */
    private static final int LIMITE_DE_PASSOS = 5_000;

    /** O formato da partida: 1x1 (Ana contra Beto) ou 2x2 (Ana e Carla contra Beto e Davi). */
    private record Formato(String nome, List<JogadorId> jogadores, LongFunction<ConfiguracaoDaPartida> configuracao) {

        @Override
        public String toString() {
            return nome;
        }
    }

    private static final Formato UM_CONTRA_UM = new Formato("1x1", List.of(ANA, BETO), Partidas::configuracao);
    private static final Formato DUPLAS =
            new Formato("2x2", List.of(ANA, BETO, CARLA, DAVI), Partidas::configuracaoEmDuplas);

    /** Um passo da partida: o estado antes, quem agiu, a ação e o que o motor devolveu. */
    private record Passo(EstadoDaPartida antes, JogadorId jogador, Acao acao, Aplicada aplicada) {}

    @Property
    @Label("Conservação: mãos, baralho restante, vira, descartadas e jogadas somam sempre as 40 cartas, sem"
            + " repetição")
    void conservacaoDasCartas(
            @ForAll("formatos") Formato formato, @ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        jogar(formato, seed, escolhas).forEach(PartidaAleatoriaPropriedadesTest::conferirConservacao);
    }

    @Property
    @Label("Toda ação de acoesLegais é aceita, e toda ação fora dela é rejeitada")
    void legaisAceitasEIlegaisRejeitadas(
            @ForAll("formatos") Formato formato, @ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        for (Passo passo : jogar(formato, seed, escolhas)) {
            for (JogadorId jogador : formato.jogadores()) {
                List<Acao> legais = MOTOR.acoesLegais(passo.antes(), jogador);
                for (Acao acao : TODAS_AS_ACOES) {
                    Resultado resultado = MOTOR.aplicar(passo.antes(), jogador, acao);
                    assertThat(resultado)
                            .as("%s tentando %s", jogador, acao)
                            .isInstanceOf(legais.contains(acao) ? Aplicada.class : Rejeitada.class);
                }
            }
        }
    }

    @Property
    @Label("RG-VIS-1, RG-VIS-2 e RG-2X2-15: visão e eventos de um jogador nunca mostram as cartas dos outros nem as do"
            + " baralho, a não ser as do parceiro nos momentos em que a dupla se vê")
    void nadaVaza(@ForAll("formatos") Formato formato, @ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        jogar(formato, seed, escolhas).forEach(passo -> conferirQueNadaVaza(formato, passo));
    }

    @Property
    @Label("O placar nunca diminui")
    void placarNuncaDiminui(
            @ForAll("formatos") Formato formato, @ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        for (Passo passo : jogar(formato, seed, escolhas)) {
            Placar antes = passo.antes().placar();
            Placar depois = passo.aplicada().novoEstado().placar();
            for (EquipeId equipe : List.of(EQUIPE_DA_ANA, EQUIPE_DO_BETO)) {
                assertThat(depois.pontosDe(equipe)).isGreaterThanOrEqualTo(antes.pontosDe(equipe));
            }
        }
    }

    @Property
    @Label("RG-AUM-1: o valor da rodada e o nível pedido estão sempre na escada de apostas")
    void valorSempreNaEscada(
            @ForAll("formatos") Formato formato, @ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        for (Passo passo : jogar(formato, seed, escolhas)) {
            Aposta aposta = passo.aplicada().novoEstado().rodada().aposta();
            assertThat(ESCADA).contains(aposta.valor());
            aposta.pedidoPendente().ifPresent(pedido -> assertThat(ESCADA).contains(pedido.nivelProposto()));
        }
    }

    @Property
    @Label("Determinismo: a mesma seed com as mesmas ações gera a mesma partida, com os mesmos eventos")
    void deterministica(
            @ForAll("formatos") Formato formato, @ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        assertThat(jogar(formato, seed, escolhas)).isEqualTo(jogar(formato, seed, escolhas));
    }

    @Property(tries = 200)
    @Label("RG-FIM-1: toda partida termina, com a vencedora em 12 pontos ou mais e a outra abaixo de 12")
    void partidaSempreTermina(@ForAll("formatos") Formato formato, @ForAll long seed, @ForAll long sementeDasEscolhas) {
        List<Passo> passos = jogarAteOFim(formato, seed, sementeDasEscolhas);

        EstadoDaPartida fim = passos.getLast().aplicada().novoEstado();
        assertThat(fim.rodada().fase()).isInstanceOf(FaseDaRodada.PartidaFinalizada.class);
        EquipeId vencedora = ((FaseDaRodada.PartidaFinalizada) fim.rodada().fase()).vencedora();
        EquipeId outra = vencedora.equals(EQUIPE_DA_ANA) ? EQUIPE_DO_BETO : EQUIPE_DA_ANA;
        assertThat(fim.placar().pontosDe(vencedora)).isGreaterThanOrEqualTo(12);
        assertThat(fim.placar().pontosDe(outra)).isLessThan(12);
        assertThat(formato.jogadores())
                .allSatisfy(
                        jogador -> assertThat(MOTOR.acoesLegais(fim, jogador)).isEmpty());
    }

    @Property(tries = 100)
    @Label("RG-AUM-7, RG-ONZE-2, RG-ESCURINHO-1 e RG-DESC-9: na Rodada de Onze e na Escurinho ninguém pede aumento,"
            + " e na Escurinho ninguém encobre nem descarta")
    void restricoesDasRodadasEspeciais(
            @ForAll("formatos") Formato formato, @ForAll long seed, @ForAll long sementeDasEscolhas) {
        for (Passo passo : jogarAteOFim(formato, seed, sementeDasEscolhas)) {
            TipoDeRodada tipo = passo.antes().rodada().tipo();
            for (JogadorId jogador : formato.jogadores()) {
                List<Acao> legais = MOTOR.acoesLegais(passo.antes(), jogador);
                if (!(tipo instanceof TipoDeRodada.Normal)) {
                    assertThat(legais).doesNotContain(new PedirAumento());
                }
                if (tipo instanceof TipoDeRodada.Escurinho) {
                    assertThat(legais)
                            .noneMatch(acao -> acao instanceof JogarEncoberta
                                    || acao instanceof Descartar
                                    || acao instanceof RecusarDescarte);
                }
            }
        }
    }

    @Property(tries = 100)
    @Label("RG-AUM-6: só se corre respondendo a um pedido de aumento ou na decisão da Rodada de Onze, e quem responde"
            + " sempre pode correr")
    void correrSoDeUmPedidoOuNaOnze(
            @ForAll("formatos") Formato formato, @ForAll long seed, @ForAll long sementeDasEscolhas) {
        for (Passo passo : jogarAteOFim(formato, seed, sementeDasEscolhas)) {
            FaseDaRodada fase = passo.antes().rodada().fase();
            for (JogadorId jogador : formato.jogadores()) {
                boolean podeCorrer = MOTOR.acoesLegais(passo.antes(), jogador).contains(new Correr());
                boolean deveCorrer = switch (fase) {
                    case FaseDaRodada.AguardandoRespostaDeAumento resposta ->
                        resposta.respondedor().equals(jogador);
                    case FaseDaRodada.DecisaoRodadaDeOnze decisao ->
                        decisao.jogador().equals(jogador);
                    default -> false;
                };
                assertThat(podeCorrer)
                        .as("%s pode correr na fase %s", jogador, fase)
                        .isEqualTo(deveCorrer);
            }
        }
    }

    @Property(tries = 100)
    @Label("RG-2X2-8 e RG-2X2-9: só a dupla que recebe o pedido pode querer ver as cartas, cada um uma vez, e nunca no"
            + " 1x1")
    void quererVerAsCartasSoDaDuplaQueResponde(
            @ForAll("formatos") Formato formato, @ForAll long seed, @ForAll long sementeDasEscolhas) {
        for (Passo passo : jogarAteOFim(formato, seed, sementeDasEscolhas)) {
            EstadoDaPartida antes = passo.antes();
            ConfiguracaoDaPartida configuracao = antes.configuracao();
            for (JogadorId jogador : formato.jogadores()) {
                boolean pode = MOTOR.acoesLegais(antes, jogador).contains(new VerCartasDoParceiro());
                boolean deve = antes.rodada().fase() instanceof FaseDaRodada.AguardandoRespostaDeAumento resposta
                        && !configuracao.parceirosDe(jogador).isEmpty()
                        && configuracao.equipeDe(resposta.respondedor()).equals(configuracao.equipeDe(jogador))
                        && !antes.rodada()
                                .aposta()
                                .pedidoPendente()
                                .orElseThrow()
                                .queremVerAsCartas()
                                .contains(jogador);
                assertThat(pode)
                        .as(
                                "%s pode querer ver as cartas em %s",
                                jogador, antes.rodada().fase())
                        .isEqualTo(deve);
            }
        }
    }

    @Property(tries = 100)
    @Label("RG-VIS-1 a RG-VIS-3 e RG-2X2-15: em partidas inteiras, nada vaza, inclusive a própria mão na Escurinho, a"
            + " mão do parceiro só aparece quando a dupla se vê, e as 40 cartas se conservam")
    void nadaVazaEmPartidasInteiras(
            @ForAll("formatos") Formato formato, @ForAll long seed, @ForAll long sementeDasEscolhas) {
        for (Passo passo : jogarAteOFim(formato, seed, sementeDasEscolhas)) {
            conferirConservacao(passo);
            conferirQueNadaVaza(formato, passo);
        }
    }

    @Provide
    Arbitrary<Formato> formatos() {
        return Arbitraries.of(UM_CONTRA_UM, DUPLAS);
    }

    /** Qual das ações legais cada jogador escolhe, passo a passo (o resto da divisão pelo número de ações legais). */
    @Provide
    Arbitrary<List<Integer>> escolhas() {
        return Arbitraries.integers().between(0, 99).list().ofMaxSize(80);
    }

    private static void conferirConservacao(Passo passo) {
        assertThat(CartasPresentes.naRodada(passo.antes().rodada())).containsExactlyInAnyOrderElementsOf(BARALHO);
        assertThat(CartasPresentes.naRodada(passo.aplicada().novoEstado().rodada()))
                .containsExactlyInAnyOrderElementsOf(BARALHO);
    }

    private static void conferirQueNadaVaza(Formato formato, Passo passo) {
        EstadoDaPartida depois = passo.aplicada().novoEstado();
        boolean mesmaRodada = depois.numeroDaRodada() == passo.antes().numeroDaRodada();
        for (JogadorId jogador : formato.jogadores()) {
            VisaoDoJogador visao = MOTOR.visaoDe(depois, jogador);
            // A mão do parceiro aparece exatamente nos momentos em que a dupla se vê (RG-2X2-10, RG-2X2-11).
            assertThat(visao.maosDosParceiros().keySet())
                    .as("os parceiros que %s vê", jogador)
                    .isEqualTo(parceirosVisiveis(jogador, depois));
            // A carta da vez no descarte é pública (sai da vira e das descartadas); o que não pode vazar é quem a
            // tem. Ela é conferida à parte, e o resto da visão passa pela varredura.
            Optional<Carta> cartaDaVez = depois.rodada().fase() instanceof AguardandoDescarte(Carta carta)
                    ? Optional.of(carta)
                    : Optional.empty();
            assertThat(visao.cartaDaVezNoDescarte()).isEqualTo(cartaDaVez);
            assertNaoMostra(semCartaDaVez(visao), ocultasPara(jogador, depois), "a visão de " + jogador);

            // Os eventos até RodadaIniciada falam da rodada que terminou; os seguintes, da rodada nova.
            List<Evento> eventos = passo.aplicada().eventos().stream()
                    .filter(evento -> evento.visivelPara(jogador))
                    .toList();
            int inicioDaNovaRodada = IntStream.range(0, eventos.size())
                    .filter(i -> eventos.get(i) instanceof RodadaIniciada)
                    .findFirst()
                    .orElse(eventos.size());
            Set<Carta> ocultasAntes = ocultasPara(jogador, passo.antes());
            ocultasAntes.removeAll(cartasReveladas(passo));
            if (mesmaRodada) {
                // A carta comprada no descarte passa a ser do próprio jogador, que pode vê-la (RG-DESC-8), e a mão do
                // parceiro, se a dupla passou a se ver neste passo, também.
                List<Carta> compradas = new ArrayList<>(depois.rodada().maoDe(jogador));
                compradas.removeAll(passo.antes().rodada().maoDe(jogador));
                ocultasAntes.removeAll(compradas);
                parceirosVisiveis(jogador, depois)
                        .forEach(parceiro ->
                                ocultasAntes.removeAll(depois.rodada().maoDe(parceiro)));
            }
            assertNaoMostra(eventos.subList(0, inicioDaNovaRodada), ocultasAntes, "os eventos de " + jogador);
            assertNaoMostra(
                    eventos.subList(inicioDaNovaRodada, eventos.size()),
                    ocultasPara(jogador, depois),
                    "os eventos de " + jogador);
        }
    }

    /**
     * As cartas que o passo tornou públicas: a jogada aberta e as descartadas (RG-DESC-8). A encoberta não se revela
     * (RG-ENC-3), e um descarte pode se resolver na resposta de outro jogador (RG-DESC-10).
     */
    private static Set<Carta> cartasReveladas(Passo passo) {
        Set<Carta> reveladas = new HashSet<>();
        if (passo.acao() instanceof JogarCarta(int indiceNaMao)) {
            reveladas.add(passo.antes().rodada().maoDe(passo.jogador()).get(indiceNaMao));
        }
        EstadoDaPartida depois = passo.aplicada().novoEstado();
        if (depois.numeroDaRodada() == passo.antes().numeroDaRodada()) {
            reveladas.addAll(depois.rodada().descarte().descartadas());
        }
        return reveladas;
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

    private static void assertNaoMostra(Object objeto, Set<Carta> ocultas, String onde) {
        for (Carta oculta : ocultas) {
            assertThat(CartasPresentes.mostra(objeto, oculta))
                    .as("%s mostra %s", onde, oculta)
                    .isFalse();
        }
    }

    /** Joga até acabarem as escolhas ou a partida. */
    private static List<Passo> jogar(Formato formato, long seed, List<Integer> escolhas) {
        EstadoDaPartida estado = MOTOR.novaPartida(formato.configuracao().apply(seed));
        List<Passo> passos = new ArrayList<>();
        for (int escolha : escolhas) {
            if (estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada) {
                break;
            }
            Passo passo = passo(estado, escolha);
            passos.add(passo);
            estado = passo.aplicada().novoEstado();
        }
        return passos;
    }

    /** Joga a partida inteira, escolhendo as ações com um gerador de semente fixa; falha se passar do limite. */
    private static List<Passo> jogarAteOFim(Formato formato, long seed, long sementeDasEscolhas) {
        Random escolhas = new Random(sementeDasEscolhas);
        EstadoDaPartida estado = MOTOR.novaPartida(formato.configuracao().apply(seed));
        List<Passo> passos = new ArrayList<>();
        while (!(estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada)) {
            assertThat(passos).as("a partida deveria terminar").hasSizeLessThan(LIMITE_DE_PASSOS);
            Passo passo = passo(estado, escolhas.nextInt(100));
            passos.add(passo);
            estado = passo.aplicada().novoEstado();
        }
        return passos;
    }

    private static Passo passo(EstadoDaPartida estado, int escolha) {
        JogadorId jogador = daVez(estado);
        List<Acao> legais = MOTOR.acoesLegais(estado, jogador);
        Acao acao = legais.get(escolha % legais.size());
        return new Passo(estado, jogador, acao, aplicarAceita(estado, jogador, acao));
    }
}
