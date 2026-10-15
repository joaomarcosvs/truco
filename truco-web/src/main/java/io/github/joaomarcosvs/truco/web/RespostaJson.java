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
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Reconstituida;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * As respostas da API para a página: visões, eventos e ações no formato do protocolo ({@code docs/protocolo.md}), cada
 * evento e cada ação com o texto que a CLI mostraria.
 *
 * <p>Contra o bot:
 *
 * <pre>{@code
 * {"seed": 2026,
 *  "passos": [{"eventos": [{"evento": {...}, "texto": "Bot jogou 5 de copas."}], "visao": {...}}],
 *  "acoesLegais": [{"acao": {"tipo": "JogarCarta", "indiceNaMao": 0}, "texto": "Jogar 2 de bastos"}]}
 * }</pre>
 *
 * <p>Numa sala, sem a seed, que não sai do servidor:
 *
 * <pre>{@code
 * {"situacao": "jogando", "voce": "convidado", "cursor": 15, "passos": [...], "acoesLegais": [...]}
 * }</pre>
 */
final class RespostaJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RespostaJson() {}

    static String partida(long seed, Reconstituida partida) {
        ConfiguracaoDaPartida configuracao = PartidaReconstituida.configuracao(seed);
        Narrador narrador =
                new Narrador(VOCE, Map.of(VOCE, "você", BOT, "bot"), configuracao, Notacao.BARALHO_ESPANHOL);
        Tela tela = new Tela(narrador, configuracao.variante());

        ObjectNode resposta = JSON.createObjectNode();
        resposta.put("seed", seed);
        escreverPassos(resposta.putArray("passos"), partida.passos(), VOCE, narrador);
        VisaoDoJogador visao = MOTOR.visaoDe(partida.estado(), VOCE);
        ArrayNode acoes = resposta.putArray("acoesLegais");
        for (Acao acao : MOTOR.acoesLegais(partida.estado(), VOCE)) {
            escreverAcao(acoes, acao, tela.opcao(acao, visao));
        }
        return JSON.writeValueAsString(resposta);
    }

    /** O que um jogador vê da sala. O outro jogador é chamado de "adversário". */
    static String sala(ConfiguracaoDaPartida configuracao, Sala.Vista vista) {
        JogadorId voce = vista.voce();
        JogadorId adversario = configuracao.jogadoresNaOrdemDaMesa().stream()
                .filter(jogador -> !jogador.equals(voce))
                .findFirst()
                .orElseThrow();
        Narrador narrador = new Narrador(
                voce, Map.of(voce, "você", adversario, "adversário"), configuracao, Notacao.BARALHO_ESPANHOL);
        Tela tela = new Tela(narrador, configuracao.variante());

        ObjectNode resposta = JSON.createObjectNode();
        resposta.put("situacao", situacao(vista.situacao()));
        resposta.put("voce", voce.valor());
        resposta.put("cursor", vista.cursor());
        escreverPassos(resposta.putArray("passos"), vista.passos(), voce, narrador);
        ArrayNode acoes = resposta.putArray("acoesLegais");
        for (Sala.Opcao opcao : vista.opcoes()) {
            escreverAcao(acoes, opcao.acao(), tela.opcao(opcao.acao(), opcao.visao()));
        }
        return JSON.writeValueAsString(resposta);
    }

    /** A sala criada ou em que você entrou: o código, o seu token e quem você é na partida. */
    static String entrada(String codigo, String token, JogadorId voce) {
        return JSON.writeValueAsString(JSON.createObjectNode()
                .put("codigo", codigo)
                .put("token", token)
                .put("voce", voce.valor()));
    }

    static String erro(String mensagem) {
        return JSON.writeValueAsString(JSON.createObjectNode().put("erro", mensagem));
    }

    private static String situacao(Sala.Situacao situacao) {
        return switch (situacao) {
            case ESPERANDO_ADVERSARIO -> "esperando-adversario";
            case JOGANDO -> "jogando";
            case FINALIZADA -> "finalizada";
        };
    }

    private static void escreverPassos(ArrayNode json, List<Passo> passos, JogadorId voce, Narrador narrador) {
        for (Passo passo : passos) {
            ObjectNode passoJson = json.addObject();
            ArrayNode eventos = passoJson.putArray("eventos");
            for (Evento evento : passo.eventos()) {
                eventos.addObject()
                        .<ObjectNode>set("evento", arvore(ProtocoloJson.escrever(evento)))
                        .put("texto", narrador.descrever(evento));
            }
            passoJson.set("visao", arvore(ProtocoloJson.escrever(passo.visaoDe(voce))));
        }
    }

    private static void escreverAcao(ArrayNode json, Acao acao, String texto) {
        json.addObject()
                .<ObjectNode>set("acao", arvore(ProtocoloJson.escrever(acao)))
                .put("texto", texto);
    }

    private static JsonNode arvore(String json) {
        return JSON.readTree(json);
    }
}
