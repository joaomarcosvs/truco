package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.aplicarAceita;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracao;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracaoEmDuplas;
import static io.github.joaomarcosvs.truco.partida.Partidas.daVez;
import static io.github.joaomarcosvs.truco.partida.Partidas.primeiraAcaoLegal;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.VerCartasDoParceiro;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.AumentoAceito;
import io.github.joaomarcosvs.truco.evento.AumentoPedido;
import io.github.joaomarcosvs.truco.evento.CartaDescartada;
import io.github.joaomarcosvs.truco.evento.CartaEncobertaJogada;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.CartaRecebidaPorDescarte;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.CartasDoParceiroVistas;
import io.github.joaomarcosvs.truco.evento.DescarteEncerrado;
import io.github.joaomarcosvs.truco.evento.DuplaViuAsCartas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.JogadorCorreu;
import io.github.joaomarcosvs.truco.evento.PartidaFinalizada;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.QuisVerAsCartas;
import io.github.joaomarcosvs.truco.evento.RodadaAnulada;
import io.github.joaomarcosvs.truco.evento.RodadaDeOnzeIniciada;
import io.github.joaomarcosvs.truco.evento.RodadaEscurinhoIniciada;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Empatada;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Vencida;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cenário golden: seed e ações fixas, comparadas com os eventos e o placar esperados, conferidos à mão contra as regras.
 * Se este teste quebrar, partidas gravadas deixariam de se reproduzir iguais.
 */
class PartidaGoldenTest {

    @Test
    @DisplayName(
            "Seed 2026, cada jogador sempre faz a primeira ação legal (descarta quando pode e joga a primeira carta):"
                    + " eventos até o início da 4ª rodada")
    void tresRodadas() {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao(2026));
        List<String> linhas = new ArrayList<>(inicio(estado));
        while (estado.numeroDaRodada() <= 3) {
            Aplicada aplicada = primeiraAcaoLegal(estado);
            aplicada.eventos().forEach(evento -> linhas.add(descrever(evento)));
            estado = aplicada.novoEstado();
        }

