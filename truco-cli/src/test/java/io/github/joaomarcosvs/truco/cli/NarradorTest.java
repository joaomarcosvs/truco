package io.github.joaomarcosvs.truco.cli;

import static io.github.joaomarcosvs.truco.cli.Cartas.carta;
import static io.github.joaomarcosvs.truco.cli.Cartas.cartas;
import static io.github.joaomarcosvs.truco.cli.TrucoCli.BETO;
import static io.github.joaomarcosvs.truco.cli.TrucoCli.BOT;
import static io.github.joaomarcosvs.truco.cli.TrucoCli.CARLA;
import static io.github.joaomarcosvs.truco.cli.TrucoCli.DAVI;
import static io.github.joaomarcosvs.truco.cli.TrucoCli.VOCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.evento.AumentoAceito;
import io.github.joaomarcosvs.truco.evento.AumentoPedido;
import io.github.joaomarcosvs.truco.evento.CartaDescartada;
import io.github.joaomarcosvs.truco.evento.CartaEncobertaJogada;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.CartaRecebidaPorDescarte;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.CartasDoParceiroVistas;
import io.github.joaomarcosvs.truco.evento.DescarteEncerrado;
import io.github.joaomarcosvs.truco.evento.DuplaViuAsCartas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.JogadorCorreu;
import io.github.joaomarcosvs.truco.evento.PartidaFinalizada;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.QuisVerAsCartas;
import io.github.joaomarcosvs.truco.evento.RodadaAnulada;
import io.github.joaomarcosvs.truco.evento.RodadaDeOnzeIniciada;
import io.github.joaomarcosvs.truco.evento.RodadaEscurinhoIniciada;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.Placar;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NarradorTest {

    static final EquipeId SUA_EQUIPE = new EquipeId("voce");
    static final EquipeId EQUIPE_DO_BOT = new EquipeId("bot");
    static final ConfiguracaoDaPartida CONFIGURACAO =
            new ConfiguracaoDaPartida(List.of(Equipe.individual(VOCE), Equipe.individual(BOT)), new TrucoPaulista(), 0);

    static Narrador narrador(Notacao notacao) {
        return new Narrador(VOCE, Map.of(VOCE, "você", BOT, "bot"), CONFIGURACAO, notacao);
    }

    static Placar placar(int seus, int doBot) {
        return new Placar(Map.of(SUA_EQUIPE, seus, EQUIPE_DO_BOT, doBot));
    }

    static Stream<Arguments> eventos() {
        return Stream.of(
                Arguments.of(
                        new RodadaIniciada(2, BOT, 1, Optional.of(carta("A♠"))),
                        "Rodada 2: bot dá as cartas, vira A♠."),
                Arguments.of(new CartasDistribuidas(VOCE, cartas("5♥ 2♦ J♥")), "Você recebeu 5♥ 2♦ J♥."),
                Arguments.of(new CartaDescartada(BOT, carta("4♦")), "Bot descartou 4♦."),
                Arguments.of(new CartaRecebidaPorDescarte(VOCE, carta("7♥")), "Você comprou 7♥."),
                Arguments.of(new DescarteEncerrado(), "Fim do descarte."),
                Arguments.of(new CartaJogada(BOT, carta("7♣"), 1), "Bot jogou 7♣."),
                Arguments.of(new CartaEncobertaJogada(VOCE, 2), "Você jogou uma carta encoberta."),
                Arguments.of(
                        new VazaFinalizada(1, new ResultadoDaVaza.Vencida(VOCE, SUA_EQUIPE)), "Vaza 1: você venceu."),
                Arguments.of(new VazaFinalizada(2, new ResultadoDaVaza.Empatada()), "Vaza 2: empate."),
                Arguments.of(new AumentoPedido(BOT, 3), "Bot pediu aumento para 3."),
                Arguments.of(new AumentoAceito(VOCE, 6), "Você aceitou: a rodada vale 6."),
                Arguments.of(new JogadorCorreu(BOT), "Bot correu."),
                Arguments.of(new RodadaFinalizada(4, SUA_EQUIPE, 1), "Rodada 4: você marcou 1 ponto."),
                Arguments.of(new RodadaFinalizada(5, EQUIPE_DO_BOT, 3), "Rodada 5: bot marcou 3 pontos."),
                Arguments.of(new RodadaAnulada(6), "Rodada 6 anulada: ninguém marcou ponto."),
                Arguments.of(new PlacarAtualizado(placar(5, 3)), "Placar: você 5 x 3 bot."),
                Arguments.of(
                        new PartidaFinalizada(EQUIPE_DO_BOT, placar(9, 12)), "Fim de partida: bot venceu por 12 a 9."),
                Arguments.of(new RodadaDeOnzeIniciada(SUA_EQUIPE), "Rodada de Onze: você decide se joga ou corre."),
                Arguments.of(
                        new RodadaEscurinhoIniciada(),
                        "Rodada Escurinho: ninguém vê as próprias cartas, e quem vencer a rodada vence a partida."));
    }

    /** No 2x2: você e Carla contra Beto e Davi. */
    static final EquipeId SUA_DUPLA = new EquipeId("voce-carla");

    static final EquipeId DUPLA_ADVERSARIA = new EquipeId("beto-davi");

    static final ConfiguracaoDaPartida DUPLAS = new ConfiguracaoDaPartida(
            List.of(new Equipe(SUA_DUPLA, List.of(VOCE, CARLA)), new Equipe(DUPLA_ADVERSARIA, List.of(BETO, DAVI))),
            new TrucoPaulista(),
            0);

    static Narrador narradorEmDuplas() {
        return new Narrador(
                VOCE, Map.of(VOCE, "você", CARLA, "Carla", BETO, "Beto", DAVI, "Davi"), DUPLAS, Notacao.SIMBOLOS);
    }

    static Stream<Arguments> eventosEmDuplas() {
        return Stream.of(
                Arguments.of(new QuisVerAsCartas(CARLA), "Carla quer ver as cartas do parceiro."),
                Arguments.of(new DuplaViuAsCartas(SUA_DUPLA), "Sua dupla viu as cartas um do outro."),
                Arguments.of(new DuplaViuAsCartas(DUPLA_ADVERSARIA), "Dupla adversária viu as cartas um do outro."),
                Arguments.of(
                        new CartasDoParceiroVistas(VOCE, CARLA, cartas("3♣ Q♣ 10♦")),
                        "Você viu as cartas de Carla: 3♣ Q♣ 10♦."),
                Arguments.of(
                        new RodadaFinalizada(1, DUPLA_ADVERSARIA, 3), "Rodada 1: dupla adversária marcou 3 pontos."),
                Arguments.of(
                        new PlacarAtualizado(new Placar(Map.of(SUA_DUPLA, 0, DUPLA_ADVERSARIA, 3))),
                        "Placar: sua dupla 0 x 3 dupla adversária."));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("eventos")
    @DisplayName("Cada evento vira uma frase do seu ponto de vista")
    void descreve(Evento evento, String frase) {
        assertThat(narrador(Notacao.SIMBOLOS).descrever(evento)).isEqualTo(frase);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("eventosEmDuplas")
    @DisplayName("RG-2X2-9 a RG-2X2-11: no 2x2, as duplas são \"sua dupla\" e \"dupla adversária\", e a dupla se vendo"
            + " vira frase")
    void descreveEmDuplas(Evento evento, String frase) {
        assertThat(narradorEmDuplas().descrever(evento)).isEqualTo(frase);
    }

    @Test
    @DisplayName("Os exemplos cobrem todos os tipos de evento")
    void todosOsEventos() {
        Set<Class<?>> exemplos = Stream.concat(eventos(), eventosEmDuplas())
                .map(argumentos -> argumentos.get()[0].getClass())
                .collect(Collectors.toSet());

        assertThat(exemplos).containsExactlyInAnyOrder(Evento.class.getPermittedSubclasses());
    }

    @Test
    @DisplayName("Com a notação de letras, as cartas aparecem com letras")
    void comLetras() {
        assertThat(narrador(Notacao.LETRAS).descrever(new CartaJogada(BOT, carta("10♠"), 1)))
                .isEqualTo("Bot jogou 10e.");
    }
}
