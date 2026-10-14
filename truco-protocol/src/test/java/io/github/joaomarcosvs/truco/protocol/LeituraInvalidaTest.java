package io.github.joaomarcosvs.truco.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** JSON inválido vira {@code Leitura.Invalida} com um motivo; ler nunca lança exceção. */
class LeituraInvalidaTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            texto vazio                       |
            não é JSON                        | jogar a primeira
            JSON incompleto                   | {"tipo":"JogarCarta"
            lista no lugar de objeto          | []
            null                              | null
            sem o campo tipo                  | {"indiceNaMao":0}
            tipo desconhecido                 | {"tipo":"Voar"}
            tipo de evento no lugar de ação   | {"tipo":"DescarteEncerrado"}
            campo faltando                    | {"tipo":"JogarCarta"}
            número como texto                 | {"tipo":"JogarCarta","indiceNaMao":"0"}
            número nulo                       | {"tipo":"JogarCarta","indiceNaMao":null}
            número quebrado                   | {"tipo":"JogarCarta","indiceNaMao":0.5}
            campo a mais                      | {"tipo":"JogarCarta","indiceNaMao":0,"carta":1}
            texto depois do JSON              | {"tipo":"Aceitar"} {"tipo":"Correr"}
            """)
    @DisplayName("Ações inválidas")
    void acoes(String caso, String json) {
        assertThat(ProtocoloJson.lerAcao(json == null ? "" : json)).isInstanceOf(Leitura.Invalida.class);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            carta inexistente                 | {"tipo":"CartaDescartada","jogador":"ana","carta":{"valor":"CORINGA","naipe":"COPAS"}}
            naipe faltando                    | {"tipo":"CartaDescartada","jogador":"ana","carta":{"valor":"AS"}}
            carta nula                        | {"tipo":"CartaDescartada","jogador":"ana","carta":null}
            jogador nulo                      | {"tipo":"JogadorCorreu","jogador":null}
            jogador vazio                     | {"tipo":"JogadorCorreu","jogador":" "}
            carta nula numa lista             | {"tipo":"CartasDistribuidas","jogador":"ana","cartas":[null]}
            placar negativo                   | {"tipo":"PlacarAtualizado","placar":{"ana":-1,"beto":0}}
            pontos nulos no placar            | {"tipo":"PlacarAtualizado","placar":{"ana":null}}
            resultado sem tipo                | {"tipo":"VazaFinalizada","numeroDaVaza":1,"resultado":{}}
            campo opcional ausente            | {"tipo":"RodadaIniciada","numeroDaRodada":1,"carteador":"ana","valor":1}
            """)
    @DisplayName("Eventos inválidos")
    void eventos(String caso, String json) {
        assertThat(ProtocoloJson.lerEvento(json)).isInstanceOf(Leitura.Invalida.class);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            objeto vazio                      | {}
            ação no lugar de visão            | {"tipo":"Aceitar"}
            """)
    @DisplayName("Visões inválidas")
    void visoes(String caso, String json) {
        assertThat(ProtocoloJson.lerVisao(json)).isInstanceOf(Leitura.Invalida.class);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            tipo desconhecido     | {"tipo":"Voar"}                               | Voar
            campo faltando        | {"tipo":"JogarCarta"}                         | indiceNaMao
            campo a mais          | {"tipo":"JogarCarta","indiceNaMao":0,"x":1}    | x
            """)
    @DisplayName("O motivo diz o que estava errado")
    void motivo(String caso, String json, String trecho) {
        assertThat(ProtocoloJson.lerAcao(json))
                .isInstanceOfSatisfying(
                        Leitura.Invalida.class,
                        invalida -> assertThat(invalida.motivo()).contains(trecho));
    }

    @Test
    @DisplayName("Uma visão com um campo a menos é inválida")
    void visaoIncompleta() {
        MotorDeTruco motor = MotorDeTruco.novo();
        JogadorId ana = new JogadorId("ana");
        ConfiguracaoDaPartida configuracao = new ConfiguracaoDaPartida(
                List.of(Equipe.individual(ana), Equipe.individual(new JogadorId("beto"))), new TrucoPaulista(), 2026);
        String visao = ProtocoloJson.escrever(motor.visaoDe(motor.novaPartida(configuracao), ana));

        String semAMao = visao.replaceFirst("\"mao\":\\[[^\\]]*\\],", "");

        assertThat(semAMao).isNotEqualTo(visao);
        assertThat(ProtocoloJson.lerVisao(semAMao)).isInstanceOf(Leitura.Invalida.class);
    }
}
