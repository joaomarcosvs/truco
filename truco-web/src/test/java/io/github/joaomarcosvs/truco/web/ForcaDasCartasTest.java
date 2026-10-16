package io.github.joaomarcosvs.truco.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.web.Sala.Formato;
import io.github.joaomarcosvs.truco.web.Sala.Situacao;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A força das cartas, que a página usa para empilhar as cartas de cada vaza sem saber as regras. Ela é conferida contra
 * a ordem de força do Truco Paulista, com a vira da rodada em que cada carta foi jogada.
 */
class ForcaDasCartasTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant AGORA = Instant.parse("2026-10-16T12:00:00Z");
    private static final JogadorId ANA = new JogadorId("lugar1");
    private static final OrdemDeForca ORDEM = new TrucoPaulista().ordemDeForca();

    @Test
    @DisplayName("RG-CARTAS-2, RG-CARTAS-5 e RG-ENC-2: numa partida 2x2 inteira, cada carta aberta vai com a força que"
            + " tinha na rodada em que foi jogada, também na jogada que encerra a rodada, e as encobertas vão sem"
            + " força")
    void forcasDaPartidaInteira() {
        Sala sala = new Sala("ABCDEF", Formato.DUPLAS, 2026, "Ana", "token-ana", AGORA);
        sala.comecar(ANA, AGORA);
        Instant agora = AGORA;
        while (sala.vistaDe(ANA, 0, agora).situacao() == Situacao.JOGANDO) {
            agora = agora.plusSeconds(1);
            Sala.Vista vista = sala.vistaDe(ANA, 0, agora);
            if (!vista.opcoes().isEmpty()) {
                sala.jogar(ANA, vista.opcoes().getFirst().acao(), vista.cursor(), agora);
            }
        }
        JsonNode passos = JSON.readTree(RespostaJson.sala(sala.configuracao(), sala.vistaDe(ANA, 0, agora)))
                .get("passos");

        Optional<Carta> viraAnterior = Optional.empty();
        int jogadasQueEncerraramRodada = 0;
        int encobertas = 0;
        for (JsonNode passo : passos) {
            boolean rodadaNova = false;
            for (JsonNode evento : passo.get("eventos")) {
                JsonNode dados = evento.get("evento");
                switch (dados.get("tipo").asString()) {
                    case "CartaJogada" -> {
                        Carta carta = carta(dados.get("carta"));
                        assertThat(evento.get("forca").asInt())
                                .as("força de %s", carta)
                                .isEqualTo(ORDEM.forca(carta, viraAnterior));
                        if (rodadaNova) {
                            throw new AssertionError("Carta jogada depois do começo da rodada, no mesmo passo");
                        }
                    }
                    case "RodadaIniciada" -> {
                        rodadaNova = true;
                        if (passo.toString().contains("\"CartaJogada\"")) {
                            jogadasQueEncerraramRodada++;
                        }
                    }
                    default -> {}
                }
            }
            JsonNode visao = passo.get("visao");
            Optional<Carta> vira =
                    visao.get("vira").isNull() ? Optional.empty() : Optional.of(carta(visao.get("vira")));
            JsonNode forcas = passo.get("forcas");
            for (int i = 0; i < visao.get("vazas").size(); i++) {
                encobertas += conferir(visao.get("vazas").get(i).get("jogadas"), forcas.get(i), vira);
            }
            encobertas += conferir(
                    visao.get("vazaAtual"), forcas.get(visao.get("vazas").size()), vira);
            viraAnterior = vira;
        }

        assertThat(jogadasQueEncerraramRodada)
                .as("jogadas que encerraram uma rodada")
                .isPositive();
        assertThat(encobertas).as("encobertas vistas").isPositive();
    }

    /** Confere as forças de uma vaza e devolve quantas encobertas ela tem. */
    private static int conferir(JsonNode jogadas, JsonNode forcas, Optional<Carta> vira) {
        int encobertas = 0;
        for (int j = 0; j < jogadas.size(); j++) {
            JsonNode jogada = jogadas.get(j);
            if (jogada.get("encoberta").asBoolean()) {
                assertThat(forcas.get(j).isNull()).isTrue();
                encobertas++;
            } else {
                assertThat(forcas.get(j).asInt()).isEqualTo(ORDEM.forca(carta(jogada.get("carta")), vira));
            }
        }
        return encobertas;
    }

    private static Carta carta(JsonNode json) {
        return new Carta(
                Valor.valueOf(json.get("valor").asString()),
                Naipe.valueOf(json.get("naipe").asString()));
    }
}
