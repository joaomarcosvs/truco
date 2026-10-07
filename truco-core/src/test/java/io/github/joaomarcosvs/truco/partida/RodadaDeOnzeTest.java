package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.ACAO_INVALIDA;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.NAO_E_A_VEZ_DO_JOGADOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.passarDescarte;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.evento.AumentoAceito;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.JogadorCorreu;
import io.github.joaomarcosvs.truco.evento.PartidaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaDeOnzeIniciada;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoDescarte;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoJogada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.DecisaoRodadaDeOnze;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rodada de Onze com vira 7♦ (manilha 10). Ana dá as cartas e Beto abre a 1ª vaza; Beto vence a 1ª (3♠ contra 4♣) e a
 * 2ª empata (A♥ contra A♠). Nos exemplos da seção 13, X tem 11.
 */
class RodadaDeOnzeTest {

    private static Cenario cenario(int pontosDaAna, int pontosDoBeto) {
        return Cenario.comVira("7♦")
                .placar(pontosDaAna, pontosDoBeto)
                .mao(BETO, "3♠ A♥ 5♦")
                .mao(ANA, "4♣ A♠ 6♦");
    }

    private static MesaDeTeste mesa(int pontosDaAna, int pontosDoBeto) {
        return new MesaDeTeste(cenario(pontosDaAna, pontosDoBeto).montar());
    }

    @Test
    @DisplayName("RG-ONZE-1: quando só um lado chega a 11, a rodada seguinte é de Onze e começa pela decisão dele")
    void chegarA11ComecaARodadaDeOnze() {
        // Beto tem 8 e vence a rodada valendo 3.
        MesaDeTeste mesa = mesa(5, 8)
                .pedirAumento(BETO)
                .aceitar(ANA)
                .jogar(BETO, "3♠")
                .jogar(ANA, "4♣")
                .jogar(BETO, "A♥")
                .jogar(ANA, "A♠");

        List<Evento> eventos = mesa.eventos();
        int iniciada = eventos.indexOf(eventos.stream()
                .filter(RodadaIniciada.class::isInstance)
                .findFirst()
                .orElseThrow());
        assertThat(eventos.get(iniciada + 1)).isEqualTo(new RodadaDeOnzeIniciada(EQUIPE_DO_BETO));
        assertThat(mesa.eventos(CartasDistribuidas.class)).hasSize(2);
        EstadoDaPartida estado = mesa.estado();
        assertThat(estado.rodada().tipo()).isEqualTo(new TipoDeRodada.DeOnze(EQUIPE_DO_BETO, false));
        assertThat(MOTOR.visaoDe(estado, ANA).tipoDaRodada()).isEqualTo(new TipoDeRodada.DeOnze(EQUIPE_DO_BETO, false));
        // RG-ONZE-3: o descarte vem antes da decisão.
        assertThat(passarDescarte(estado).rodada().fase()).isEqualTo(new DecisaoRodadaDeOnze(BETO));
    }

    @Test
    @DisplayName("RG-ONZE-1: só quem tem 11 decide, e ninguém joga antes da decisão")
    void soQuemTem11Decide() {
        EstadoDaPartida estado = mesa(8, 11).estado();

        assertThat(estado.rodada().fase()).isEqualTo(new DecisaoRodadaDeOnze(BETO));
        assertThat(MOTOR.acoesLegais(estado, BETO)).containsExactly(new Aceitar(), new Correr());
        assertThat(MOTOR.acoesLegais(estado, ANA)).isEmpty();
        assertThat(MOTOR.aplicar(estado, ANA, new Correr())).isEqualTo(new Rejeitada(NAO_E_A_VEZ_DO_JOGADOR));
        assertThat(MOTOR.aplicar(estado, BETO, new JogarCarta(0))).isEqualTo(new Rejeitada(ACAO_INVALIDA));
        assertThat(MOTOR.aplicar(estado, BETO, new PedirAumento())).isEqualTo(new Rejeitada(ACAO_INVALIDA));
        assertThat(MOTOR.visaoDe(estado, ANA).vezDe()).hasValue(BETO);
        assertThat(MOTOR.visaoDe(estado, BETO).mao()).containsExactly(carta("3♠"), carta("A♥"), carta("5♦"));
    }

