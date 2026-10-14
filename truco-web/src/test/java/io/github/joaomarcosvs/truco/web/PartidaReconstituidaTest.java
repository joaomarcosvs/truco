package io.github.joaomarcosvs.truco.web;

import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.MOTOR;
import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.VOCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.JogadaInvalida;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Reconstituicao;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Reconstituida;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Refazer a partida pela seed e pelas suas jogadas, como o servidor faz a cada pedido. */
class PartidaReconstituidaTest {

    private static Reconstituida reconstituida(long seed, List<Acao> jogadas) {
        Reconstituicao reconstituicao = PartidaReconstituida.reconstituir(seed, jogadas);
        assertThat(reconstituicao).isInstanceOf(Reconstituida.class);
        return (Reconstituida) reconstituicao;
    }

    @Test
    @DisplayName("Sem jogadas: a partida começa, o bot faz o que vem antes de você, e para na sua primeira decisão")
    void comeco() {
        // Seed 2026 (a mesma do golden da CLI): você dá as cartas, ninguém tem o 4♦ e o bot abre com o 5♥.
        Reconstituida partida = reconstituida(2026, List.of());

        List<Evento> eventos = partida.passos().stream()
                .flatMap(passo -> passo.eventos().stream())
                .toList();
        assertThat(eventos.getFirst()).isInstanceOf(RodadaIniciada.class);
        assertThat(eventos.get(1)).isInstanceOf(CartasDistribuidas.class);
        assertThat(eventos.getLast()).isInstanceOf(CartaJogada.class);
        assertThat(partida.estado().rodada().fase()).isEqualTo(new FaseDaRodada.AguardandoJogada(VOCE));
        assertThat(MOTOR.acoesLegais(partida.estado(), VOCE))
                .containsExactly(new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new PedirAumento());
    }

    @Test
    @DisplayName("Os passos começam na sua última jogada, e a partida é sempre a mesma para a mesma seed e jogadas")
    void passosDesdeASuaUltimaJogada() {
        List<Acao> jogadas = List.of(new JogarCarta(0));

        Reconstituida partida = reconstituida(2026, jogadas);

        assertThat(partida.passos().getFirst().eventos().getFirst()).isInstanceOf(CartaJogada.class);
        assertThat(((CartaJogada) partida.passos().getFirst().eventos().getFirst()).jogador())
                .isEqualTo(VOCE);
        assertThat(reconstituida(2026, jogadas)).isEqualTo(partida);
    }

    @Test
    @DisplayName("Uma jogada que não era legal é recusada, dizendo qual foi")
    void jogadaIlegal() {
        assertThat(PartidaReconstituida.reconstituir(2026, List.of(new JogarCarta(7))))
                .isEqualTo(new JogadaInvalida(0, new JogarCarta(7)));
        // RG-AUM-6: na própria vez não se corre.
        assertThat(PartidaReconstituida.reconstituir(2026, List.of(new JogarCarta(0), new Correr())))
                .isInstanceOf(JogadaInvalida.class);
    }

    @Test
    @DisplayName(
            "RG-FIM-1: jogando sempre a primeira ação legal, a partida chega ao fim; jogadas depois dele são recusadas")
    void partidaInteira() {
        List<Acao> jogadas = new ArrayList<>();
        Reconstituida partida = reconstituida(2026, jogadas);
        while (!(partida.estado().rodada().fase() instanceof FaseDaRodada.PartidaFinalizada)) {
            assertThat(jogadas).hasSizeLessThan(ServidorWeb.MAXIMO_DE_JOGADAS);
            jogadas.add(MOTOR.acoesLegais(partida.estado(), VOCE).getFirst());
            partida = reconstituida(2026, jogadas);
        }

        assertThat(MOTOR.acoesLegais(partida.estado(), VOCE)).isEmpty();
        jogadas.add(new JogarCarta(0));
        assertThat(PartidaReconstituida.reconstituir(2026, jogadas))
                .isEqualTo(new JogadaInvalida(jogadas.size() - 1, new JogarCarta(0)));
    }
}
