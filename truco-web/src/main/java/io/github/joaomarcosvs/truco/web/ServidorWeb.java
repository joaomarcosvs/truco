package io.github.joaomarcosvs.truco.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.protocol.Leitura;
import io.github.joaomarcosvs.truco.protocol.ProtocoloJson;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.JogadaInvalida;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Reconstituida;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servidor HTTP da página de teste, só com o JDK. Não guarda nada entre pedidos: cada pedido traz a seed e as suas
 * jogadas, e a partida é refeita ({@link PartidaReconstituida}).
 *
 * <ul>
 *   <li>{@code GET /}, {@code /mesa.js}, {@code /estilo.css}: a página.
 *   <li>{@code POST /api/partida} com {@code {"seed": 2026, "acoes": [...]}}: a partida até agora ({@link RespostaJson}).
 *       Sem {@code seed}, começa uma partida com uma seed sorteada.
 *   <li>{@code GET /saude}: responde "ok", para o serviço de hospedagem saber que o servidor está de pé.
 * </ul>
 */
final class ServidorWeb {

    /** Bem acima das jogadas de uma partida inteira. */
    static final int MAXIMO_DE_JOGADAS = 2_000;

    private static final int MAXIMO_DO_CORPO = 256 * 1024;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Arquivo(String recurso, String tipo) {}

    private static final Map<String, Arquivo> PAGINA = Map.of(
            "/", new Arquivo("/web/index.html", "text/html; charset=utf-8"),
            "/mesa.js", new Arquivo("/web/mesa.js", "text/javascript; charset=utf-8"),
            "/estilo.css", new Arquivo("/web/estilo.css", "text/css; charset=utf-8"));

    private final HttpServer servidor;

    private ServidorWeb(HttpServer servidor) {
        this.servidor = servidor;
    }

    /** Sobe o servidor na porta dada, em todos os endereços; com a porta 0, o sistema escolhe uma livre. */
    static ServidorWeb iniciar(int porta) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.createContext("/api/partida", ServidorWeb::partida);
        servidor.createContext("/saude", troca -> responder(troca, 200, "text/plain; charset=utf-8", "ok"));
        servidor.createContext("/", ServidorWeb::pagina);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.start();
        return new ServidorWeb(servidor);
    }

    int porta() {
        return servidor.getAddress().getPort();
    }

    void parar() {
        servidor.stop(0);
    }

    private static void pagina(HttpExchange troca) throws IOException {
        if (!troca.getRequestMethod().equals("GET")) {
            responder(troca, 405, "text/plain; charset=utf-8", "Método não permitido");
            return;
        }
        Arquivo arquivo = PAGINA.get(troca.getRequestURI().getPath());
        if (arquivo == null) {
            responder(troca, 404, "text/plain; charset=utf-8", "Não encontrado");
            return;
        }
        try (InputStream conteudo = ServidorWeb.class.getResourceAsStream(arquivo.recurso())) {
            if (conteudo == null) {
                throw new IllegalStateException("Arquivo da página ausente: " + arquivo.recurso());
            }
            responder(troca, 200, arquivo.tipo(), conteudo.readAllBytes());
        }
    }

    private static void partida(HttpExchange troca) throws IOException {
        if (!troca.getRequestMethod().equals("POST")) {
            responder(troca, 405, "application/json; charset=utf-8", RespostaJson.erro("Use POST"));
            return;
        }
        byte[] corpo = troca.getRequestBody().readNBytes(MAXIMO_DO_CORPO + 1);
        if (corpo.length > MAXIMO_DO_CORPO) {
            responder(troca, 413, "application/json; charset=utf-8", RespostaJson.erro("Pedido grande demais"));
            return;
        }
        Pedido pedido;
        try {
            pedido = Pedido.ler(new String(corpo, StandardCharsets.UTF_8));
        } catch (PedidoInvalido e) {
            responder(troca, 400, "application/json; charset=utf-8", RespostaJson.erro(e.getMessage()));
            return;
        }
        switch (PartidaReconstituida.reconstituir(pedido.seed(), pedido.acoes())) {
            case Reconstituida partida ->
                responder(troca, 200, "application/json; charset=utf-8", RespostaJson.partida(pedido.seed(), partida));
            case JogadaInvalida(int indice, Acao acao) ->
                responder(
                        troca,
                        400,
                        "application/json; charset=utf-8",
                        RespostaJson.erro("A jogada %d (%s) não é legal nesta partida"
                                .formatted(indice + 1, ProtocoloJson.escrever(acao))));
        }
    }

    /** O corpo de {@code POST /api/partida}: a seed (sorteada se ausente) e as suas jogadas, em ordem. */
    private record Pedido(long seed, List<Acao> acoes) {

        private static final Set<String> CAMPOS = Set.of("seed", "acoes");

        static Pedido ler(String corpo) throws PedidoInvalido {
            JsonNode raiz;
            try {
                raiz = JSON.readTree(corpo);
            } catch (JacksonException e) {
                throw new PedidoInvalido("JSON inválido: " + e.getOriginalMessage());
            }
            if (raiz == null || !raiz.isObject()) {
                throw new PedidoInvalido("O pedido precisa ser um objeto JSON");
            }
            for (String campo : raiz.propertyNames()) {
                if (!CAMPOS.contains(campo)) {
                    throw new PedidoInvalido("Campo desconhecido: " + campo);
                }
            }
            long seed;
            JsonNode seedJson = raiz.get("seed");
            if (seedJson == null || seedJson.isNull()) {
                seed = ThreadLocalRandom.current().nextLong(1_000_000);
            } else if (seedJson.isIntegralNumber() && seedJson.canConvertToLong()) {
                seed = seedJson.longValue();
            } else {
                throw new PedidoInvalido("A seed precisa ser um número inteiro");
            }
            JsonNode acoesJson = raiz.get("acoes");
            List<Acao> acoes = new ArrayList<>();
            if (acoesJson != null && !acoesJson.isNull()) {
                if (!acoesJson.isArray()) {
                    throw new PedidoInvalido("\"acoes\" precisa ser uma lista");
                }
                if (acoesJson.size() > MAXIMO_DE_JOGADAS) {
                    throw new PedidoInvalido("Jogadas demais");
                }
                for (JsonNode acaoJson : acoesJson) {
                    switch (ProtocoloJson.lerAcao(acaoJson.toString())) {
                        case Leitura.Lida<Acao>(Acao acao) -> acoes.add(acao);
                        case Leitura.Invalida<Acao>(String motivo) ->
                            throw new PedidoInvalido("Jogada %d inválida: %s".formatted(acoes.size() + 1, motivo));
                    }
                }
            }
            return new Pedido(seed, List.copyOf(acoes));
        }
    }

    /** O pedido não segue o formato esperado; vira uma resposta 400. */
    private static final class PedidoInvalido extends Exception {

        private static final long serialVersionUID = 1L;

        PedidoInvalido(String mensagem) {
            super(mensagem);
        }
    }

    private static void responder(HttpExchange troca, int status, String tipo, String corpo) throws IOException {
        responder(troca, status, tipo, corpo.getBytes(StandardCharsets.UTF_8));
    }

    private static void responder(HttpExchange troca, int status, String tipo, byte[] corpo) throws IOException {
        troca.getResponseHeaders().set("Content-Type", tipo);
        troca.getResponseHeaders().set("Cache-Control", "no-store");
        troca.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        troca.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'");
        troca.sendResponseHeaders(status, corpo.length == 0 ? -1 : corpo.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(corpo);
        }
    }
}
