package io.github.joaomarcosvs.truco.protocol;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A visão de um jogador no JSON, com os mesmos campos da {@code VisaoDoJogador}. */
record VisaoDto(
        String jogador,
        List<String> jogadoresNaOrdemDaMesa,
        List<Equipe> equipes,
        Map<String, Integer> placar,
        int numeroDaRodada,
        String carteador,
        TipoDeRodadaDto tipoDaRodada,
        Aposta aposta,
        Optional<CartaDto> vira,
        List<CartaDto> descartadas,
        Optional<CartaDto> cartaDaVezNoDescarte,
        List<CartaDto> mao,
        Map<String, List<CartaDto>> maosDosParceiros,
        Map<String, Integer> cartasNaMao,
        List<Vaza> vazas,
        List<Jogada> vazaAtual,
        Optional<String> vezDe,
        Optional<String> vencedoraDaPartida) {

    record Equipe(String id, List<String> jogadores) {}

    record Aposta(int valor, Optional<String> ultimaEquipeQueAceitou, Optional<PedidoDeAumento> pedidoPendente) {}

    record PedidoDeAumento(String pedinte, String respondedor, int nivelProposto, List<String> queremVerAsCartas) {}

    /** Uma jogada como o jogador a vê: a carta encoberta dos outros vem como {@code null}. */
    record Jogada(String jogador, Optional<CartaDto> carta, boolean encoberta) {}

    record Vaza(List<Jogada> jogadas, ResultadoDaVazaDto resultado) {}
}
