package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.aplicarAceita;
import static io.github.joaomarcosvs.truco.partida.Partidas.daVez;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.regras.manilhasfixas.ManilhasFixas;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste de prontidão (CLAUDE.md, seção 7, marco M10): o motor, sem nenhuma mudança, joga a variante de manilhas fixas.
 * Ela não tem vira nem descarte, e as manilhas não dependem da rodada.
 */
class ProntidaoDeVarianteTest {

    private static final List<Carta> BARALHO =
            new ManilhasFixas().composicaoDoBaralho().cartas();

    private static ConfiguracaoDaPartida configuracao(long seed) {
        return new ConfiguracaoDaPartida(
                List.of(Equipe.individual(ANA), Equipe.individual(BETO)), new ManilhasFixas(), seed);
    }

    @Test
    @DisplayName("M10: a rodada começa sem vira e sem descarte, direto na 1ª vaza, com as 40 cartas")
    void semViraNemDescarte() {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao(2026));
        Rodada rodada = estado.rodada();

        assertThat(rodada.vira()).isEmpty();
        assertThat(rodada.descarte().encerrado()).isTrue();
        assertThat(rodada.fase())
                .isEqualTo(
                        new FaseDaRodada.AguardandoJogada(estado.configuracao().aDireitaDe(estado.carteador())));
        assertThat(rodada.maoDe(ANA)).hasSize(3);
        assertThat(rodada.maoDe(BETO)).hasSize(3);
        assertThat(rodada.baralhoRestante()).hasSize(34);
        assertThat(CartasPresentes.naRodada(rodada)).containsExactlyInAnyOrderElementsOf(BARALHO);
        assertThat(MOTOR.visaoDe(estado, ANA).vira()).isEmpty();
    }

    @Test
    @DisplayName("M10: as manilhas fixas decidem as vazas: o zap (4♣) vence o 3, e o 7♦ vence o 5")
    void manilhasDecidemAsVazas() {
        // Ana dá as cartas; Beto abre.
        MesaDeTeste mesa = new MesaDeTeste(Cenario.semVira(new ManilhasFixas())
                .mao(BETO, "3♠ 5♦ 6♥")
                .mao(ANA, "4♣ 7♦ A♥")
                .montar());

        mesa.jogar(BETO, "3♠").jogar(ANA, "4♣").jogar(ANA, "7♦").jogar(BETO, "5♦");

        assertThat(mesa.eventos(VazaFinalizada.class))
                .containsExactly(
                        new VazaFinalizada(1, new ResultadoDaVaza.Vencida(ANA, EQUIPE_DA_ANA)),
                        new VazaFinalizada(2, new ResultadoDaVaza.Vencida(ANA, EQUIPE_DA_ANA)));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 1));
    }

    @Test
    @DisplayName("M10: entre manilhas, a mais forte vence: 7♥ contra A♠")
    void entreManilhas() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.semVira(new ManilhasFixas())
                .mao(BETO, "A♠ 5♦ 6♥")
                .mao(ANA, "7♥ 7♣ A♥")
                .montar());

        mesa.jogar(BETO, "A♠").jogar(ANA, "7♥");

        assertThat(mesa.eventos(VazaFinalizada.class))
                .containsExactly(new VazaFinalizada(1, new ResultadoDaVaza.Vencida(ANA, EQUIPE_DA_ANA)));
    }

    @Test
    @DisplayName("M10: as rodadas seguintes também começam sem vira")
    void rodadasSeguintesSemVira() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.semVira(new ManilhasFixas())
                .mao(BETO, "3♠ 5♦ 6♥")
                .mao(ANA, "4♣ 7♦ A♥")
                .montar());

        mesa.pedirAumento(BETO).correr(ANA);

        assertThat(mesa.eventos(RodadaIniciada.class))
                .containsExactly(new RodadaIniciada(2, BETO, 1, Optional.empty()));
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DO_BETO)).isEqualTo(1);
        assertThat(mesa.estado().rodada().vira()).isEmpty();
    }

    @Property(tries = 200)
    @Label("M10: partidas inteiras com escolhas aleatórias terminam, sem vira nem descarte, com as 40 cartas, com só"
            + " ações legais aceitas e sem correr na própria vez")
    void partidasInteiras(@ForAll long seed, @ForAll long sementeDasEscolhas) {
        Random escolhas = new Random(sementeDasEscolhas);
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao(seed));
        List<Evento> eventos = new ArrayList<>();
        for (int passo = 0; !(estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada); passo++) {
            assertThat(passo).as("a partida deveria terminar").isLessThan(5_000);
            assertThat(estado.rodada().vira()).isEmpty();
            assertThat(estado.rodada().fase()).isNotInstanceOf(FaseDaRodada.AguardandoDescarte.class);
            assertThat(CartasPresentes.naRodada(estado.rodada())).containsExactlyInAnyOrderElementsOf(BARALHO);
            JogadorId jogador = daVez(estado);
            List<Acao> legais = MOTOR.acoesLegais(estado, jogador);
            if (estado.rodada().fase() instanceof FaseDaRodada.AguardandoJogada) {
                assertThat(legais).doesNotContain(new Correr()); // RG-AUM-6
            }
            Aplicada aplicada = aplicarAceita(estado, jogador, legais.get(escolhas.nextInt(legais.size())));
            eventos.addAll(aplicada.eventos());
            estado = aplicada.novoEstado();
        }

        EquipeId vencedora = ((FaseDaRodada.PartidaFinalizada) estado.rodada().fase()).vencedora();
        assertThat(estado.placar().pontosDe(vencedora)).isGreaterThanOrEqualTo(12);
        assertThat(eventos)
                .filteredOn(RodadaIniciada.class::isInstance)
                .allMatch(evento -> ((RodadaIniciada) evento).vira().isEmpty());
    }
}
