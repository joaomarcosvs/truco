package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.CARLA;
import static io.github.joaomarcosvs.truco.partida.Partidas.DAVI;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.acao.VerCartasDoParceiro;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.NotacaoDeCartas;
import io.github.joaomarcosvs.truco.evento.CartaDescartada;
import io.github.joaomarcosvs.truco.evento.CartaRecebidaPorDescarte;
import io.github.joaomarcosvs.truco.evento.CartasDoParceiroVistas;
import io.github.joaomarcosvs.truco.evento.DuplaViuAsCartas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.QuisVerAsCartas;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoJogada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoRespostaDeAumento;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.DecisaoRodadaDeOnze;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Empatada;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Vencida;
import io.github.joaomarcosvs.truco.visao.JogadaVisivel;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O 2x2 (seção 11 das regras): Ana e Carla contra Beto e Davi, sentados nessa ordem. Na mesa padrão, a vira é 7♦ (a
 * manilha é o 10), Davi dá as cartas e Ana abre: a ordem de jogo é Ana, Beto, Carla e Davi.
 */
class DuplasTest {

    private static final List<JogadorId> MESA = List.of(ANA, BETO, CARLA, DAVI);

    private static Cenario mesa() {
        return Cenario.comVira("7♦")
                .emDuplas()
                .carteador(DAVI)
                .mao(ANA, "3♥ 4♠ 5♠")
                .mao(BETO, "2♠ 4♥ 5♦")
                .mao(CARLA, "3♣ 6♠ 6♦")
                .mao(DAVI, "7♠ 5♥ 6♣");
    }

    private static MesaDeTeste jogar(Cenario cenario) {
        return new MesaDeTeste(cenario.montar());
    }

    private static VisaoDoJogador visao(MesaDeTeste mesa, JogadorId jogador) {
        return MOTOR.visaoDe(mesa.estado(), jogador);
    }

    /** Os eventos que o jogador pode ver. */
    private static List<Evento> vistosPor(MesaDeTeste mesa, JogadorId jogador) {
        return mesa.eventos().stream()
                .filter(evento -> evento.visivelPara(jogador))
                .toList();
    }

    @Test
    @DisplayName("RG-2X2-3 e RG-2X2-5: a vaza fecha com as 4 cartas, a mais forte vence para a dupla, e quem a jogou"
            + " abre a seguinte")
    void vazaComQuatroCartas() {
        MesaDeTeste mesa = jogar(mesa()).jogar(ANA, "4♠").jogar(BETO, "2♠").jogar(CARLA, "6♠");
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(DAVI));

        mesa.jogar(DAVI, "7♠");

