package io.github.joaomarcosvs.truco.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.web.Sala.Formato;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
 * Numa sala, cada pessoa recebe a própria visão, os eventos que pode ver e os textos da CLI. Nada disso pode mostrar a
 * ela as cartas dos outros, as do baralho ou as encobertas dos outros, nem a própria mão na Rodada Escurinho; no 2x2, a
 * mão do parceiro só aparece quando a dupla se vê. A seed também não pode sair do servidor.
 */
class InformacaoOcultaNaSalaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant INICIO = Instant.parse("2026-10-15T12:00:00Z");

    @Property(tries = 40)
    @Label("RG-VIS-1 a RG-VIS-3, RG-DESC-10 e RG-2X2-15: numa sala 1x1 de duas pessoas ou 2x2 de duas pessoas e dois"
            + " bots, em partidas inteiras com escolhas aleatórias, ninguém vê uma carta oculta para ele, a seed não"
            + " aparece e a partida termina")
    void nadaVaza(@ForAll boolean emDuplas, @ForAll long seed, @ForAll long sementeDasEscolhas) {
        Formato formato = emDuplas ? Formato.DUPLAS : Formato.UM_CONTRA_UM;
        Sala sala = new Sala("ABCDEF", formato, seed, "Ana", "token-ana", INICIO);
        sala.entrar("Beto", "token-beto", INICIO);
        if (emDuplas) {
            sala.comecar(new JogadorId("lugar1"), INICIO);
        }
        List<JogadorId> pessoas = List.of(new JogadorId("lugar1"), new JogadorId("lugar2"));
        Random escolhas = new Random(sementeDasEscolhas);
        Map<JogadorId, Integer> cursores = new HashMap<>();
        Map<JogadorId, EstadoDaPartida> estados = new HashMap<>();
        Instant agora = INICIO;
        for (int passo = 0; passo <= ServidorWeb.MAXIMO_DE_JOGADAS; passo++) {
            agora = agora.plus(Duration.ofSeconds(escolhas.nextInt(7)));
            Map<JogadorId, Sala.Vista> vistas = new HashMap<>();
            for (JogadorId pessoa : pessoas) {
                Sala.Vista vista = sala.vistaDe(pessoa, cursores.getOrDefault(pessoa, 0), agora);
                JsonNode resposta = JSON.readTree(RespostaJson.sala(sala.configuracao(), vista));
                CartasOcultas.conferirPassos(pessoa, vista.passos(), resposta.get("passos"));
                if (!vista.passos().isEmpty()) {
                    estados.put(pessoa, vista.passos().getLast().estado());
                }
                if (estados.containsKey(pessoa)) {
                    CartasOcultas.conferirAcoes(pessoa, estados.get(pessoa), resposta.get("acoesLegais"));
                }
                assertThat(resposta.has("seed")).isFalse();
                if (Math.abs(seed) > 1_000_000_000L) {
                    assertThat(resposta.toString()).doesNotContain(Long.toString(seed));
                }
                cursores.put(pessoa, vista.cursor());
                vistas.put(pessoa, vista);
            }
            if (vistas.get(pessoas.getFirst()).situacao() == Sala.Situacao.FINALIZADA) {
                return;
            }
            List<JogadorId> podemJogar = new ArrayList<>(pessoas.stream()
                    .filter(pessoa -> !vistas.get(pessoa).opcoes().isEmpty())
                    .toList());
            if (!podemJogar.isEmpty()) {
                JogadorId quemJoga = podemJogar.get(escolhas.nextInt(podemJogar.size()));
                List<Sala.Opcao> opcoes = vistas.get(quemJoga).opcoes();
                Sala.Opcao opcao = opcoes.get(escolhas.nextInt(opcoes.size()));
                assertThat(sala.jogar(quemJoga, opcao.acao(), cursores.get(quemJoga), agora))
                        .isInstanceOf(Sala.Aceita.class);
            }
        }
        throw new AssertionError("A partida não terminou");
    }
}
