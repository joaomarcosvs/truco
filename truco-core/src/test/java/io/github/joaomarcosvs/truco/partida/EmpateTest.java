package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.RodadaAnulada;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoJogada;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Empatada;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Vencida;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Empates com vira 7♦ (manilha 10), como nos exemplos da seção 13. Ana dá as cartas e Beto abre a 1ª vaza. */
class EmpateTest {

    @Test
    @DisplayName("RG-EMP-1: com empate na 1ª vaza (A♥ contra A♠), quem vencer a 2ª vence a rodada")
    void empateNaPrimeira() {
        MesaDeTeste mesa = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "A♥ 3♠ 4♦").mao(ANA, "A♠ 2♥ 5♣").montar());

        mesa.jogar(BETO, "A♥").jogar(ANA, "A♠").jogar(BETO, "3♠").jogar(ANA, "2♥");

        assertThat(resultadosDasVazas(mesa)).containsExactly(new Empatada(), new Vencida(BETO, EQUIPE_DO_BETO));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
    }

    @Test
    @DisplayName("RG-EMP-2: se a 1ª vaza teve vencedor e a 2ª empatou, vence a rodada quem venceu a 1ª")
    void empateNaSegunda() {
        MesaDeTeste mesa = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "3♠ A♥ 4♦").mao(ANA, "2♥ A♠ 5♣").montar());

        mesa.jogar(BETO, "3♠").jogar(ANA, "2♥").jogar(BETO, "A♥").jogar(ANA, "A♠");

        assertThat(resultadosDasVazas(mesa)).containsExactly(new Vencida(BETO, EQUIPE_DO_BETO), new Empatada());
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
    }

    @Test
    @DisplayName("RG-EMP-3: com empate na 1ª e na 2ª vaza, a 3ª decide")
    void empateNasDuasPrimeiras() {
        MesaDeTeste mesa = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "A♥ 2♦ 4♦").mao(ANA, "A♠ 2♣ 5♣").montar());

        mesa.jogar(BETO, "A♥").jogar(ANA, "A♠");
        mesa.jogar(BETO, "2♦").jogar(ANA, "2♣");
        mesa.jogar(BETO, "4♦").jogar(ANA, "5♣");

        assertThat(resultadosDasVazas(mesa))
                .containsExactly(new Empatada(), new Empatada(), new Vencida(ANA, EQUIPE_DA_ANA));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 1));
    }

    @Test
    @DisplayName("RG-EMP-4: com vencedores diferentes na 1ª e na 2ª e empate na 3ª, vence quem venceu a 1ª")
    void empateNaTerceira() {
        MesaDeTeste mesa = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "3♠ 4♦ Q♥").mao(ANA, "2♥ 5♣ Q♠").montar());

        mesa.jogar(BETO, "3♠").jogar(ANA, "2♥"); // Beto vence a 1ª
        mesa.jogar(BETO, "4♦").jogar(ANA, "5♣"); // Ana vence a 2ª e abre a 3ª
        mesa.jogar(ANA, "Q♠").jogar(BETO, "Q♥"); // empate

        assertThat(resultadosDasVazas(mesa))
                .containsExactly(new Vencida(BETO, EQUIPE_DO_BETO), new Vencida(ANA, EQUIPE_DA_ANA), new Empatada());
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
    }

    @Test
    @DisplayName("RG-EMP-5: se as três vazas empatam, a rodada é anulada, ninguém pontua e o próximo carteador dá as"
            + " cartas")
    void tresEmpates() {
        MesaDeTeste mesa = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "A♥ 2♦ Q♥").mao(ANA, "A♠ 2♣ Q♠").montar());

        mesa.jogar(BETO, "A♥").jogar(ANA, "A♠");
        mesa.jogar(BETO, "2♦").jogar(ANA, "2♣");
        mesa.jogar(BETO, "Q♥").jogar(ANA, "Q♠");

        assertThat(resultadosDasVazas(mesa)).containsExactly(new Empatada(), new Empatada(), new Empatada());
        assertThat(mesa.eventos(RodadaAnulada.class)).containsExactly(new RodadaAnulada(1));
        assertThat(mesa.eventos(RodadaFinalizada.class)).isEmpty();
        assertThat(mesa.eventos(PlacarAtualizado.class)).isEmpty();
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DA_ANA)).isZero();
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DO_BETO)).isZero();
        assertThat(mesa.estado().numeroDaRodada()).isEqualTo(2);
        assertThat(mesa.estado().carteador()).isEqualTo(BETO);
    }

    @Test
    @DisplayName("RG-EMP-6: depois de uma vaza empatada, abre a seguinte quem abriu a empatada")
    void quemAbriuOEmpateAbreASeguinte() {
        // Ana dá as cartas: Beto abre a vaza que empata.
        MesaDeTeste betoAbre = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "A♥ 3♠ 4♦").mao(ANA, "A♠ 2♥ 5♣").montar());
        betoAbre.jogar(BETO, "A♥").jogar(ANA, "A♠");

        // Beto dá as cartas: Ana abre a vaza que empata.
        MesaDeTeste anaAbre = new MesaDeTeste(Cenario.comVira("7♦")
                .carteador(BETO)
                .mao(ANA, "A♥ 3♠ 4♦")
                .mao(BETO, "A♠ 2♥ 5♣")
                .montar());
        anaAbre.jogar(ANA, "A♥").jogar(BETO, "A♠");

        assertThat(betoAbre.estado().rodada().fase()).isEqualTo(new AguardandoJogada(BETO));
        assertThat(anaAbre.estado().rodada().fase()).isEqualTo(new AguardandoJogada(ANA));
    }

    private static List<ResultadoDaVaza> resultadosDasVazas(MesaDeTeste mesa) {
        return mesa.eventos(VazaFinalizada.class).stream()
                .map(VazaFinalizada::resultado)
                .toList();
    }
}
