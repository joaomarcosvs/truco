package io.github.joaomarcosvs.truco.cli;

import static java.util.stream.Collectors.joining;

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
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.Placar;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Conta a partida em português, do ponto de vista de um jogador ("você"). Só deve receber os eventos que esse jogador
 * pode ver ({@code Evento.visivelPara}).
 */
final class Narrador {

    private final JogadorId voce;
    private final Map<JogadorId, String> nomes;
    private final ConfiguracaoDaPartida configuracao;
    private final Notacao notacao;

    /**
     * @param voce o jogador do ponto de vista da narração
     * @param nomes como cada jogador é chamado no meio de uma frase, como "você" e "bot"
     */
    Narrador(JogadorId voce, Map<JogadorId, String> nomes, ConfiguracaoDaPartida configuracao, Notacao notacao) {
        this.voce = voce;
        this.nomes = Map.copyOf(nomes);
        this.configuracao = configuracao;
        this.notacao = notacao;
    }

    /** Uma frase sobre o evento, começando com maiúscula. */
    String descrever(Evento evento) {
        String frase = switch (evento) {
            case RodadaIniciada iniciada ->
                "rodada %d: %s dá as cartas%s."
                        .formatted(
                                iniciada.numeroDaRodada(),
                                nome(iniciada.carteador()),
                                iniciada.vira()
                                        .map(vira -> ", vira " + carta(vira))
                                        .orElse(""));
            case CartasDistribuidas distribuidas ->
                "%s recebeu %s.".formatted(nome(distribuidas.jogador()), cartas(distribuidas.cartas()));
            case CartaDescartada descartada ->
                "%s descartou %s.".formatted(nome(descartada.jogador()), carta(descartada.carta()));
            case CartaRecebidaPorDescarte recebida ->
                "%s comprou %s.".formatted(nome(recebida.jogador()), carta(recebida.carta()));
            case DescarteEncerrado encerrado -> "fim do descarte.";
            case CartaJogada jogada -> "%s jogou %s.".formatted(nome(jogada.jogador()), carta(jogada.carta()));
            case CartaEncobertaJogada encoberta -> "%s jogou uma carta encoberta.".formatted(nome(encoberta.jogador()));
            case VazaFinalizada vaza -> "vaza %d: %s.".formatted(vaza.numeroDaVaza(), resultado(vaza.resultado()));
            case AumentoPedido pedido ->
                "%s pediu aumento para %d.".formatted(nome(pedido.pedinte()), pedido.nivelProposto());
            case AumentoAceito aceito ->
                "%s aceitou: a rodada vale %d.".formatted(nome(aceito.jogador()), aceito.valor());
            case JogadorCorreu correu -> "%s correu.".formatted(nome(correu.jogador()));
            case QuisVerAsCartas quis -> "%s quer ver as cartas do parceiro.".formatted(nome(quis.jogador()));
            case DuplaViuAsCartas viu -> "%s viu as cartas um do outro.".formatted(nome(viu.equipe()));
            case CartasDoParceiroVistas vistas ->
                "%s viu as cartas de %s: %s."
                        .formatted(nome(vistas.jogador()), nome(vistas.parceiro()), cartas(vistas.cartas()));
            case RodadaFinalizada finalizada ->
                "rodada %d: %s marcou %s."
                        .formatted(
                                finalizada.numeroDaRodada(), nome(finalizada.vencedora()), pontos(finalizada.pontos()));
            case RodadaAnulada anulada ->
                "rodada %d anulada: ninguém marcou ponto.".formatted(anulada.numeroDaRodada());
            case PlacarAtualizado placar -> "placar: %s.".formatted(placar(placar.placar()));
            case PartidaFinalizada fim ->
                "fim de partida: %s venceu por %d a %d."
                        .formatted(
                                nome(fim.vencedora()),
                                fim.placar().pontosDe(fim.vencedora()),
                                outrasEquipes(fim.vencedora())
                                        .mapToInt(equipe -> fim.placar().pontosDe(equipe))
                                        .max()
                                        .orElse(0));
            case RodadaDeOnzeIniciada onze ->
                "rodada de Onze: %s decide se joga ou corre.".formatted(nome(onze.equipeComOnze()));
            case RodadaEscurinhoIniciada escurinho ->
                "rodada Escurinho: ninguém vê as próprias cartas, e quem vencer a rodada vence a partida.";
        };
        return comMaiuscula(frase);
    }

    /** O placar com a sua equipe primeiro, como "você 3 x 2 bot". */
    String placar(Placar placar) {
        EquipeId suaEquipe = configuracao.equipeDe(voce);
        return Stream.concat(Stream.of(suaEquipe), outrasEquipes(suaEquipe))
                .map(equipe -> {
                    String pontos = String.valueOf(placar.pontosDe(equipe));
                    return equipe.equals(suaEquipe) ? nome(equipe) + " " + pontos : pontos + " " + nome(equipe);
                })
                .collect(joining(" x "));
    }

    String nome(JogadorId jogador) {
        return nomes.getOrDefault(jogador, jogador.valor());
    }

    /** O nome da equipe: no 1x1, o do jogador dela; no 2x2, "sua dupla" ou "dupla adversária". */
    String nome(EquipeId equipe) {
        return configuracao.equipes().stream()
                .filter(candidata -> candidata.id().equals(equipe))
                .findFirst()
                .map(candidata -> candidata.jogadores().size() == 1
                        ? nome(candidata.jogadores().getFirst())
                        : candidata.jogadores().contains(voce) ? "sua dupla" : "dupla adversária")
                .orElse(equipe.valor());
    }

    /** O nome da equipe adversária: o truco tem sempre duas. */
    String nomeDoAdversario() {
        return nome(outrasEquipes(configuracao.equipeDe(voce)).findFirst().orElseThrow());
    }

    String carta(Carta carta) {
        return notacao.de(carta);
    }

    String cartas(List<Carta> cartas) {
        return cartas.stream().map(this::carta).collect(joining(" "));
    }

    static String pontos(int pontos) {
        return pontos == 1 ? "1 ponto" : pontos + " pontos";
    }

    static String comMaiuscula(String frase) {
        return frase.isEmpty() ? frase : Character.toUpperCase(frase.charAt(0)) + frase.substring(1);
    }

    /** Quem venceu a vaza, ou "empate". */
    String resultado(ResultadoDaVaza resultado) {
        return switch (resultado) {
            case ResultadoDaVaza.Vencida vencida -> nome(vencida.jogador()) + " venceu";
            case ResultadoDaVaza.Empatada empatada -> "empate";
        };
    }

    private Stream<EquipeId> outrasEquipes(EquipeId equipe) {
        return configuracao.equipes().stream().map(Equipe::id).filter(outra -> !outra.equals(equipe));
    }
}
