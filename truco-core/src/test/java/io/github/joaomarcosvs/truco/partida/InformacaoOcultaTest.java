package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.CartasPresentes.mostra;
import static io.github.joaomarcosvs.truco.partida.CartasPresentes.ocultasPara;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;

import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InformacaoOcultaTest {

    private final EstadoDaPartida estado = MOTOR.novaPartida(configuracao(2026));

    @Test
    @DisplayName("RG-VIS-1: o jogador vê a própria mão, a vira, o placar, a rodada e de quem é a vez")
    void oQueOJogadorVe() {
        VisaoDoJogador visao = MOTOR.visaoDe(estado, ANA);

        assertThat(visao.jogador()).isEqualTo(ANA);
        assertThat(visao.mao()).isEqualTo(estado.rodada().maoDe(ANA));
        assertThat(visao.vira()).isEqualTo(estado.rodada().vira());
        assertThat(visao.placar()).isEqualTo(estado.placar());
        assertThat(visao.numeroDaRodada()).isEqualTo(1);
        assertThat(visao.carteador()).isEqualTo(estado.carteador());
        assertThat(visao.valorDaRodada()).isEqualTo(1);
        assertThat(visao.vezDe()).contains(estado.configuracao().aDireitaDe(estado.carteador()));
        assertThat(visao.cartasNaMao()).containsOnly(entry(ANA, 3), entry(BETO, 3));
        assertThat(visao.jogadoresNaOrdemDaMesa()).containsExactly(ANA, BETO);
    }

    @Test
    @DisplayName("RG-VIS-2: a visão de um jogador não mostra as cartas do outro nem as do baralho")
    void visaoNaoMostraCartasOcultas() {
        for (JogadorId jogador : List.of(ANA, BETO)) {
            VisaoDoJogador visao = MOTOR.visaoDe(estado, jogador);

            assertThat(ocultasPara(jogador, estado.rodada())).hasSize(3 + 33).noneMatch(carta -> mostra(visao, carta));
        }
    }

    @Test
    @DisplayName("RG-VIS-1: as cartas jogadas ficam visíveis para todos")
    void cartasJogadasSaoPublicas() {
        MesaDeTeste mesa = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "3♠ A♥ 5♦").mao(ANA, "4♣ A♠ 6♦").montar());

        mesa.jogar(BETO, "3♠");

        VisaoDoJogador daAna = MOTOR.visaoDe(mesa.estado(), ANA);
        assertThat(daAna.vazaAtual()).containsExactly(new Jogada(BETO, carta("3♠")));
        assertThat(daAna.cartasNaMao()).containsEntry(BETO, 2);
        assertThat(mesa.eventos()).allMatch(evento -> evento.visivelPara(ANA) && evento.visivelPara(BETO));
    }

    @Test
    @DisplayName("RG-VIS-2: as cartas distribuídas só são visíveis para quem as recebeu")
    void cartasDistribuidasSaoPrivadas() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .mao(BETO, "10♣ 3♠ 4♦")
                .mao(ANA, "A♠ 2♥ 5♣")
                .montar());

        mesa.jogar(BETO, "10♣").jogar(ANA, "5♣").jogar(BETO, "3♠").jogar(ANA, "2♥");

        assertThat(mesa.eventos(CartasDistribuidas.class)).hasSize(2).allSatisfy(evento -> {
            JogadorId outro = evento.jogador().equals(ANA) ? BETO : ANA;
            assertThat(evento.visivelPara(evento.jogador())).isTrue();
            assertThat(evento.visivelPara(outro)).isFalse();
        });
        assertThat(mesa.eventos())
                .filteredOn(evento -> !(evento instanceof CartasDistribuidas))
                .allMatch(evento -> evento.visivelPara(ANA) && evento.visivelPara(BETO));
    }

    @Test
    @DisplayName("Pedir a visão de quem não está na partida é erro de programação")
    void visaoDeQuemNaoJoga() {
        assertThatIllegalArgumentException().isThrownBy(() -> MOTOR.visaoDe(estado, new JogadorId("zeca")));
    }
}
