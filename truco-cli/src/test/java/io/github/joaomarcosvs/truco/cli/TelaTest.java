package io.github.joaomarcosvs.truco.cli;

import static io.github.joaomarcosvs.truco.cli.Cartas.carta;
import static io.github.joaomarcosvs.truco.cli.Cartas.cartas;
import static io.github.joaomarcosvs.truco.cli.NarradorTest.CONFIGURACAO;
import static io.github.joaomarcosvs.truco.cli.NarradorTest.EQUIPE_DO_BOT;
import static io.github.joaomarcosvs.truco.cli.NarradorTest.SUA_EQUIPE;
import static io.github.joaomarcosvs.truco.cli.NarradorTest.narrador;
import static io.github.joaomarcosvs.truco.cli.NarradorTest.placar;
import static io.github.joaomarcosvs.truco.cli.TrucoCli.BOT;
import static io.github.joaomarcosvs.truco.cli.TrucoCli.VOCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.JogarEncoberta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.Aposta;
import io.github.joaomarcosvs.truco.partida.PedidoDeAumento;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;
import io.github.joaomarcosvs.truco.visao.JogadaVisivel;
import io.github.joaomarcosvs.truco.visao.VazaVisivel;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A mesa e as opções de menu, a partir de visões montadas à mão. Você joga contra o bot, com vira 6♣. */
class TelaTest {

    private final Tela tela = new Tela(narrador(Notacao.SIMBOLOS), CONFIGURACAO.variante());

    /** Monta a visão de "você", com padrões de uma 1ª vaza sem nada jogado; cada teste muda o que precisa. */
    private static final class Visao {
        TipoDeRodada tipo = new TipoDeRodada.Normal();
        Aposta aposta = Aposta.inicial(1);
        List<Carta> descartadas = List.of();
        Optional<Carta> cartaDaVez = Optional.empty();
        List<Carta> mao = cartas("2♥ 3♣ J♦");
        int cartasDoBot = 3;
        List<VazaVisivel> vazas = new ArrayList<>();
        List<JogadaVisivel> vazaAtual = List.of();

        Visao pedidoDoBot(int nivel) {
            aposta = new Aposta(
                    aposta.valor(),
                    aposta.ultimaEquipeQueAceitou(),
                    Optional.of(new PedidoDeAumento(BOT, VOCE, nivel)));
            return this;
        }

        VisaoDoJogador montar() {
            int suasCartas = tipo instanceof TipoDeRodada.Escurinho ? 3 : mao.size();
            return new VisaoDoJogador(
                    VOCE,
                    List.of(VOCE, BOT),
                    CONFIGURACAO.equipes(),
                    placar(2, 1),
                    3,
                    BOT,
                    tipo,
                    aposta,
                    Optional.of(carta("6♣")),
                    descartadas,
                    cartaDaVez,
                    tipo instanceof TipoDeRodada.Escurinho ? List.of() : mao,
                    Map.of(VOCE, suasCartas, BOT, cartasDoBot),
                    vazas,
                    vazaAtual,
                    Optional.of(VOCE),
                    Optional.empty());
        }
    }

    private static JogadaVisivel aberta(String quem, String carta) {
        return new JogadaVisivel(quem.equals("você") ? VOCE : BOT, Optional.of(carta(carta)), false);
    }

    @Test
    @DisplayName("A mesa mostra rodada, valor, placar, vira, as vazas, a vaza em andamento e as mãos")
    void mesa() {
        // O bot dá as cartas: você abre a 1ª vaza, vence e abre a 2ª.
        Visao visao = new Visao();
        visao.mao = cartas("2♥");
        visao.cartasDoBot = 2;
        visao.vazas.add(new VazaVisivel(
                List.of(aberta("você", "3♠"), aberta("bot", "10♠")), new ResultadoDaVaza.Vencida(VOCE, SUA_EQUIPE)));
        visao.vazaAtual = List.of(aberta("você", "J♦"));

        assertThat(tela.mesa(visao.montar()))
                .containsExactly(
                        "Rodada 3 | vale 1 | você 2 x 1 bot | vira 6♣",
                        "Vaza 1: você 3♠, bot 10♠ (você venceu)",
                        "Vaza 2: você J♦",
                        "Sua mão: [1] 2♥ | bot: 2 cartas");
    }

    @Test
    @DisplayName("RG-ENC-3: a carta encoberta do bot aparece só como encoberta; a sua, com a carta")
    void encobertas() {
        Visao visao = new Visao();
        visao.mao = cartas("2♥");
        visao.cartasDoBot = 1;
        visao.vazas.add(new VazaVisivel(
                List.of(aberta("você", "3♠"), aberta("bot", "10♠")), new ResultadoDaVaza.Vencida(VOCE, SUA_EQUIPE)));
        visao.vazas.add(new VazaVisivel(
                List.of(
                        new JogadaVisivel(VOCE, Optional.of(carta("J♦")), true),
                        new JogadaVisivel(BOT, Optional.empty(), true)),
                new ResultadoDaVaza.Empatada()));

        assertThat(tela.mesa(visao.montar())).contains("Vaza 2: você J♦ encoberta, bot encoberta (empate)");
    }