        assertThat(mesa.eventos(VazaFinalizada.class))
                .containsExactly(new VazaFinalizada(1, new Vencida(BETO, EQUIPE_DO_BETO)));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(BETO));
    }

    @Test
    @DisplayName("RG-2X2-4, RG-2X2-5 e RG-EMP-1: empatar com o parceiro empata a vaza, quem abriu abre a seguinte, e a"
            + " dupla que vence a 2ª vence a rodada")
    void empateComOParceiro() {
        MesaDeTeste mesa = jogar(mesa())
                .jogar(ANA, "3♥")
                .jogar(BETO, "2♠")
                .jogar(CARLA, "3♣")
                .jogar(DAVI, "7♠");

        assertThat(mesa.eventos(VazaFinalizada.class)).containsExactly(new VazaFinalizada(1, new Empatada()));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(ANA));

        mesa.jogar(ANA, "5♠").jogar(BETO, "4♥").jogar(CARLA, "6♠").jogar(DAVI, "5♥");

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 1));
    }

    @Test
    @DisplayName("RG-2X2-6: o direito de aumentar é da dupla que aceitou, e cada um dela pede na sua vez")
    void direitoDeAumentarDaDupla() {
        MesaDeTeste mesa = jogar(mesa()).pedirAumento(ANA).aceitar(BETO);

        // Aceito o truco, a vez volta para Ana, que não pode pedir: o direito é da dupla do Beto (RG-AUM-5).
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).doesNotContain(new PedirAumento());
        mesa.jogar(ANA, "4♠");
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).contains(new PedirAumento());
        mesa.jogar(BETO, "2♠");
        assertThat(MOTOR.acoesLegais(mesa.estado(), CARLA)).doesNotContain(new PedirAumento());
        mesa.jogar(CARLA, "6♠");
        assertThat(MOTOR.acoesLegais(mesa.estado(), DAVI)).contains(new PedirAumento());
    }

    @Test
    @DisplayName("RG-2X2-7: recebe o pedido o adversário à direita de quem pediu; num pedir mais, quem tinha pedido")
    void quemRecebeOPedido() {
        MesaDeTeste mesa = jogar(mesa()).pedirAumento(ANA);
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoRespostaDeAumento(BETO, 3));

        mesa.pedirAumento(BETO);
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoRespostaDeAumento(ANA, 6));

        MesaDeTeste daCarla = jogar(mesa()).jogar(ANA, "4♠").jogar(BETO, "2♠").pedirAumento(CARLA);
        assertThat(daCarla.estado().rodada().fase()).isEqualTo(new AguardandoRespostaDeAumento(DAVI, 3));
    }

    @Test
    @DisplayName("RG-2X2-8: só quem recebeu responde, pela dupla; correndo, a dupla de quem pediu ganha os pontos")
    void soQuemRecebeuResponde() {
        MesaDeTeste mesa = jogar(mesa()).pedirAumento(ANA);

        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).contains(new Aceitar(), new Correr(), new PedirAumento());
        assertThat(MOTOR.acoesLegais(mesa.estado(), DAVI)).containsExactly(new VerCartasDoParceiro());
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).isEmpty();
        assertThat(MOTOR.acoesLegais(mesa.estado(), CARLA)).isEmpty();

        mesa.correr(BETO);

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 1));
    }

    @Test
    @DisplayName("RG-2X2-9 e RG-2X2-10: quando os dois parceiros que receberam o pedido querem, eles se veem até"
            + " responder, e os adversários só sabem que eles olharam")
    void duplaSeVeAoReceberOPedido() {
        MesaDeTeste mesa = jogar(mesa()).pedirAumento(ANA);
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).contains(new VerCartasDoParceiro());

        mesa.verCartas(DAVI);
        // Só o Davi quis: ninguém vê nada ainda.
        assertThat(MESA)
                .allSatisfy(jogador ->
                        assertThat(visao(mesa, jogador).maosDosParceiros()).isEmpty());
        assertThat(MOTOR.acoesLegais(mesa.estado(), DAVI)).isEmpty();

        mesa.verCartas(BETO);

        assertThat(mesa.eventos(QuisVerAsCartas.class))
                .containsExactly(new QuisVerAsCartas(DAVI), new QuisVerAsCartas(BETO));
        assertThat(mesa.eventos(DuplaViuAsCartas.class)).containsExactly(new DuplaViuAsCartas(EQUIPE_DO_BETO));
        assertThat(visao(mesa, BETO).maosDosParceiros()).isEqualTo(Map.of(DAVI, cartas("7♠ 5♥ 6♣")));
        assertThat(visao(mesa, DAVI).maosDosParceiros()).isEqualTo(Map.of(BETO, cartas("2♠ 4♥ 5♦")));
        assertThat(visao(mesa, ANA).maosDosParceiros()).isEmpty();
        assertThat(visao(mesa, CARLA).maosDosParceiros()).isEmpty();
        assertThat(vistosPor(mesa, BETO))
                .contains(new CartasDoParceiroVistas(BETO, DAVI, cartas("7♠ 5♥ 6♣")))
                .doesNotContain(new CartasDoParceiroVistas(DAVI, BETO, cartas("2♠ 4♥ 5♦")));
        assertThat(vistosPor(mesa, ANA)).noneMatch(CartasDoParceiroVistas.class::isInstance);
        assertThat(visao(mesa, ANA).aposta().pedidoPendente().orElseThrow().queremVerAsCartas())
                .containsExactly(DAVI, BETO);

        mesa.aceitar(BETO);

        assertThat(MESA)
                .allSatisfy(jogador ->
                        assertThat(visao(mesa, jogador).maosDosParceiros()).isEmpty());
    }

    @Test
    @DisplayName("RG-2X2-10: quem recebeu pode responder sem esperar o parceiro, e aí ninguém vê nada")
    void responderSemEsperar() {
        MesaDeTeste mesa = jogar(mesa()).pedirAumento(ANA).verCartas(DAVI).aceitar(BETO);

        assertThat(mesa.eventos(DuplaViuAsCartas.class)).isEmpty();
        assertThat(MOTOR.acoesLegais(mesa.estado(), DAVI)).doesNotContain(new VerCartasDoParceiro());
        assertThat(MESA)
                .allSatisfy(jogador ->
                        assertThat(visao(mesa, jogador).maosDosParceiros()).isEmpty());
    }

    @Test
    @DisplayName("RG-2X2-9: quem pede não vê as cartas do parceiro; num pedir mais, quem pode ver é a dupla que recebe")
    void quemPedeNaoVe() {
        MesaDeTeste mesa = jogar(mesa()).pedirAumento(ANA);
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).doesNotContain(new VerCartasDoParceiro());
        assertThat(MOTOR.acoesLegais(mesa.estado(), CARLA)).doesNotContain(new VerCartasDoParceiro());

        mesa.pedirAumento(BETO);

        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).contains(new VerCartasDoParceiro());
        assertThat(MOTOR.acoesLegais(mesa.estado(), CARLA)).containsExactly(new VerCartasDoParceiro());
        assertThat(MOTOR.acoesLegais(mesa.estado(), DAVI)).isEmpty();
    }

    @Test
    @DisplayName("RG-2X2-9: no 1x1 não há parceiro, e ninguém pode querer ver as cartas")
    void semParceiroNo1x1() {
        EstadoDaPartida estado = new MesaDeTeste(Cenario.comVira("7♦")
                        .mao(ANA, "3♥ 4♠ 5♠")
                        .mao(BETO, "2♠ 4♥ 5♦")
                        .montar())
                .pedirAumento(BETO)
                .estado();

        assertThat(MOTOR.acoesLegais(estado, ANA)).doesNotContain(new VerCartasDoParceiro());
        assertThat(MOTOR.visaoDe(estado, ANA).maosDosParceiros()).isEmpty();
    }

    @Test
    @DisplayName("RG-2X2-11: na Rodada de Onze, depois do descarte, a dupla com 11 vê as cartas uma da outra, e a"
            + " adversária não")
    void rodadaDeOnzeEmDuplas() {
        // Ninguém tem o 4♦, a primeira carta do descarte: todos passam, e começa a decisão (RG-ONZE-3).
        MesaDeTeste mesa = jogar(mesa().placar(11, 5).emDescarte())
                .recusarDescarte(ANA)
                .recusarDescarte(BETO)
                .recusarDescarte(CARLA)
                .recusarDescarte(DAVI);

        assertThat(mesa.estado().rodada().fase()).isEqualTo(new DecisaoRodadaDeOnze(ANA));
        assertThat(vistosPor(mesa, ANA)).contains(new CartasDoParceiroVistas(ANA, CARLA, cartas("3♣ 6♠ 6♦")));
        assertThat(vistosPor(mesa, CARLA)).contains(new CartasDoParceiroVistas(CARLA, ANA, cartas("3♥ 4♠ 5♠")));
        assertThat(vistosPor(mesa, BETO)).noneMatch(CartasDoParceiroVistas.class::isInstance);
        assertThat(visao(mesa, ANA).maosDosParceiros()).isEqualTo(Map.of(CARLA, cartas("3♣ 6♠ 6♦")));
        assertThat(visao(mesa, CARLA).maosDosParceiros()).isEqualTo(Map.of(ANA, cartas("3♥ 4♠ 5♠")));
        assertThat(visao(mesa, BETO).maosDosParceiros()).isEmpty();
        assertThat(visao(mesa, DAVI).maosDosParceiros()).isEmpty();
    }

    @Test
    @DisplayName("RG-2X2-12: decide a Rodada de Onze quem da dupla com 11 joga primeiro, e depois da decisão ninguém"
            + " vê mais as cartas do parceiro")
    void quemDecideARodadaDeOnze() {
        // Com Ana dando as cartas, a ordem é Beto, Carla, Davi e Ana: da dupla da Ana, joga primeiro a Carla.
        MesaDeTeste mesa = jogar(mesa().carteador(ANA).placar(11, 5));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new DecisaoRodadaDeOnze(CARLA));
        assertThat(MOTOR.acoesLegais(mesa.estado(), CARLA)).containsExactly(new Aceitar(), new Correr());
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).isEmpty();

        mesa.aceitar(CARLA);

        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(BETO));
        assertThat(MESA)
                .allSatisfy(jogador ->
                        assertThat(visao(mesa, jogador).maosDosParceiros()).isEmpty());
    }

    @Test
    @DisplayName("RG-2X2-13: na Rodada Escurinho, ninguém vê a própria mão nem a do parceiro")
    void escurinhoEmDuplas() {
        MesaDeTeste mesa = jogar(mesa().placar(11, 11));

        assertThat(MESA).allSatisfy(jogador -> {
            assertThat(visao(mesa, jogador).mao()).isEmpty();
            assertThat(visao(mesa, jogador).maosDosParceiros()).isEmpty();
        });
    }

    @Test
    @DisplayName("RG-2X2-14: no descarte, os quatro respondem; o dono descarta, e só ele vê a carta que comprou, nem o"
            + " parceiro")
    void descarteEmDuplas() {
        MesaDeTeste mesa =
                jogar(mesa().mao(DAVI, "4♦ 5♥ 6♣").baralhoComecandoPor("A♣").emDescarte());
        assertThat(MESA)
                .allSatisfy(jogador ->
                        assertThat(MOTOR.acoesLegais(mesa.estado(), jogador)).contains(new RecusarDescarte()));
        assertThat(MOTOR.acoesLegais(mesa.estado(), DAVI)).contains(new Descartar(0));

        mesa.recusarDescarte(ANA).descartar(DAVI, "4♦").recusarDescarte(BETO);
        assertThat(mesa.eventos(CartaDescartada.class)).isEmpty();

        mesa.recusarDescarte(CARLA);

        assertThat(mesa.eventos(CartaDescartada.class)).containsExactly(new CartaDescartada(DAVI, carta("4♦")));
        assertThat(vistosPor(mesa, DAVI)).contains(new CartaRecebidaPorDescarte(DAVI, carta("A♣")));
        assertThat(vistosPor(mesa, BETO)).noneMatch(CartaRecebidaPorDescarte.class::isInstance);
    }

    @Test
    @DisplayName("RG-2X2-15: o parceiro não conhece a carta encoberta, e fora do aumento e da Onze ninguém vê a mão do"
            + " parceiro")
    void encobertaEscondidaDoParceiro() {
        MesaDeTeste mesa = jogar(mesa())
                .jogar(ANA, "4♠")
                .jogar(BETO, "2♠")
                .jogar(CARLA, "6♠")
                .jogar(DAVI, "7♠")
                .jogar(BETO, "4♥")
                .jogarEncoberta(CARLA, "3♣");

        assertThat(visao(mesa, ANA).vazaAtual())
                .containsExactly(
                        new JogadaVisivel(BETO, Optional.of(carta("4♥")), false),
                        new JogadaVisivel(CARLA, Optional.empty(), true));
        assertThat(visao(mesa, CARLA).vazaAtual()).contains(new JogadaVisivel(CARLA, Optional.of(carta("3♣")), true));
        assertThat(MESA)
                .allSatisfy(jogador ->
                        assertThat(visao(mesa, jogador).maosDosParceiros()).isEmpty());
    }

    private static List<Carta> cartas(String notacoes) {
        return Arrays.stream(notacoes.split(" ")).map(NotacaoDeCartas::carta).toList();
    }
}
