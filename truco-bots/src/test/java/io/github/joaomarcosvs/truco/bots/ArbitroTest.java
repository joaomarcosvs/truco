package io.github.joaomarcosvs.truco.bots;

import static io.github.joaomarcosvs.truco.bots.Partidas.ANA;
import static io.github.joaomarcosvs.truco.bots.Partidas.BETO;
import static io.github.joaomarcosvs.truco.bots.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.bots.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.bots.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.bots.Partidas.configuracao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArbitroTest {

    /** Um passo visto pelo observador. */
    private record Passo(EstadoDaPartida antes, JogadorId jogador, Acao acao, Aplicada aplicada) {}

    /** Uma consulta a uma estratégia: quem foi consultado, de quem era a visão e quais eram as ações legais. */
    private record Consulta(JogadorId consultado, JogadorId donoDaVisao, List<Acao> legais) {}

    /** Estratégia que registra cada consulta e delega a decisão a um bot aleatório. */
    private static Estrategia registrando(JogadorId jogador, long seed, List<Consulta> consultas) {
        BotAleatorio bot = new BotAleatorio(seed);
        return (visao, legais) -> {
            consultas.add(new Consulta(jogador, visao.jogador(), legais));
            return bot.decidir(visao, legais);
        };
    }

    @Test
    @DisplayName("RG-FIM-1: joga a partida até o fim e avisa o observador de cada ação, em sequência")
    void jogaAteOFim() {
        EstadoDaPartida inicio = MOTOR.novaPartida(configuracao(2026));
        List<Passo> passos = new ArrayList<>();
        Arbitro arbitro = new Arbitro(MOTOR, Map.of(ANA, new BotAleatorio(1), BETO, new BotAleatorio(2)));

        EstadoDaPartida fim = arbitro.jogar(
                inicio, (antes, jogador, acao, aplicada) -> passos.add(new Passo(antes, jogador, acao, aplicada)));

        assertThat(fim.rodada().fase()).isInstanceOf(FaseDaRodada.PartidaFinalizada.class);
        assertThat(passos.getFirst().antes()).isEqualTo(inicio);
        for (int i = 1; i < passos.size(); i++) {
            assertThat(passos.get(i).antes())
                    .isEqualTo(passos.get(i - 1).aplicada().novoEstado());
        }
        assertThat(passos.getLast().aplicada().novoEstado()).isEqualTo(fim);
    }

    @Test
    @DisplayName("Cada estratégia é consultada só na sua vez, com a própria visão e as próprias ações legais")
    void consultaComAPropriaVisao() {
        EstadoDaPartida inicio = MOTOR.novaPartida(configuracao(2026));
        List<Consulta> consultas = new ArrayList<>();
        List<Passo> passos = new ArrayList<>();
        Arbitro arbitro =
                new Arbitro(MOTOR, Map.of(ANA, registrando(ANA, 1, consultas), BETO, registrando(BETO, 2, consultas)));

        arbitro.jogar(
                inicio, (antes, jogador, acao, aplicada) -> passos.add(new Passo(antes, jogador, acao, aplicada)));

        assertThat(consultas).hasSameSizeAs(passos);
        for (int i = 0; i < passos.size(); i++) {
            Passo passo = passos.get(i);
            Consulta consulta = consultas.get(i);
            assertThat(consulta.consultado()).isEqualTo(passo.jogador());
            assertThat(consulta.donoDaVisao()).isEqualTo(passo.jogador());
            assertThat(consulta.legais()).isEqualTo(MOTOR.acoesLegais(passo.antes(), passo.jogador()));
        }
    }

    @Test
    @DisplayName("RG-DESC-10: no descarte, todos os jogadores respondem, um de cada vez, na ordem da mesa")
    void todosRespondemNoDescarte() {
        EstadoDaPartida inicio = MOTOR.novaPartida(configuracao(2026));
        assertThat(inicio.rodada().fase()).isInstanceOf(FaseDaRodada.AguardandoDescarte.class);
        List<Consulta> consultas = new ArrayList<>();
        Arbitro arbitro =
                new Arbitro(MOTOR, Map.of(ANA, registrando(ANA, 1, consultas), BETO, registrando(BETO, 2, consultas)));

        arbitro.jogar(inicio, (antes, jogador, acao, aplicada) -> {});

        // Na seed 2026, ninguém tem o 4♦, a primeira carta da sequência: os dois só podem recusar.
        assertThat(consultas.subList(0, 2))
                .containsExactly(
                        new Consulta(ANA, ANA, List.of(new RecusarDescarte())),
                        new Consulta(BETO, BETO, List.of(new RecusarDescarte())));
    }

    @Test
    @DisplayName("Determinismo: seed 2026 com BotAleatorio(1) para Ana e BotAleatorio(2) para Beto dá sempre o mesmo"
            + " fim")
    void fimFixo() {
        Arbitro arbitro = new Arbitro(MOTOR, Map.of(ANA, new BotAleatorio(1), BETO, new BotAleatorio(2)));

        EstadoDaPartida fim =
                arbitro.jogar(MOTOR.novaPartida(configuracao(2026)), (antes, jogador, acao, aplicada) -> {});

        assertThat("rodada %d: ana %d x %d beto"
                        .formatted(
                                fim.numeroDaRodada(),
                                fim.placar().pontosDe(EQUIPE_DA_ANA),
                                fim.placar().pontosDe(EQUIPE_DO_BETO)))
                .isEqualTo("rodada 5: ana 4 x 19 beto");
    }

    @Test
    @DisplayName("Estratégia que escolhe uma ação fora das legais é um bug: o árbitro para com erro")
    void estrategiaComAcaoIlegal() {
        Estrategia trapaceira = (visao, legais) -> new JogarCarta(99);
        Arbitro arbitro = new Arbitro(MOTOR, Map.of(ANA, trapaceira, BETO, trapaceira));

        assertThatIllegalStateException()
                .isThrownBy(() -> arbitro.jogar(MOTOR.novaPartida(configuracao(2026)), (antes, j, a, ap) -> {}))
                .withMessageContaining("fora das ações legais");
    }

    @Test
    @DisplayName("Todo jogador da partida precisa de uma estratégia")
    void jogadorSemEstrategia() {
        Arbitro arbitro = new Arbitro(MOTOR, Map.of(ANA, new BotAleatorio(1)));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> arbitro.jogar(MOTOR.novaPartida(configuracao(2026)), (antes, j, a, ap) -> {}))
                .withMessageContaining("beto");
    }
}