    @Test
    @DisplayName("A mesa mostra o aumento pedido, o descarte e as rodadas especiais")
    void situacoes() {
        Visao comPedido = new Visao().pedidoDoBot(3);
        assertThat(tela.mesa(comPedido.montar())).contains("Bot pediu aumento para 3.");

        Visao noDescarte = new Visao();
        noDescarte.descartadas = cartas("4♦");
        noDescarte.cartaDaVez = Optional.of(carta("4♠"));
        assertThat(tela.mesa(noDescarte.montar())).contains("Descartadas: 4♦", "Descarte: a carta da vez é 4♠.");

        Visao deOnze = new Visao();
        deOnze.tipo = new TipoDeRodada.DeOnze(SUA_EQUIPE, false);
        assertThat(tela.mesa(deOnze.montar())).contains("Rodada de Onze: você decide se joga ou corre.");
        deOnze.tipo = new TipoDeRodada.DeOnze(EQUIPE_DO_BOT, true);
        assertThat(tela.mesa(deOnze.montar())).contains("Rodada de Onze: bot decidiu jogar.");
    }

    @Test
    @DisplayName("RG-VIS-3: na Escurinho, a sua mão aparece só pelas posições")
    void escurinho() {
        Visao visao = new Visao();
        visao.tipo = new TipoDeRodada.Escurinho();

        assertThat(tela.mesa(visao.montar()))
                .contains(
                        "Rodada Escurinho: ninguém vê as próprias cartas.",
                        "Sua mão: [1] ? [2] ? [3] ? | bot: 3 cartas");
        assertThat(tela.opcao(new JogarCarta(1), visao.montar())).isEqualTo("Jogar a carta [2] (às cegas)");
    }

    @Test
    @DisplayName("Na sua vez: jogar aberta ou encoberta, pedir aumento e correr, com o efeito de cada opção")
    void opcoesNaVez() {
        VisaoDoJogador visao = new Visao().montar();

        assertThat(tela.opcao(new JogarCarta(0), visao)).isEqualTo("Jogar 2♥");
        assertThat(tela.opcao(new JogarEncoberta(1), visao)).isEqualTo("Jogar 3♣ encoberta");
        assertThat(tela.opcao(new PedirAumento(), visao)).isEqualTo("Pedir aumento para 3");
        assertThat(tela.opcao(new Correr(), visao)).isEqualTo("Correr (bot ganha 1 ponto)");

        Visao valendoTres = new Visao();
        valendoTres.aposta = new Aposta(3, Optional.of(SUA_EQUIPE), Optional.empty());
        assertThat(tela.opcao(new PedirAumento(), valendoTres.montar())).isEqualTo("Pedir aumento para 6");
        assertThat(tela.opcao(new Correr(), valendoTres.montar())).isEqualTo("Correr (bot ganha 3 pontos)");
    }

    @Test
    @DisplayName("RG-AUM-3 e RG-AUM-4: respondendo a um pedido, aceitar, correr ou aceitar e pedir mais")
    void opcoesNaResposta() {
        VisaoDoJogador truco = new Visao().pedidoDoBot(3).montar();

        assertThat(tela.opcao(new Aceitar(), truco)).isEqualTo("Aceitar (a rodada passa a valer 3)");
        assertThat(tela.opcao(new Correr(), truco)).isEqualTo("Correr (bot ganha 1 ponto)");
        assertThat(tela.opcao(new PedirAumento(), truco)).isEqualTo("Aceitar e pedir aumento para 6");

        Visao seis = new Visao();
        seis.aposta = new Aposta(3, Optional.of(SUA_EQUIPE), Optional.empty());
        assertThat(tela.opcao(new Correr(), seis.pedidoDoBot(6).montar())).isEqualTo("Correr (bot ganha 3 pontos)");
    }

    @Test
    @DisplayName("RG-ONZE-1: na Rodada de Onze, jogar vale 3 e correr dá 1 ao adversário")
    void opcoesNaRodadaDeOnze() {
        Visao decisao = new Visao();
        decisao.tipo = new TipoDeRodada.DeOnze(SUA_EQUIPE, false);

        assertThat(tela.opcao(new Aceitar(), decisao.montar())).isEqualTo("Jogar a Rodada de Onze (vale 3)");
        assertThat(tela.opcao(new Correr(), decisao.montar())).isEqualTo("Correr (bot ganha 1 ponto)");

        Visao jogando = new Visao();
        jogando.tipo = new TipoDeRodada.DeOnze(SUA_EQUIPE, true);
        jogando.aposta = new Aposta(3, Optional.empty(), Optional.empty());
        assertThat(tela.opcao(new Correr(), jogando.montar())).isEqualTo("Correr (bot ganha 3 pontos)");
    }

    @Test
    @DisplayName("RG-DESC-4 e RG-DESC-5: no descarte, descartar a carta da vez ou não descartar")
    void opcoesNoDescarte() {
        Visao visao = new Visao();
        visao.mao = cartas("4♦ 3♣ J♦");
        visao.cartaDaVez = Optional.of(carta("4♦"));

        assertThat(tela.opcao(new Descartar(0), visao.montar())).isEqualTo("Descartar 4♦ e comprar outra do baralho");
        assertThat(tela.opcao(new RecusarDescarte(), visao.montar())).isEqualTo("Não descartar");
    }
}
