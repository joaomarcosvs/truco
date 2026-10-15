package io.github.joaomarcosvs.truco.web;

import static io.github.joaomarcosvs.truco.web.Sala.ANFITRIAO;
import static io.github.joaomarcosvs.truco.web.Sala.CONVIDADO;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Numa sala, cada jogador recebe a própria visão, os eventos que pode ver e os textos da CLI. Nada disso pode mostrar a
 * ele as cartas do adversário, as do baralho ou as encobertas do adversário, nem a própria mão na Rodada Escurinho. A
 * seed também não pode sair do servidor.
 */
class InformacaoOcultaNaSalaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant AGORA = Instant.parse("2026-10-15T12:00:00Z");

    @Property(tries = 40)
    @Label("RG-VIS-1 a RG-VIS-3 e RG-DESC-10: numa sala, em partidas inteiras com escolhas aleatórias dos dois"
            + " jogadores, ninguém vê uma carta oculta para ele, a seed não aparece e a partida termina")
    void nadaVaza(@ForAll long seed, @ForAll long sementeDasEscolhas) {
        Sala sala = new Sala("ABCDEF", seed, "token-anfitriao", AGORA);
        sala.entrar("token-convidado", AGORA);
        Random escolhas = new Random(sementeDasEscolhas);
        Map<JogadorId, Integer> cursores = new HashMap<>(Map.of(ANFITRIAO, 0, CONVIDADO, 0));
        Map<JogadorId, EstadoDaPartida> estados = new HashMap<>();
        for (int jogadas = 0; jogadas <= ServidorWeb.MAXIMO_DE_JOGADAS; jogadas++) {
            Map<JogadorId, Sala.Vista> vistas = new HashMap<>();
            for (JogadorId jogador : List.of(ANFITRIAO, CONVIDADO)) {
                Sala.Vista vista = sala.vistaDe(jogador, cursores.get(jogador));
                JsonNode resposta = JSON.readTree(RespostaJson.sala(sala.configuracao(), vista));
                CartasOcultas.conferirPassos(jogador, vista.passos(), resposta.get("passos"));
                if (!vista.passos().isEmpty()) {
                    estados.put(jogador, vista.passos().getLast().estado());
                }
                CartasOcultas.conferirAcoes(jogador, estados.get(jogador), resposta.get("acoesLegais"));
                assertThat(resposta.has("seed")).isFalse();
                if (Math.abs(seed) > 1_000_000_000L) {
                    assertThat(resposta.toString()).doesNotContain(Long.toString(seed));
                }
                cursores.put(jogador, vista.cursor());
                vistas.put(jogador, vista);
            }
            if (vistas.get(ANFITRIAO).situacao() == Sala.Situacao.FINALIZADA) {
                return;
            }
            List<JogadorId> podemJogar = List.of(ANFITRIAO, CONVIDADO).stream()
                    .filter(jogador -> !vistas.get(jogador).opcoes().isEmpty())
                    .toList();
            JogadorId quemJoga = podemJogar.get(escolhas.nextInt(podemJogar.size()));
            List<Sala.Opcao> opcoes = vistas.get(quemJoga).opcoes();
            Sala.Opcao opcao = opcoes.get(escolhas.nextInt(opcoes.size()));
            assertThat(sala.jogar(quemJoga, opcao.acao(), cursores.get(quemJoga), AGORA))
                    .isInstanceOf(Sala.Aceita.class);
        }
        throw new AssertionError("A partida não terminou");
    }
}
