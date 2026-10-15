package io.github.joaomarcosvs.truco.web;

import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.BOT;
import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.MOTOR;
import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.VOCE;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.cli.Narrador;
import io.github.joaomarcosvs.truco.cli.Notacao;
import io.github.joaomarcosvs.truco.cli.Tela;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.protocol.ProtocoloJson;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Passo;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Reconstituida;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A resposta da API para a página: visões, eventos e ações no formato do protocolo ({@code docs/protocolo.md}), cada
 * evento e cada ação com o texto que a CLI mostraria.
 *
 * <pre>{@code
 * {"seed": 2026,
 *  "passos": [{"eventos": [{"evento": {...}, "texto": "Bot jogou 5♥."}], "visao": {...}}],
 *  "acoesLegais": [{"acao": {"tipo": "JogarCarta", "indiceNaMao": 0}, "texto": "Jogar 2♣"}]}
 * }</pre>
 */
final class RespostaJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<JogadorId, String> NOMES = Map.of(VOCE, "você", BOT, "bot");

    private RespostaJson() {}

    static String partida(long seed, Reconstituida partida) {
        ConfiguracaoDaPartida configuracao = PartidaReconstituida.configuracao(seed);
        Narrador narrador = new Narrador(VOCE, NOMES, configuracao, Notacao.BARALHO_ESPANHOL);
        Tela tela = new Tela(narrador, configuracao.variante());

        ObjectNode resposta = JSON.createObjectNode();
        resposta.put("seed", seed);
        ArrayNode passos = resposta.putArray("passos");
        for (Passo passo : partida.passos()) {
            ObjectNode json = passos.addObject();
            ArrayNode eventos = json.putArray("eventos");
            for (Evento evento : passo.eventos()) {
                eventos.addObject()
                        .<ObjectNode>set("evento", arvore(ProtocoloJson.escrever(evento)))
                        .put("texto", narrador.descrever(evento));
            }
            json.set("visao", arvore(ProtocoloJson.escrever(passo.suaVisao())));
        }
        VisaoDoJogador visao = MOTOR.visaoDe(partida.estado(), VOCE);
        ArrayNode acoes = resposta.putArray("acoesLegais");
        for (Acao acao : MOTOR.acoesLegais(partida.estado(), VOCE)) {
            acoes.addObject()
                    .<ObjectNode>set("acao", arvore(ProtocoloJson.escrever(acao)))
                    .put("texto", tela.opcao(acao, visao));
        }
        return JSON.writeValueAsString(resposta);
    }

    static String erro(String mensagem) {
        return JSON.writeValueAsString(JSON.createObjectNode().put("erro", mensagem));
    }

    private static JsonNode arvore(String json) {
        return JSON.readTree(json);
    }
}