    @Test
    @DisplayName("RG-ONZE-1: quem decide é o lado com 11, mesmo que a 1ª vaza seja aberta pelo outro")
    void decideQuemTem11MesmoSemAbrir() {
        MesaDeTeste mesa = mesa(11, 5);

        assertThat(mesa.estado().rodada().fase()).isEqualTo(new DecisaoRodadaDeOnze(ANA));

        mesa.aceitar(ANA);

        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(BETO));
    }

    @Test
    @DisplayName("RG-ONZE-1: X com 11 e Y com 8; se X corre, Y ganha 1 ponto")
    void correrDaRodadaDeOnze() {
        MesaDeTeste mesa = mesa(8, 11).correr(BETO);

        assertThat(mesa.eventos(JogadorCorreu.class)).containsExactly(new JogadorCorreu(BETO));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 1));
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DA_ANA)).isEqualTo(9);
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DO_BETO)).isEqualTo(11);
        // Beto continua com 11, e a rodada seguinte também é de Onze.
        assertThat(mesa.estado().numeroDaRodada()).isEqualTo(2);
        assertThat(mesa.estado().rodada().tipo()).isEqualTo(new TipoDeRodada.DeOnze(EQUIPE_DO_BETO, false));
    }

    @Test
    @DisplayName("RG-ONZE-1 e RG-ONZE-2: decidindo jogar, a rodada vale 3 e começa a 1ª vaza")
    void jogarARodadaDeOnze() {
        MesaDeTeste mesa = mesa(8, 11).aceitar(BETO);

        assertThat(mesa.eventos()).containsExactly(new AumentoAceito(BETO, 3));
        assertThat(mesa.estado().rodada().aposta().valor()).isEqualTo(3);
        assertThat(mesa.estado().rodada().tipo()).isEqualTo(new TipoDeRodada.DeOnze(EQUIPE_DO_BETO, true));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(BETO));
    }

    @Test
    @DisplayName("RG-ONZE-1 e RG-FIM-1: X com 11 joga a Rodada de Onze, vence, ganha 3 e vence a partida")
    void vencerARodadaDeOnze() {
        MesaDeTeste mesa = mesa(8, 11)
                .aceitar(BETO)
                .jogar(BETO, "3♠")
                .jogar(ANA, "4♣")
                .jogar(BETO, "A♥")
                .jogar(ANA, "A♠");

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 3));
        assertThat(mesa.eventos(PartidaFinalizada.class)).hasSize(1);
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DO_BETO)).isEqualTo(14);
    }

    @Test
    @DisplayName("RG-ONZE-1: se Y vence a Rodada de Onze, ganha os 3 pontos")
    void adversarioVenceARodadaDeOnze() {
        // Ana vence a 1ª (A♠ contra 5♦) e a 2ª (3♣ contra A♥).
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                        .placar(4, 11)
                        .mao(BETO, "5♦ A♥ 4♠")
                        .mao(ANA, "A♠ 3♣ 6♦")
                        .montar())
                .aceitar(BETO)
                .jogar(BETO, "5♦")
                .jogar(ANA, "A♠")
                .jogar(ANA, "3♣")
                .jogar(BETO, "A♥");

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 3));
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DA_ANA)).isEqualTo(7);
    }

    @Test
    @DisplayName("RG-ONZE-2 e RG-AUM-7: na Rodada de Onze ninguém pede aumento, em nenhuma vaza")
    void semAumentoNaRodadaDeOnze() {
        MesaDeTeste mesa = mesa(8, 11).aceitar(BETO);

        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO))
                .containsExactly(new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new Correr());
        assertThat(MOTOR.aplicar(mesa.estado(), BETO, new PedirAumento())).isEqualTo(new Rejeitada(ACAO_INVALIDA));

        mesa.jogar(BETO, "3♠");

        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).doesNotContain(new PedirAumento());

        mesa.jogar(ANA, "4♣");

        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).doesNotContain(new PedirAumento());
    }

    @Test
    @DisplayName("RG-ONZE-3: na Rodada de Onze há descarte, e ele acontece antes da decisão de jogar ou correr")
    void descarteAntesDaDecisao() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .placar(8, 11)
                .mao(BETO, "3♠ A♥ 5♦")
                .mao(ANA, "4♦ A♠ 6♦")
                .baralhoComecandoPor("2♣")
                .emDescarte()
                .montar());

        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoDescarte(carta("4♦")));
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).containsExactly(new RecusarDescarte());

        // Ana descarta o 4♦ e compra o 2♣; ninguém tem o 4♠, a carta seguinte, e o descarte acaba.
        mesa.descartar(ANA, "4♦").recusarDescarte(BETO).recusarDescarte(ANA).recusarDescarte(BETO);

        assertThat(mesa.estado().rodada().maoDe(ANA)).containsExactly(carta("2♣"), carta("A♠"), carta("6♦"));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new DecisaoRodadaDeOnze(BETO));
    }

    @Test
    @DisplayName("RG-ONZE-1 e RG-AUM-6: depois de decidir jogar, quem corre na própria vez entrega os 3 pontos")
    void correrDepoisDeDecidirJogar() {
        MesaDeTeste mesa = mesa(5, 11).aceitar(BETO).correr(BETO);

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 3));
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DA_ANA)).isEqualTo(8);
    }
}
