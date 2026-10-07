package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.ACAO_INVALIDA;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.NAO_E_A_VEZ_DO_JOGADOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.evento.AumentoAceito;
import io.github.joaomarcosvs.truco.evento.AumentoPedido;
import io.github.joaomarcosvs.truco.evento.JogadorCorreu;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoJogada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoRespostaDeAumento;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Aumentos com vira 7♦ (manilha 10). Ana dá as cartas, então Beto joga primeiro; nos exemplos da seção 13, X é Beto e
 * Y é Ana.
 */
class AumentoTest {

    private static MesaDeTeste mesa() {
        return new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "3♠ A♥ 5♦").mao(ANA, "4♣ A♠ 6♦").montar());
    }

    @Test
    @DisplayName("RG-AUM-2: só o jogador da vez pode pedir aumento, antes de jogar a carta")
    void soQuemEstaNaVezPede() {
        MesaDeTeste mesa = mesa();

        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).contains(new PedirAumento());
        assertThat(MOTOR.aplicar(mesa.estado(), ANA, new PedirAumento()))
                .isEqualTo(new Rejeitada(NAO_E_A_VEZ_DO_JOGADOR));

        mesa.jogar(BETO, "3♠");

        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).contains(new PedirAumento());
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).isEmpty();
    }

    @Test
    @DisplayName("RG-AUM-3 e RG-AUM-4: X pede truco, Y corre, e X ganha 1 ponto")
    void trucoRecusado() {
        MesaDeTeste mesa = mesa().pedirAumento(BETO);

        assertThat(mesa.eventos()).containsExactly(new AumentoPedido(BETO, 3));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoRespostaDeAumento(ANA, 3));
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA))
                .containsExactly(new Aceitar(), new Correr(), new PedirAumento());
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).isEmpty();

        mesa.correr(ANA);

        assertThat(mesa.eventos(JogadorCorreu.class)).containsExactly(new JogadorCorreu(ANA));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DO_BETO)).isEqualTo(1);
        assertThat(mesa.estado().numeroDaRodada()).isEqualTo(2);
    }

    @Test
    @DisplayName("RG-AUM-3: aceito o aumento, a rodada passa a valer o novo valor e quem ia jogar continua")
    void trucoAceito() {
        MesaDeTeste mesa = mesa().pedirAumento(BETO).aceitar(ANA);

        assertThat(mesa.eventos(AumentoAceito.class)).containsExactly(new AumentoAceito(ANA, 3));
        assertThat(mesa.estado().rodada().aposta().valor()).isEqualTo(3);
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(BETO));

        // Beto vence a 1ª (3♠ contra 4♣) e a 2ª empata (A♥ contra A♠): Beto vence a rodada, que vale 3.
        mesa.jogar(BETO, "3♠").jogar(ANA, "4♣").jogar(BETO, "A♥").jogar(ANA, "A♠");

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 3));
    }

    @Test
    @DisplayName("RG-AUM-4: X pede truco, Y aceita (vale 3), Y pede seis, X corre, e Y ganha 3 pontos")
    void seisRecusado() {
        MesaDeTeste mesa =
                mesa().pedirAumento(BETO).aceitar(ANA).jogar(BETO, "3♠").pedirAumento(ANA);

        assertThat(mesa.eventos(AumentoPedido.class))
                .containsExactly(new AumentoPedido(BETO, 3), new AumentoPedido(ANA, 6));

        mesa.correr(BETO);

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 3));
    }

    @Test
    @DisplayName("RG-AUM-5: X pede truco, Y aceita, Y pede seis, X aceita (vale 6), e agora só X pode pedir nove")
    void direitoDeAumentarAlterna() {
        MesaDeTeste mesa =
                mesa().pedirAumento(BETO).aceitar(ANA).jogar(BETO, "3♠").pedirAumento(ANA);
        mesa.aceitar(BETO);

        // Ana, que pediu o seis, espera: na vez dela não pode pedir nove.
        assertThat(mesa.estado().rodada().aposta().valor()).isEqualTo(6);
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(ANA));
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).doesNotContain(new PedirAumento());

        // Ana perde a vaza (4♣ contra 3♠); Beto abre a seguinte e pode pedir nove.
        mesa.jogar(ANA, "4♣");

        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(BETO));
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).contains(new PedirAumento());
        assertThat(mesa.pedirAumento(BETO).eventos(AumentoPedido.class)).last().isEqualTo(new AumentoPedido(BETO, 9));
    }

    @Test
    @DisplayName("RG-AUM-3: X pede truco e Y responde com seis; X pode aceitar, correr ou pedir nove")
    void pedirMais() {
        MesaDeTeste mesa = mesa().pedirAumento(BETO).pedirAumento(ANA);

        assertThat(mesa.eventos())
                .containsExactly(new AumentoPedido(BETO, 3), new AumentoAceito(ANA, 3), new AumentoPedido(ANA, 6));
        assertThat(mesa.estado().rodada().aposta().valor()).isEqualTo(3);
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoRespostaDeAumento(BETO, 6));
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO))
                .containsExactly(new Aceitar(), new Correr(), new PedirAumento());
    }

    @Test
    @DisplayName("RG-AUM-1: com a rodada valendo 12, ninguém pode pedir aumento")
    void dozeEOTeto() {
        MesaDeTeste mesa =
                mesa().pedirAumento(BETO).pedirAumento(ANA).pedirAumento(BETO).pedirAumento(ANA);

        // Com o doze pendente, não há nível acima para pedir mais.
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).containsExactly(new Aceitar(), new Correr());

        mesa.aceitar(BETO);

        assertThat(mesa.estado().rodada().aposta().valor()).isEqualTo(12);
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).doesNotContain(new PedirAumento());
        mesa.jogar(BETO, "3♠");
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).doesNotContain(new PedirAumento());
    }

    @ParameterizedTest(name = "{0} recusado: quem pediu ganha {2}")
    @CsvSource({"truco, 1, 1", "seis, 2, 3", "nove, 3, 6", "doze, 4, 9"})
    @DisplayName("RG-AUM-4: quando o adversário corre, quem pediu ganha o valor anterior ao pedido")
    void valorAoCorrer(String nome, int pedidos, int pontos) {
        // Beto pede truco, e cada um pede mais até chegar ao nível recusado.
        MesaDeTeste mesa = mesa();
        JogadorId pedinte = BETO;
        mesa.pedirAumento(pedinte);
        for (int pedido = 2; pedido <= pedidos; pedido++) {
            pedinte = outro(pedinte);
            mesa.pedirAumento(pedinte);
        }

        mesa.correr(outro(pedinte));

        EquipeId equipeDoPedinte = pedinte.equals(BETO) ? EQUIPE_DO_BETO : EQUIPE_DA_ANA;
        assertThat(mesa.eventos(RodadaFinalizada.class))
                .containsExactly(new RodadaFinalizada(1, equipeDoPedinte, pontos));
    }

    @Test
    @DisplayName("RG-AUM-7: pode-se pedir aumento na 1ª, na 2ª e na 3ª vaza")
    void aumentoEmQualquerVaza() {
        MesaDeTeste mesa = mesa();
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).contains(new PedirAumento());

        mesa.jogar(BETO, "3♠").jogar(ANA, "4♣"); // Beto vence e abre a 2ª
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).contains(new PedirAumento());

        mesa.jogar(BETO, "5♦").jogar(ANA, "6♦"); // Ana vence e abre a 3ª
        assertThat(mesa.estado().rodada().numeroDaVazaAtual()).isEqualTo(3);
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).contains(new PedirAumento());
    }

    @Test
    @DisplayName("Aceitar sem pedido pendente é rejeitado, e quem pediu não responde ao próprio pedido")
    void respostasForaDeHora() {
        MesaDeTeste mesa = mesa();
        assertThat(MOTOR.aplicar(mesa.estado(), BETO, new Aceitar())).isEqualTo(new Rejeitada(ACAO_INVALIDA));

        mesa.pedirAumento(BETO);

        assertThat(MOTOR.aplicar(mesa.estado(), BETO, new Aceitar())).isEqualTo(new Rejeitada(NAO_E_A_VEZ_DO_JOGADOR));
    }

    @Test
    @DisplayName("RG-VIS-1: a visão mostra o pedido pendente e de quem é a resposta")
    void visaoDoPedido() {
        MesaDeTeste mesa = mesa().pedirAumento(BETO);

        VisaoDoJogador doBeto = MOTOR.visaoDe(mesa.estado(), BETO);

        assertThat(doBeto.aposta().pedidoPendente()).contains(new PedidoDeAumento(BETO, ANA, 3));
        assertThat(doBeto.vezDe()).contains(ANA);
        assertThat(MOTOR.visaoDe(mesa.estado(), ANA).aposta()).isEqualTo(doBeto.aposta());
        assertThat(doBeto.aposta().ultimaEquipeQueAceitou()).isEqualTo(Optional.empty());
    }

    private static JogadorId outro(JogadorId jogador) {
        return jogador.equals(BETO) ? ANA : BETO;
    }
}
