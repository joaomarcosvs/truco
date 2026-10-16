package io.github.joaomarcosvs.truco.bots;

import static io.github.joaomarcosvs.truco.bots.Partidas.ANA;
import static io.github.joaomarcosvs.truco.bots.Partidas.BETO;
import static io.github.joaomarcosvs.truco.bots.Partidas.CARLA;
import static io.github.joaomarcosvs.truco.bots.Partidas.DAVI;
import static io.github.joaomarcosvs.truco.bots.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.bots.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.bots.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.bots.Partidas.configuracao;
import static io.github.joaomarcosvs.truco.bots.Partidas.configuracaoEmDuplas;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.acao.VerCartasDoParceiro;
import io.github.joaomarcosvs.truco.evento.DuplaViuAsCartas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /**
     * Roteiro para o 2x2: pede aumento quando a rodada vale 1; quem recebe o pedido quer ver as cartas e depois aceita;
     * consultado fora da vez, só quer ver se o parceiro já quis. Fora isso, a primeira ação legal.
     */
    private record Roteiro(JogadorId jogador, List<String> registro) implements Estrategia {

        @Override
        public Acao decidir(VisaoDoJogador visao, List<Acao> legais) {
            if (legais.contains(new VerCartasDoParceiro())) {
                registro.add(jogador + " quer ver");
                return new VerCartasDoParceiro();
            }
            if (legais.contains(new Aceitar())) {
                registro.add(jogador + " aceita");
                return new Aceitar();
            }
            if (legais.contains(new PedirAumento()) && visao.aposta().valor() == 1) {
                return new PedirAumento();
            }
            return legais.getFirst();
        }

        @Override
        public Optional<Acao> decidirOpcional(VisaoDoJogador visao, List<Acao> legais) {
            boolean parceiroQuis = visao.aposta()
                    .pedidoPendente()
                    .filter(pedido -> !pedido.queremVerAsCartas().isEmpty())
                    .isPresent();
            registro.add(jogador + (parceiroQuis ? " também quer ver" : " não quer ver"));
            return parceiroQuis ? Optional.of(new VerCartasDoParceiro()) : Optional.empty();
        }
    }

    @Test
    @DisplayName("RG-2X2-9: o parceiro de quem recebe o pedido é consultado antes, pode não querer ver as cartas e é"
            + " consultado de novo quando quem recebeu passa a querer")
    void parceiroConsultadoNoAumento() {
        List<String> registro = new ArrayList<>();
        Map<JogadorId, Estrategia> roteiros = new HashMap<>();
        for (JogadorId jogador : List.of(ANA, BETO, CARLA, DAVI)) {
            roteiros.put(jogador, new Roteiro(jogador, registro));
        }
        List<Evento> eventos = new ArrayList<>();

        new Arbitro(MOTOR, roteiros)
                .jogar(MOTOR.novaPartida(configuracaoEmDuplas(2026)), (antes, jogador, acao, aplicada) -> {
                    if (antes.numeroDaRodada() == 1) {
                        eventos.addAll(aplicada.eventos());
                    }
                });

        // Na seed 2026, Carla dá as cartas, Davi abre e pede truco, e quem recebe é a Ana.
        assertThat(registro.subList(0, 4))
                .containsExactly("carla não quer ver", "ana quer ver", "carla também quer ver", "ana aceita");
        assertThat(eventos).contains(new DuplaViuAsCartas(EQUIPE_DA_ANA));
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