        // Manilhas: J na rodada 1 (vira Q♦), 2 na rodada 2 (vira A♠) e 7 na rodada 3 (vira 6♣). Em nenhuma
        // rodada alguém tem o 4♦, a primeira carta da sequência, então o descarte termina sem descartes.
        assertThat(String.join("\n", linhas)).isEqualTo("""
                        rodada 1: ana dá as cartas, vale 1, vira Q♦
                        beto recebe 5♥ 10♣ 7♦
                        ana recebe 2♣ 3♣ J♦
                        descarte encerrado
                        beto joga 5♥
                        ana joga 2♣
                        vaza 1: ana vence
                        ana joga 3♣
                        beto joga 10♣
                        vaza 2: ana vence
                        rodada 1: ana marca 1
                        placar: ana 1 x 0 beto
                        rodada 2: beto dá as cartas, vale 1, vira A♠
                        ana recebe 5♥ 2♦ J♥
                        beto recebe 2♠ 7♠ 3♣
                        descarte encerrado
                        ana joga 5♥
                        beto joga 2♠
                        vaza 1: beto vence
                        beto joga 7♠
                        ana joga 2♦
                        vaza 2: ana vence
                        ana joga J♥
                        beto joga 3♣
                        vaza 3: beto vence
                        rodada 2: beto marca 1
                        placar: ana 1 x 1 beto
                        rodada 3: ana dá as cartas, vale 1, vira 6♣
                        beto recebe 10♠ 4♠ 10♣
                        ana recebe 3♠ 2♥ 3♣
                        descarte encerrado
                        beto joga 10♠
                        ana joga 3♠
                        vaza 1: ana vence
                        ana joga 2♥
                        beto joga 4♠
                        vaza 2: ana vence
                        rodada 3: ana marca 1
                        placar: ana 2 x 1 beto
                        rodada 4: beto dá as cartas, vale 1, vira 2♠
                        ana recebe A♣ Q♦ 7♥
                        beto recebe 7♠ 6♣ 3♥""");
    }

    @Test
    @DisplayName("Seed 2026, cada jogador pede mais sempre que pode, senão escolhe a última ação: aumentos até o doze")
    void aumentosAteODoze() {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao(2026));
        List<String> linhas = new ArrayList<>(inicio(estado));
        while (estado.numeroDaRodada() <= 2) {
            JogadorId jogador = daVez(estado);
            List<Acao> legais = MOTOR.acoesLegais(estado, jogador);
            Acao escolhida = legais.contains(new PedirAumento()) ? new PedirAumento() : legais.getLast();
            Aplicada aplicada = aplicarAceita(estado, jogador, escolhida);
            aplicada.eventos().forEach(evento -> linhas.add(descrever(evento)));
            estado = aplicada.novoEstado();
        }

        // No doze, que é o teto, não há como pedir mais, e a última ação legal é correr.
        assertThat(String.join("\n", linhas)).isEqualTo("""
                        rodada 1: ana dá as cartas, vale 1, vira Q♦
                        beto recebe 5♥ 10♣ 7♦
                        ana recebe 2♣ 3♣ J♦
                        descarte encerrado
                        beto pede 3
                        ana aceita, a rodada vale 3
                        ana pede 6
                        beto aceita, a rodada vale 6
                        beto pede 9
                        ana aceita, a rodada vale 9
                        ana pede 12
                        beto corre
                        rodada 1: ana marca 9
                        placar: ana 9 x 0 beto
                        rodada 2: beto dá as cartas, vale 1, vira A♠
                        ana recebe 5♥ 2♦ J♥
                        beto recebe 2♠ 7♠ 3♣
                        descarte encerrado
                        ana pede 3
                        beto aceita, a rodada vale 3
                        beto pede 6
                        ana aceita, a rodada vale 6
                        ana pede 9
                        beto aceita, a rodada vale 9
                        beto pede 12
                        ana corre
                        rodada 2: beto marca 9
                        placar: ana 9 x 9 beto
                        rodada 3: ana dá as cartas, vale 1, vira 6♣
                        beto recebe 10♠ 4♠ 10♣
                        ana recebe 3♠ 2♥ 3♣""");
    }

    @Test
    @DisplayName("Seed 2026, cada jogador faz a primeira ação legal, mas corre de toda Rodada de Onze: partida até o"
            + " fim, passando pela Rodada de Onze e pela Escurinho")
    void partidaCompleta() {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao(2026));
        List<String> linhas = new ArrayList<>();
        while (!(estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada)) {
            JogadorId jogador = daVez(estado);
            Acao escolhida = estado.rodada().fase() instanceof FaseDaRodada.DecisaoRodadaDeOnze
                    ? new Correr()
                    : MOTOR.acoesLegais(estado, jogador).getFirst();
            Aplicada aplicada = aplicarAceita(estado, jogador, escolhida);
            aplicada.eventos().stream()
                    .filter(evento -> evento instanceof RodadaFinalizada
                            || evento instanceof RodadaAnulada
                            || evento instanceof RodadaDeOnzeIniciada
                            || evento instanceof RodadaEscurinhoIniciada
                            || evento instanceof JogadorCorreu
                            || evento instanceof PartidaFinalizada)
                    .forEach(evento -> linhas.add(descrever(evento)));
            estado = aplicada.novoEstado();
        }
        linhas.add(descrever(new PlacarAtualizado(estado.placar())));

        // Até a rodada 20, Ana vence 9 e Beto 11: Rodada de Onze para Beto, que corre duas vezes e leva Ana a 11. Com
        // 11 a 11 vem a Escurinho, e quem a vence (Beto) vence a partida.
        assertThat(String.join("\n", linhas)).isEqualTo("""
                        rodada 1: ana marca 1
                        rodada 2: beto marca 1
                        rodada 3: ana marca 1
                        rodada 4: ana marca 1
                        rodada 5: beto marca 1
                        rodada 6: ana marca 1
                        rodada 7: beto marca 1
                        rodada 8: ana marca 1
                        rodada 9: beto marca 1
                        rodada 10: ana marca 1
                        rodada 11: beto marca 1
                        rodada 12: beto marca 1
                        rodada 13: beto marca 1
                        rodada 14: ana marca 1
                        rodada 15: ana marca 1
                        rodada 16: ana marca 1
                        rodada 17: beto marca 1
                        rodada 18: beto marca 1
                        rodada 19: beto marca 1
                        rodada 20: beto marca 1
                        rodada de onze para beto
                        beto corre
                        rodada 21: ana marca 1
                        rodada de onze para beto
                        beto corre
                        rodada 22: ana marca 1
                        rodada escurinho
                        rodada 23: beto marca 1
                        partida finalizada: beto vence
                        placar: ana 11 x 12 beto""");
    }

    @Test
    @DisplayName("RG-2X2-*: seed 2026 em duplas; o primeiro a poder pede truco, a dupla que recebe quer ver as cartas e"
            + " aceita, e no resto cada um faz a primeira ação legal: eventos da 1ª rodada")
    void duplas() {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracaoEmDuplas(2026));
        List<String> linhas = new ArrayList<>(inicio(estado));
        while (estado.numeroDaRodada() == 1) {
            // Quem pode querer ver as cartas age antes de quem responde ao pedido.
            EstadoDaPartida agora = estado;
            JogadorId jogador = agora.configuracao().jogadoresNaOrdemDaMesa().stream()
                    .filter(candidato -> MOTOR.acoesLegais(agora, candidato).contains(new VerCartasDoParceiro()))
                    .findFirst()
                    .orElseGet(() -> daVez(agora));
            List<Acao> legais = MOTOR.acoesLegais(estado, jogador);
            Acao escolhida;
            if (legais.contains(new VerCartasDoParceiro())) {
                escolhida = new VerCartasDoParceiro();
            } else if (legais.contains(new Aceitar())) {
                escolhida = new Aceitar();
            } else if (legais.contains(new PedirAumento())
                    && estado.rodada().aposta().valor() == 1) {
                escolhida = new PedirAumento();
            } else {
                escolhida = legais.getFirst();
            }
            Aplicada aplicada = aplicarAceita(estado, jogador, escolhida);
            aplicada.eventos().forEach(evento -> linhas.add(descrever(evento)));
            estado = aplicada.novoEstado();
        }

        // Vira 6♣: a manilha é o 7. Ninguém tem o 4♦, então não há descarte. Davi abre e pede truco; recebe Ana, à
        // direita dele, e a dupla dela se vê antes de aceitar. O 3♣ da Carla vence a 1ª vaza; as manilhas do Davi
        // vencem a 2ª e a 3ª, e a dupla do Beto marca 3. O carteador passa para o Davi.
        assertThat(String.join("\n", linhas)).isEqualTo("""
                        rodada 1: carla dá as cartas, vale 1, vira 6♣
                        davi recebe 5♥ 7♦ 7♥
                        ana recebe 2♣ J♦ 2♠
                        beto recebe 10♣ Q♦ 10♥
                        carla recebe 3♣ Q♣ 10♦
                        descarte encerrado
                        davi pede 3
                        ana quer ver as cartas
                        carla quer ver as cartas
                        equipe ana vê as cartas
                        ana vê de carla: 3♣ Q♣ 10♦
                        carla vê de ana: 2♣ J♦ 2♠
                        ana aceita, a rodada vale 3
                        davi joga 5♥
                        ana joga 2♣
                        beto joga 10♣
                        carla joga 3♣
                        vaza 1: carla vence
                        carla joga Q♣
                        davi joga 7♦
                        ana joga J♦
                        beto joga Q♦
                        vaza 2: davi vence
                        davi joga 7♥
                        ana joga 2♠
                        beto joga 10♥
                        carla joga 10♦
                        vaza 3: davi vence
                        rodada 1: beto marca 3
                        placar: ana 0 x 3 beto
                        rodada 2: davi dá as cartas, vale 1, vira 7♥
                        ana recebe 5♥ J♥ 10♣
                        beto recebe 2♠ 3♣ 10♠
                        carla recebe 2♦ A♠ 5♠
                        davi recebe 7♠ 6♦ 2♥""");
    }

    /** A 1ª rodada, que já vem distribuída no estado inicial, descrita como os eventos das rodadas seguintes. */
    private static List<String> inicio(EstadoDaPartida estado) {
        Rodada rodada = estado.rodada();
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        List<String> linhas = new ArrayList<>();
        linhas.add(descrever(
                new RodadaIniciada(1, estado.carteador(), rodada.aposta().valor(), rodada.vira())));
        configuracao
                .jogadoresAPartirDe(configuracao.aDireitaDe(estado.carteador()))
                .forEach(jogador -> linhas.add(descrever(new CartasDistribuidas(jogador, rodada.maoDe(jogador)))));
        return linhas;
    }

    private static String descrever(Evento evento) {
        return switch (evento) {
            case RodadaIniciada iniciada ->
                "rodada %d: %s dá as cartas, vale %d, vira %s"
                        .formatted(
                                iniciada.numeroDaRodada(),
                                iniciada.carteador(),
                                iniciada.valor(),
                                iniciada.vira().orElseThrow());
            case CartasDistribuidas distribuidas ->
                "%s recebe %s"
                        .formatted(
                                distribuidas.jogador(),
                                distribuidas.cartas().stream()
                                        .map(Carta::toString)
                                        .collect(joining(" ")));
            case CartaJogada jogada -> "%s joga %s".formatted(jogada.jogador(), jogada.carta());
            case CartaEncobertaJogada encoberta -> "%s joga encoberta".formatted(encoberta.jogador());
            case CartaDescartada descartada -> "%s descarta %s".formatted(descartada.jogador(), descartada.carta());
            case CartaRecebidaPorDescarte recebida -> "%s compra %s".formatted(recebida.jogador(), recebida.carta());
            case DescarteEncerrado encerrado -> "descarte encerrado";
            case RodadaDeOnzeIniciada onze -> "rodada de onze para %s".formatted(onze.equipeComOnze());
            case RodadaEscurinhoIniciada escurinho -> "rodada escurinho";
            case PartidaFinalizada fim -> "partida finalizada: %s vence".formatted(fim.vencedora());
            case VazaFinalizada vaza ->
                "vaza %d: %s"
                        .formatted(
                                vaza.numeroDaVaza(),
                                switch (vaza.resultado()) {
                                    case Vencida vencida -> vencida.jogador() + " vence";
                                    case Empatada empatada -> "empate";
                                });
            case RodadaFinalizada finalizada ->
                "rodada %d: %s marca %d"
                        .formatted(finalizada.numeroDaRodada(), finalizada.vencedora(), finalizada.pontos());
            case RodadaAnulada anulada -> "rodada %d anulada".formatted(anulada.numeroDaRodada());
            case AumentoPedido pedido -> "%s pede %d".formatted(pedido.pedinte(), pedido.nivelProposto());
            case AumentoAceito aceito -> "%s aceita, a rodada vale %d".formatted(aceito.jogador(), aceito.valor());
            case JogadorCorreu correu -> "%s corre".formatted(correu.jogador());
            case QuisVerAsCartas quis -> "%s quer ver as cartas".formatted(quis.jogador());
            case DuplaViuAsCartas viu -> "equipe %s vê as cartas".formatted(viu.equipe());
            case CartasDoParceiroVistas vistas ->
                "%s vê de %s: %s"
                        .formatted(
                                vistas.jogador(),
                                vistas.parceiro(),
                                vistas.cartas().stream().map(Carta::toString).collect(joining(" ")));
            case PlacarAtualizado placar ->
                "placar: ana %d x %d beto"
                        .formatted(
                                placar.placar().pontosDe(EQUIPE_DA_ANA),
                                placar.placar().pontosDe(EQUIPE_DO_BETO));
        };
    }
}
