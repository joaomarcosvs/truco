package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.CartasPresentes.mostra;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.ACAO_INVALIDA;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.NAO_E_A_VEZ_DO_JOGADOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.aplicarAceita;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.evento.CartaDescartada;
import io.github.joaomarcosvs.truco.evento.CartaRecebidaPorDescarte;
import io.github.joaomarcosvs.truco.evento.DescarteEncerrado;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoDescarte;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoJogada;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Descarte, com os exemplos da seção 13. Com vira 7♦ (manilha 10), a sequência é 4♦, 4♠, 4♥, 4♣, 5♦... Ana dá as
 * cartas, e X é Beto e Y é Ana. Os dois respondem a cada carta da vez, em qualquer ordem.
 */
class DescarteTest {

    @Test
    @DisplayName("RG-DESC-4 e RG-DESC-6: X tem 4♦ e 4♠, Y tem 4♥; X descarta os dois, Y descarta o 4♥, e o descarte"
            + " termina porque o 4♣ não está em nenhuma mão")
    void sequenciaDeDescartes() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "4♦ 4♠ A♥")
                .mao(ANA, "4♥ A♠ 6♦")
                .montar());
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoDescarte(carta("4♦")));

        mesa.recusarDescarte(ANA).descartar(BETO, "4♦");
        mesa.recusarDescarte(ANA).descartar(BETO, "4♠");
        mesa.descartar(ANA, "4♥").recusarDescarte(BETO);
        mesa.recusarDescarte(ANA).recusarDescarte(BETO); // ninguém tem o 4♣

        assertThat(mesa.eventos(CartaDescartada.class))
                .containsExactly(
                        new CartaDescartada(BETO, carta("4♦")),
                        new CartaDescartada(BETO, carta("4♠")),
                        new CartaDescartada(ANA, carta("4♥")));
        assertThat(mesa.eventos(DescarteEncerrado.class)).hasSize(1);
        assertThat(mesa.estado().rodada().descarte().descartadas())
                .containsExactly(carta("4♦"), carta("4♠"), carta("4♥"));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(BETO));
    }

    @Test
    @DisplayName("RG-DESC-7: a carta comprada vem do topo do baralho e fica na posição da descartada")
    void reposicao() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "A♥ 4♦ 3♠")
                .mao(ANA, "5♥ A♠ 6♦")
                .baralhoComecandoPor("Q♣ J♦")
                .montar());

        mesa.descartar(BETO, "4♦").recusarDescarte(ANA);

        assertThat(mesa.estado().rodada().maoDe(BETO)).containsExactly(carta("A♥"), carta("Q♣"), carta("3♠"));
        assertThat(mesa.estado().rodada().baralhoRestante().getFirst()).isEqualTo(carta("J♦"));
        assertThat(mesa.eventos(CartaRecebidaPorDescarte.class))
                .containsExactly(new CartaRecebidaPorDescarte(BETO, carta("Q♣")));
    }

    @Test
    @DisplayName("RG-DESC-7: X descarta o 4♦ e compra o 4♠, que é a próxima da sequência, e pode descartá-lo também")
    void compraAProximaDaSequencia() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "4♦ A♥ 5♣")
                .mao(ANA, "5♥ A♠ 6♦")
                .baralhoComecandoPor("4♠")
                .montar());

        mesa.descartar(BETO, "4♦").recusarDescarte(ANA);

        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoDescarte(carta("4♠")));
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).contains(new Descartar(0));
        mesa.descartar(BETO, "4♠").recusarDescarte(ANA);
        assertThat(mesa.eventos(CartaDescartada.class)).last().isEqualTo(new CartaDescartada(BETO, carta("4♠")));
    }

    @Test
    @DisplayName("RG-DESC-3: com vira 3 (manilha 4), a sequência começa em 5♦, e quem tem o 4♦ não pode descartá-lo")
    void manilhaNaoEntraNaSequencia() {
        EstadoDaPartida estado = Cenario.comVira("3♠")
                .emDescarte()
                .mao(BETO, "4♦ 5♦ A♥")
                .mao(ANA, "5♥ A♠ 6♦")
                .montar();

        assertThat(estado.rodada().fase()).isEqualTo(new AguardandoDescarte(carta("5♦")));
        assertThat(MOTOR.acoesLegais(estado, BETO)).containsExactly(new Descartar(1), new RecusarDescarte());
        assertThat(MOTOR.aplicar(estado, BETO, new Descartar(0))).isEqualTo(new Rejeitada(ACAO_INVALIDA));
    }

    @Test
    @DisplayName("RG-DESC-6: ninguém tem o 4♦, que está no baralho, mas Y tem o 4♠: não há descarte")
    void ninguemTemAPrimeira() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "5♥ A♥ 3♠")
                .mao(ANA, "4♠ A♠ 6♦")
                .montar());

        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).containsExactly(new RecusarDescarte());

        mesa.recusarDescarte(ANA).recusarDescarte(BETO);

        assertThat(mesa.eventos()).containsExactly(new DescarteEncerrado());
        assertThat(mesa.estado().rodada().maoDe(ANA)).contains(carta("4♠"));
    }

    @Test
    @DisplayName("RG-DESC-6: quando a primeira carta da sequência é a vira, ninguém pode tê-la e não há descarte")
    void primeiraDaSequenciaEAVira() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("4♦")
                .emDescarte()
                .mao(BETO, "4♠ A♥ 3♠")
                .mao(ANA, "4♥ A♠ 6♦")
                .montar());

        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoDescarte(carta("4♦")));
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).containsExactly(new RecusarDescarte());

        mesa.recusarDescarte(ANA).recusarDescarte(BETO);

        assertThat(mesa.eventos()).containsExactly(new DescarteEncerrado());
    }

    @Test
    @DisplayName("RG-DESC-5 e RG-DESC-1: X tem 4♦ e recusa; Y tem 4♠: a sequência termina, e ninguém descarta mais na"
            + " rodada")
    void recusaEncerra() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "4♦ A♥ 3♠")
                .mao(ANA, "4♠ A♠ 6♦")
                .montar());

        mesa.recusarDescarte(ANA).recusarDescarte(BETO);

        assertThat(mesa.eventos()).containsExactly(new DescarteEncerrado());
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(BETO));
        assertThat(MOTOR.aplicar(mesa.estado(), BETO, new Descartar(0))).isEqualTo(new Rejeitada(ACAO_INVALIDA));
    }

    @Test
    @DisplayName("RG-DESC-2: durante o descarte não se joga carta nem se pede aumento")
    void descarteVemAntesDeTudo() {
        EstadoDaPartida estado = Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "4♦ A♥ 3♠")
                .mao(ANA, "5♥ A♠ 6♦")
                .montar();

        assertThat(MOTOR.acoesLegais(estado, BETO)).containsExactly(new Descartar(0), new RecusarDescarte());
        assertThat(MOTOR.aplicar(estado, BETO, new PedirAumento())).isEqualTo(new Rejeitada(ACAO_INVALIDA));
        assertThat(MOTOR.aplicar(estado, BETO, new JogarCarta(1))).isEqualTo(new Rejeitada(ACAO_INVALIDA));
    }

    @Test
    @DisplayName("Cada jogador responde uma vez a cada carta da vez")
    void respondeUmaVez() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "4♦ A♥ 3♠")
                .mao(ANA, "5♥ A♠ 6♦")
                .montar());

        mesa.recusarDescarte(ANA);

        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).isEmpty();
        assertThat(MOTOR.aplicar(mesa.estado(), ANA, new RecusarDescarte()))
                .isEqualTo(new Rejeitada(NAO_E_A_VEZ_DO_JOGADOR));
    }

    @Test
    @DisplayName("RG-DESC-8 e RG-VIS-4: a carta descartada é pública, e a comprada só quem comprou vê")
    void visibilidadeDoDescarte() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "4♦ A♥ 3♠")
                .mao(ANA, "5♥ A♠ 6♦")
                .baralhoComecandoPor("Q♣")
                .montar());

        mesa.recusarDescarte(ANA).descartar(BETO, "4♦");

        CartaDescartada descartada = mesa.eventos(CartaDescartada.class).getFirst();
        CartaRecebidaPorDescarte recebida =
                mesa.eventos(CartaRecebidaPorDescarte.class).getFirst();
        assertThat(descartada.visivelPara(ANA)).isTrue();
        assertThat(recebida.visivelPara(BETO)).isTrue();
        assertThat(recebida.visivelPara(ANA)).isFalse();
        VisaoDoJogador daAna = MOTOR.visaoDe(mesa.estado(), ANA);
        assertThat(daAna.descartadas()).containsExactly(carta("4♦"));
        assertThat(mostra(daAna, carta("Q♣"))).isFalse();
    }

    @Test
    @DisplayName("RG-DESC-10: durante o descarte, a visão mostra a carta da vez, mas não de quem se espera a resposta")
    void visaoDuranteODescarte() {
        EstadoDaPartida estado = Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "4♦ A♥ 3♠")
                .mao(ANA, "5♥ A♠ 6♦")
                .montar();

        VisaoDoJogador daAna = MOTOR.visaoDe(estado, ANA);

        assertThat(daAna.cartaDaVezNoDescarte()).contains(carta("4♦"));
        assertThat(daAna.vezDe()).isEmpty();
    }

    @ParameterizedTest(name = "Ana responde primeiro: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("RG-DESC-10: para Ana, que não tem a carta da vez, \"Beto tinha e recusou\" e \"ninguém tinha\" são"
            + " indistinguíveis")
    void recusaEAusenciaSaoIndistinguiveis(boolean anaPrimeiro) {
        EstadoDaPartida betoTemAVez = Cenario.comVira("7♦")
                .emDescarte()
                .mao(ANA, "5♥ 6♣ Q♦")
                .mao(BETO, "4♦ A♥ 3♠")
                .montar();
        EstadoDaPartida ninguemTem = Cenario.comVira("7♦")
                .emDescarte()
                .mao(ANA, "5♥ 6♣ Q♦")
                .mao(BETO, "2♦ A♥ 3♠")
                .montar();

        // Para Beto os dois mundos são diferentes; para Ana, idênticos.
        assertThat(MOTOR.acoesLegais(betoTemAVez, BETO)).isNotEqualTo(MOTOR.acoesLegais(ninguemTem, BETO));
        assertThat(oQueAnaPercebe(betoTemAVez, anaPrimeiro)).isEqualTo(oQueAnaPercebe(ninguemTem, anaPrimeiro));
    }

    @Test
    @DisplayName("Sem carta no baralho para comprar, ninguém pode descartar")
    void semBaralhoNaoHaDescarte() {
        EstadoDaPartida estado = Cenario.comVira("7♦")
                .emDescarte()
                .mao(BETO, "4♦ A♥ 3♠")
                .mao(ANA, "5♥ A♠ 6♦")
                .semBaralhoRestante()
                .montar();

        assertThat(MOTOR.acoesLegais(estado, BETO)).containsExactly(new RecusarDescarte());
    }

    /** Tudo o que chega a Ana enquanto os dois recusam: visões, ações legais e eventos visíveis para ela. */
    private static List<Object> oQueAnaPercebe(EstadoDaPartida estado, boolean anaPrimeiro) {
        List<Object> percebido = new ArrayList<>(List.of(MOTOR.visaoDe(estado, ANA), MOTOR.acoesLegais(estado, ANA)));
        for (JogadorId jogador : anaPrimeiro ? List.of(ANA, BETO) : List.of(BETO, ANA)) {
            Aplicada aplicada = aplicarAceita(estado, jogador, new RecusarDescarte());
            estado = aplicada.novoEstado();
            percebido.add(aplicada.eventos().stream()
                    .filter(evento -> evento.visivelPara(ANA))
                    .toList());
            percebido.add(MOTOR.visaoDe(estado, ANA));
            percebido.add(MOTOR.acoesLegais(estado, ANA));
        }
        return percebido;
    }
}
