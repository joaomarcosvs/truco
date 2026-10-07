package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.aplicarAceita;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracao;
import static io.github.joaomarcosvs.truco.partida.Partidas.daVez;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.AumentoAceito;
import io.github.joaomarcosvs.truco.evento.AumentoPedido;
import io.github.joaomarcosvs.truco.evento.CartaEncobertaJogada;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.JogadorCorreu;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.RodadaAnulada;
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
    @DisplayName("Seed 2026, cada jogador sempre joga a primeira carta da mão: eventos até o início da 4ª rodada")
    void tresRodadas() {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao(2026));
        List<String> linhas = new ArrayList<>(inicio(estado));
        while (estado.numeroDaRodada() <= 3) {
            Aplicada aplicada = aplicarAceita(estado, daVez(estado), new JogarCarta(0));
            aplicada.eventos().forEach(evento -> linhas.add(descrever(evento)));
            estado = aplicada.novoEstado();
        }

        // Manilhas: J na rodada 1 (vira Q♦), 2 na rodada 2 (vira A♠) e 7 na rodada 3 (vira 6♣).
        assertThat(String.join("\n", linhas)).isEqualTo("""
                        rodada 1: ana dá as cartas, vale 1, vira Q♦
                        beto recebe 5♥ 10♣ 7♦
                        ana recebe 2♣ 3♣ J♦
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
            case PlacarAtualizado placar ->
                "placar: ana %d x %d beto"
                        .formatted(
                                placar.placar().pontosDe(EQUIPE_DA_ANA),
                                placar.placar().pontosDe(EQUIPE_DO_BETO));
        };
    }
}
