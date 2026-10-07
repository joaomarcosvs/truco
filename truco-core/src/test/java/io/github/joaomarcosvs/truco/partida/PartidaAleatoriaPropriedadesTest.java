package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.CartasPresentes.ocultasPara;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.aplicarAceita;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracao;
import static io.github.joaomarcosvs.truco.partida.Partidas.daVez;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Partidas com escolhas aleatórias entre as ações legais, conferindo os invariantes do core a cada passo. */
class PartidaAleatoriaPropriedadesTest {

    private static final List<Carta> BARALHO =
            new TrucoPaulista().composicaoDoBaralho().cartas();

    /** Um passo da partida: o estado antes, quem agiu, a ação e o que o motor devolveu. */
    private record Passo(EstadoDaPartida antes, JogadorId jogador, Acao acao, Aplicada aplicada) {}

    @Property
    @Label("Conservação: mãos, baralho restante, vira e cartas jogadas somam sempre as 40 cartas, sem repetição")
    void conservacaoDasCartas(@ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        for (Passo passo : jogar(seed, escolhas)) {
            assertThat(CartasPresentes.naRodada(passo.antes().rodada())).containsExactlyInAnyOrderElementsOf(BARALHO);
            assertThat(CartasPresentes.naRodada(passo.aplicada().novoEstado().rodada()))
                    .containsExactlyInAnyOrderElementsOf(BARALHO);
        }
    }

    @Property
    @Label("Toda ação de acoesLegais é aceita, e toda ação fora dela é rejeitada")
    void legaisAceitasEIlegaisRejeitadas(@ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        for (Passo passo : jogar(seed, escolhas)) {
            for (JogadorId jogador : List.of(ANA, BETO)) {
                List<Acao> legais = MOTOR.acoesLegais(passo.antes(), jogador);
                for (int indiceNaMao = -1; indiceNaMao <= 3; indiceNaMao++) {
                    Acao acao = new JogarCarta(indiceNaMao);
                    Resultado resultado = MOTOR.aplicar(passo.antes(), jogador, acao);
                    assertThat(resultado)
                            .as("%s tentando %s", jogador, acao)
                            .isInstanceOf(legais.contains(acao) ? Aplicada.class : Rejeitada.class);
                }
            }
        }
    }

    @Property
    @Label("RG-VIS-1 e RG-VIS-2: visão e eventos de um jogador nunca mostram as cartas do outro nem as do baralho")
    void nadaVaza(@ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        for (Passo passo : jogar(seed, escolhas)) {
            EstadoDaPartida depois = passo.aplicada().novoEstado();
            for (JogadorId jogador : List.of(ANA, BETO)) {
                VisaoDoJogador visao = MOTOR.visaoDe(depois, jogador);
                assertNaoMostra(visao, ocultasPara(jogador, depois.rodada()), "a visão de " + jogador);

                // Os eventos até RodadaIniciada falam da rodada que terminou; os seguintes, da rodada nova.
                List<Evento> eventos = passo.aplicada().eventos().stream()
                        .filter(evento -> evento.visivelPara(jogador))
                        .toList();
                int inicioDaNovaRodada = IntStream.range(0, eventos.size())
                        .filter(i -> eventos.get(i) instanceof RodadaIniciada)
                        .findFirst()
                        .orElse(eventos.size());
                Set<Carta> ocultasAntes = ocultasPara(jogador, passo.antes().rodada());
                cartaRevelada(passo).ifPresent(ocultasAntes::remove);
                assertNaoMostra(eventos.subList(0, inicioDaNovaRodada), ocultasAntes, "os eventos de " + jogador);
                assertNaoMostra(
                        eventos.subList(inicioDaNovaRodada, eventos.size()),
                        ocultasPara(jogador, depois.rodada()),
                        "os eventos de " + jogador);
            }
        }
    }

    @Property
    @Label("O placar nunca diminui")
    void placarNuncaDiminui(@ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        for (Passo passo : jogar(seed, escolhas)) {
            Placar antes = passo.antes().placar();
            Placar depois = passo.aplicada().novoEstado().placar();
            for (EquipeId equipe : List.of(EQUIPE_DA_ANA, EQUIPE_DO_BETO)) {
                assertThat(depois.pontosDe(equipe)).isGreaterThanOrEqualTo(antes.pontosDe(equipe));
            }
        }
    }

    @Property
    @Label("Determinismo: a mesma seed com as mesmas ações gera a mesma partida, com os mesmos eventos")
    void deterministica(@ForAll long seed, @ForAll("escolhas") List<Integer> escolhas) {
        assertThat(jogar(seed, escolhas)).isEqualTo(jogar(seed, escolhas));
    }

    /** Qual das ações legais cada jogador escolhe, passo a passo. */
    @Provide
    Arbitrary<List<Integer>> escolhas() {
        return Arbitraries.integers().between(0, 2).list().ofMaxSize(80);
    }

    /** A carta que a ação tornou pública. */
    private static Optional<Carta> cartaRevelada(Passo passo) {
        return switch (passo.acao()) {
            case JogarCarta(int indiceNaMao) ->
                Optional.of(passo.antes().rodada().maoDe(passo.jogador()).get(indiceNaMao));
        };
    }

    private static void assertNaoMostra(Object objeto, Set<Carta> ocultas, String onde) {
        for (Carta oculta : ocultas) {
            assertThat(CartasPresentes.mostra(objeto, oculta))
                    .as("%s mostra %s", onde, oculta)
                    .isFalse();
        }
    }

    private static List<Passo> jogar(long seed, List<Integer> escolhas) {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao(seed));
        List<Passo> passos = new ArrayList<>();
        for (int escolha : escolhas) {
            JogadorId jogador = daVez(estado);
            List<Acao> legais = MOTOR.acoesLegais(estado, jogador);
            Acao acao = legais.get(escolha % legais.size());
            Aplicada aplicada = aplicarAceita(estado, jogador, acao);
            passos.add(new Passo(estado, jogador, acao, aplicada));
            estado = aplicada.novoEstado();
        }
        return passos;
    }
}
