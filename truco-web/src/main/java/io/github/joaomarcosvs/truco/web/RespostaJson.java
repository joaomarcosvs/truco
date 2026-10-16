package io.github.joaomarcosvs.truco.web;

import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.BOT;
import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.MOTOR;
import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.VOCE;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.cli.Narrador;
import io.github.joaomarcosvs.truco.cli.Notacao;
import io.github.joaomarcosvs.truco.cli.Tela;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.protocol.ProtocoloJson;
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import io.github.joaomarcosvs.truco.visao.JogadaVisivel;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Reconstituida;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * {"situacao": "finalizada", "formato": "2x2", "voce": "lugar3", "anfitriao": "lugar1",
 *  "lugares": [{"id": "lugar1", "equipe": "dupla1", "nome": "Ana", "bot": false}, ...],
 *  "partida": 1, "queremJogarDeNovo": ["lugar1"], "sairam": [],
 *  "cursor": 15, "passos": [...], "acoesLegais": [...]}
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
        escreverPassos(
                resposta.putArray("passos"),
                partida.passos(),
                VOCE,
                narrador,
                configuracao.variante().ordemDeForca());
        VisaoDoJogador visao = MOTOR.visaoDe(partida.estado(), VOCE);
        ArrayNode acoes = resposta.putArray("acoesLegais");
        for (Acao acao : MOTOR.acoesLegais(partida.estado(), VOCE)) {
            escreverAcao(acoes, acao, tela.opcao(acao, visao));
        }
        return JSON.writeValueAsString(resposta);
    }

    /** O que um jogador vê da sala. Nos textos, os outros jogadores aparecem pelo nome; as duplas, como no narrador. */
    static String sala(ConfiguracaoDaPartida configuracao, Sala.Vista vista) {
        JogadorId voce = vista.voce();
        Map<JogadorId, String> nomes = new HashMap<>();
        vista.lugares().forEach(lugar -> lugar.nome().ifPresent(nome -> nomes.put(lugar.id(), nome)));
        nomes.put(voce, "você");
        Narrador narrador = new Narrador(voce, nomes, configuracao, Notacao.BARALHO_ESPANHOL);
        Tela tela = new Tela(narrador, configuracao.variante());

        ObjectNode resposta = JSON.createObjectNode();
        resposta.put("situacao", situacao(vista.situacao()));
        resposta.put("formato", vista.formato() == Sala.Formato.DUPLAS ? "2x2" : "1x1");
        resposta.put("voce", voce.valor());
        resposta.put("anfitriao", vista.anfitriao().valor());
        ArrayNode lugares = resposta.putArray("lugares");
        for (Sala.Lugar lugar : vista.lugares()) {
            lugares.addObject()
                    .put("id", lugar.id().valor())
                    .put("equipe", lugar.equipe().valor())
                    .put("nome", lugar.nome().orElse(null))
                    .put("bot", lugar.bot());
        }
        resposta.put("partida", vista.partida());
        ArrayNode queremJogarDeNovo = resposta.putArray("queremJogarDeNovo");
        vista.queremJogarDeNovo().forEach(lugar -> queremJogarDeNovo.add(lugar.valor()));
        ArrayNode sairam = resposta.putArray("sairam");
        vista.sairam().forEach(lugar -> sairam.add(lugar.valor()));
        resposta.put("cursor", vista.cursor());
        escreverPassos(
                resposta.putArray("passos"),
                vista.passos(),
                voce,
                narrador,
                configuracao.variante().ordemDeForca());
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
            case ESPERANDO_JOGADORES -> "esperando-jogadores";
            case JOGANDO -> "jogando";
            case FINALIZADA -> "finalizada";
        };
    }

    /**
     * Os passos, com a força de cada carta aberta jogada, para a página empilhar as cartas de cada vaza sem saber as
     * regras (RG-CARTAS-2, RG-CARTAS-4 e RG-CARTAS-5: a força depende da vira). Cada {@code CartaJogada} leva a
     * {@code "forca"} da carta na rodada em que foi jogada. A visão vem com {@code "forcas"}: uma lista por vaza (as
     * terminadas e, por último, a atual), na ordem das jogadas, com {@code null} nas encobertas, que não contam
     * (RG-ENC-2) e cuja força revelaria algo da carta (RG-ENC-3). Maior vence; igual empata.
     */
    private static void escreverPassos(
            ArrayNode json, List<Passo> passos, JogadorId voce, Narrador narrador, OrdemDeForca ordem) {
        for (Passo passo : passos) {
            ObjectNode passoJson = json.addObject();
            ArrayNode eventos = passoJson.putArray("eventos");
            for (Evento evento : passo.eventos()) {
                ObjectNode eventoJson = eventos.addObject()
                        .<ObjectNode>set("evento", arvore(ProtocoloJson.escrever(evento)))
                        .put("texto", narrador.descrever(evento));
                if (evento instanceof CartaJogada jogada) {
                    eventoJson.put("forca", ordem.forca(jogada.carta(), passo.viraAntes()));
                }
            }
            VisaoDoJogador visao = passo.visaoDe(voce);
            passoJson.set("visao", arvore(ProtocoloJson.escrever(visao)));
            ArrayNode forcas = passoJson.putArray("forcas");
            visao.vazas().forEach(vaza -> escreverForcas(forcas.addArray(), vaza.jogadas(), visao.vira(), ordem));
            escreverForcas(forcas.addArray(), visao.vazaAtual(), visao.vira(), ordem);
        }
    }

    private static void escreverForcas(
            ArrayNode json, List<JogadaVisivel> jogadas, Optional<Carta> vira, OrdemDeForca ordem) {
        for (JogadaVisivel jogada : jogadas) {
            if (jogada.encoberta() || jogada.carta().isEmpty()) {
                json.addNull();
            } else {
                json.add(ordem.forca(jogada.carta().get(), vira));
            }
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
