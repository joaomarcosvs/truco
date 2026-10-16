package io.github.joaomarcosvs.truco.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Um evento no JSON, com o campo {@code "tipo"}. A visibilidade não vai no JSON: o servidor só entrega a cada jogador os
 * eventos que ele pode ver.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "tipo")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EventoDto.RodadaIniciada.class, name = "RodadaIniciada"),
    @JsonSubTypes.Type(value = EventoDto.CartasDistribuidas.class, name = "CartasDistribuidas"),
    @JsonSubTypes.Type(value = EventoDto.CartaDescartada.class, name = "CartaDescartada"),
    @JsonSubTypes.Type(value = EventoDto.CartaRecebidaPorDescarte.class, name = "CartaRecebidaPorDescarte"),
    @JsonSubTypes.Type(value = EventoDto.DescarteEncerrado.class, name = "DescarteEncerrado"),
    @JsonSubTypes.Type(value = EventoDto.CartaJogada.class, name = "CartaJogada"),
    @JsonSubTypes.Type(value = EventoDto.CartaEncobertaJogada.class, name = "CartaEncobertaJogada"),
    @JsonSubTypes.Type(value = EventoDto.VazaFinalizada.class, name = "VazaFinalizada"),
    @JsonSubTypes.Type(value = EventoDto.AumentoPedido.class, name = "AumentoPedido"),
    @JsonSubTypes.Type(value = EventoDto.AumentoAceito.class, name = "AumentoAceito"),
    @JsonSubTypes.Type(value = EventoDto.JogadorCorreu.class, name = "JogadorCorreu"),
    @JsonSubTypes.Type(value = EventoDto.QuisVerAsCartas.class, name = "QuisVerAsCartas"),
    @JsonSubTypes.Type(value = EventoDto.DuplaViuAsCartas.class, name = "DuplaViuAsCartas"),
    @JsonSubTypes.Type(value = EventoDto.CartasDoParceiroVistas.class, name = "CartasDoParceiroVistas"),
    @JsonSubTypes.Type(value = EventoDto.RodadaFinalizada.class, name = "RodadaFinalizada"),
    @JsonSubTypes.Type(value = EventoDto.RodadaAnulada.class, name = "RodadaAnulada"),
    @JsonSubTypes.Type(value = EventoDto.PlacarAtualizado.class, name = "PlacarAtualizado"),
    @JsonSubTypes.Type(value = EventoDto.PartidaFinalizada.class, name = "PartidaFinalizada"),
    @JsonSubTypes.Type(value = EventoDto.RodadaDeOnzeIniciada.class, name = "RodadaDeOnzeIniciada"),
    @JsonSubTypes.Type(value = EventoDto.RodadaEscurinhoIniciada.class, name = "RodadaEscurinhoIniciada")
})
sealed interface EventoDto {

    record RodadaIniciada(int numeroDaRodada, String carteador, int valor, Optional<CartaDto> vira)
            implements EventoDto {}

    record CartasDistribuidas(String jogador, List<CartaDto> cartas) implements EventoDto {}

    record CartaDescartada(String jogador, CartaDto carta) implements EventoDto {}

    record CartaRecebidaPorDescarte(String jogador, CartaDto carta) implements EventoDto {}

    record DescarteEncerrado() implements EventoDto {}

    record CartaJogada(String jogador, CartaDto carta, int numeroDaVaza) implements EventoDto {}

    record CartaEncobertaJogada(String jogador, int numeroDaVaza) implements EventoDto {}

    record VazaFinalizada(int numeroDaVaza, ResultadoDaVazaDto resultado) implements EventoDto {}

    record AumentoPedido(String pedinte, int nivelProposto) implements EventoDto {}

    record AumentoAceito(String jogador, int valor) implements EventoDto {}

    record JogadorCorreu(String jogador) implements EventoDto {}

    record QuisVerAsCartas(String jogador) implements EventoDto {}

    record DuplaViuAsCartas(String equipe) implements EventoDto {}

    record CartasDoParceiroVistas(String jogador, String parceiro, List<CartaDto> cartas) implements EventoDto {}

    record RodadaFinalizada(int numeroDaRodada, String vencedora, int pontos) implements EventoDto {}

    record RodadaAnulada(int numeroDaRodada) implements EventoDto {}

    /** O placar é um objeto com os pontos de cada equipe, com as chaves em ordem alfabética. */
    record PlacarAtualizado(Map<String, Integer> placar) implements EventoDto {}

    record PartidaFinalizada(String vencedora, Map<String, Integer> placar) implements EventoDto {}

    record RodadaDeOnzeIniciada(String equipeComOnze) implements EventoDto {}

    record RodadaEscurinhoIniciada() implements EventoDto {}
}
