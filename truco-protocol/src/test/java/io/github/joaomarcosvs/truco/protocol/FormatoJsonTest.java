package io.github.joaomarcosvs.truco.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.Placar;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O JSON exato de alguns valores, para fixar as decisões de formato (os outros exemplos estão na documentação). */
class FormatoJsonTest {

    private static final JogadorId ANA = new JogadorId("ana");
    private static final EquipeId EQUIPE_DA_ANA = new EquipeId("ana");
    private static final EquipeId EQUIPE_DO_BETO = new EquipeId("beto");

    @Test
    @DisplayName("Ação: o campo \"tipo\" com o nome da ação, seguido dos campos; sem campos, só o tipo")
    void acoes() {
        assertThat(ProtocoloJson.escrever(new JogarCarta(2))).isEqualTo("{\"tipo\":\"JogarCarta\",\"indiceNaMao\":2}");
        assertThat(ProtocoloJson.escrever(new Aceitar())).isEqualTo("{\"tipo\":\"Aceitar\"}");
    }

    @Test
    @DisplayName("Carta: valor e naipe pelos nomes dos enums, só em ASCII")
    void carta() {
        CartaJogada jogada = new CartaJogada(ANA, new Carta(Valor.TRES, Naipe.ESPADAS), 1);

        assertThat(ProtocoloJson.escrever(jogada))
                .isEqualTo("{\"tipo\":\"CartaJogada\",\"jogador\":\"ana\",\"carta\":{\"valor\":\"TRES\",\"naipe\":"
                        + "\"ESPADAS\"},\"numeroDaVaza\":1}");
    }

    @Test
    @DisplayName("Um valor ausente sai como null: o campo está sempre lá")
    void opcionalVazio() {
        assertThat(ProtocoloJson.escrever(new RodadaIniciada(1, ANA, 1, Optional.empty())))
                .isEqualTo("{\"tipo\":\"RodadaIniciada\",\"numeroDaRodada\":1,\"carteador\":\"ana\",\"valor\":1,"
                        + "\"vira\":null}");
    }

    @Test
    @DisplayName("Variantes dentro de outros valores também levam \"tipo\"")
    void varianteAninhada() {
        assertThat(ProtocoloJson.escrever(new VazaFinalizada(2, new ResultadoDaVaza.Empatada())))
                .isEqualTo("{\"tipo\":\"VazaFinalizada\",\"numeroDaVaza\":2,\"resultado\":{\"tipo\":\"Empatada\"}}");
    }

    @Test
    @DisplayName("O placar sai com as equipes em ordem alfabética, seja qual for a ordem do mapa")
    void placarOrdenado() {
        Map<EquipeId, Integer> betoPrimeiro = new LinkedHashMap<>();
        betoPrimeiro.put(EQUIPE_DO_BETO, 1);
        betoPrimeiro.put(EQUIPE_DA_ANA, 3);

        String json = "{\"tipo\":\"PlacarAtualizado\",\"placar\":{\"ana\":3,\"beto\":1}}";
        assertThat(ProtocoloJson.escrever(new PlacarAtualizado(new Placar(betoPrimeiro))))
                .isEqualTo(json);
        assertThat(ProtocoloJson.escrever(
                        new PlacarAtualizado(new Placar(Map.of(EQUIPE_DA_ANA, 3, EQUIPE_DO_BETO, 1)))))
                .isEqualTo(json);
    }

    @Test
    @DisplayName("A leitura aceita espaços e quebras de linha")
    void leituraComEspacos() {
        assertThat(ProtocoloJson.lerAcao("  {\n  \"tipo\" : \"JogarCarta\",\n  \"indiceNaMao\" : 1\n}\n"))
                .isEqualTo(new Leitura.Lida<>(new JogarCarta(1)));
    }
}
