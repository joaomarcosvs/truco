package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.CartasPresentes.mostra;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.ACAO_INVALIDA;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.JogarEncoberta;
import io.github.joaomarcosvs.truco.evento.CartaEncobertaJogada;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Empatada;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Vencida;
import io.github.joaomarcosvs.truco.visao.JogadaVisivel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Carta encoberta com vira 7♦ (manilha 10). Ana dá as cartas e Beto abre; Beto vence a 1ª vaza com 3♠ contra 4♣ e
 * abre a 2ª. Nos exemplos da seção 13, X é Beto e Y é Ana.
 */
class EncobertaTest {

    private static MesaDeTeste mesa() {
        return new MesaDeTeste(Cenario.comVira("7♦")
                .mao(BETO, "3♠ 6♣ 5♦")
                .mao(ANA, "4♣ A♠ 10♣")
                .montar());
    }

    @Test
    @DisplayName("RG-ENC-1: na 1ª vaza nenhuma carta pode ser encoberta; a partir da 2ª, pode")
    void soAPartirDaSegundaVaza() {
        MesaDeTeste mesa = mesa();
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).noneMatch(JogarEncoberta.class::isInstance);
        assertThat(MOTOR.aplicar(mesa.estado(), BETO, new JogarEncoberta(0))).isEqualTo(new Rejeitada(ACAO_INVALIDA));

        mesa.jogar(BETO, "3♠");
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).noneMatch(JogarEncoberta.class::isInstance);

        mesa.jogar(ANA, "4♣");
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).contains(new JogarEncoberta(0), new JogarEncoberta(1));
    }

    @Test
    @DisplayName("RG-ENC-2: na 2ª vaza, X joga 6♣ aberta e Y joga encoberta: X vence a vaza")
    void abertaVenceEncoberta() {
        MesaDeTeste mesa = mesa().jogar(BETO, "3♠").jogar(ANA, "4♣");

        mesa.jogar(BETO, "6♣").jogarEncoberta(ANA, "A♠");

        assertThat(mesa.eventos(CartaEncobertaJogada.class)).containsExactly(new CartaEncobertaJogada(ANA, 2));
        assertThat(mesa.eventos(VazaFinalizada.class))
                .last()
                .isEqualTo(new VazaFinalizada(2, new Vencida(BETO, EQUIPE_DO_BETO)));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
    }

    @Test
    @DisplayName("RG-ENC-2: se quem abre encobre, a carta aberta do outro vence, e ele abre a vaza seguinte")
    void quemAbreEncobreEPerde() {
        MesaDeTeste mesa = mesa().jogar(BETO, "3♠").jogar(ANA, "4♣");

        mesa.jogarEncoberta(BETO, "5♦").jogar(ANA, "A♠");

        assertThat(mesa.eventos(VazaFinalizada.class))
                .last()
                .isEqualTo(new VazaFinalizada(2, new Vencida(ANA, EQUIPE_DA_ANA)));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new FaseDaRodada.AguardandoJogada(ANA));
    }

    @Test
    @DisplayName("RG-ENC-4: se as duas cartas da vaza são encobertas, a vaza empata")
    void duasEncobertasEmpatam() {
        MesaDeTeste mesa = mesa().jogar(BETO, "3♠").jogar(ANA, "4♣");

        mesa.jogarEncoberta(BETO, "5♦").jogarEncoberta(ANA, "A♠");

        // Beto venceu a 1ª e a 2ª empatou: Beto vence a rodada (RG-EMP-2).
        assertThat(mesa.eventos(VazaFinalizada.class)).last().isEqualTo(new VazaFinalizada(2, new Empatada()));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
    }

    @Test
    @DisplayName("RG-ENC-3: o adversário só sabe que uma carta foi encoberta, nem durante nem depois da rodada")
    void identidadeNuncaRevelada() {
        MesaDeTeste mesa = mesa().jogar(BETO, "3♠").jogar(ANA, "4♣");

        mesa.jogarEncoberta(BETO, "5♦");

        assertThat(MOTOR.visaoDe(mesa.estado(), ANA).vazaAtual())
                .containsExactly(new JogadaVisivel(BETO, Optional.empty(), true));
        assertThat(MOTOR.visaoDe(mesa.estado(), BETO).vazaAtual())
                .containsExactly(new JogadaVisivel(BETO, Optional.of(carta("5♦")), true));

        mesa.jogar(ANA, "A♠"); // Ana vence a 2ª e abre a 3ª

        assertThat(MOTOR.visaoDe(mesa.estado(), ANA).vazas().getLast().jogadas())
                .contains(new JogadaVisivel(BETO, Optional.empty(), true));

        mesa.jogar(ANA, "10♣").jogar(BETO, "6♣"); // Ana vence a 3ª e a rodada

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 1));
        assertThat(mesa.eventos(CartaJogada.class))
                .noneMatch(jogada -> jogada.carta().equals(carta("5♦")));
        assertThat(eventosDaRodadaQueTerminou(mesa).stream().filter(evento -> evento.visivelPara(ANA)))
                .noneMatch(evento -> mostra(evento, carta("5♦")));
    }

    /** Os eventos até o início da rodada seguinte, cuja distribuição nova pode trazer a mesma carta. */
    private static List<Evento> eventosDaRodadaQueTerminou(MesaDeTeste mesa) {
        List<Evento> eventos = mesa.eventos();
        int inicioDaSeguinte =
                eventos.indexOf(mesa.eventos(RodadaIniciada.class).getFirst());
        return eventos.subList(0, inicioDaSeguinte);
    }
}
