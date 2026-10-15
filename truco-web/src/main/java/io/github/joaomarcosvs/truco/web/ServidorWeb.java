package io.github.joaomarcosvs.truco.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.partida.JogadorId;
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
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servidor HTTP da página de teste, só com o JDK.
 *
 * <ul>
 *   <li>{@code GET /}, {@code /mesa.js}, {@code /cartas.js}, {@code /estilo.css}, {@code /baralho-espanhol.png}: a
 *       página.
 *   <li>{@code POST /api/partida} com {@code {"seed": 2026, "acoes": [...]}}: a partida contra o bot até agora
 *       ({@link RespostaJson#partida}). Não guarda nada entre pedidos: cada pedido traz a seed e as suas jogadas, e a
 *       partida é refeita ({@link PartidaReconstituida}). Sem {@code seed}, começa uma partida com uma seed sorteada.
 *   <li>{@code POST /api/salas}: cria uma sala para jogar contra um amigo ({@link Sala}), guardada na memória.
 *   <li>{@code POST /api/salas/{codigo}/entrar}: o amigo entra na sala.
 *   <li>{@code GET /api/salas/{codigo}?desde=N}: o que mudou na sala depois do cursor N ({@link RespostaJson#sala}).
 *   <li>{@code POST /api/salas/{codigo}/jogadas} com {@code {"acao": {...}, "cursor": N}}: uma jogada na sala.
 *   <li>{@code GET /saude}: responde "ok", para o serviço de hospedagem saber que o servidor está de pé.
 * </ul>
 *
 * Nas rotas de uma sala, o jogador se identifica pelo token, no cabeçalho {@code Authorization: Bearer <token>}.
 */
final class ServidorWeb {

    /** Bem acima das jogadas de uma partida inteira. */
    static final int MAXIMO_DE_JOGADAS = 2_000;

    private static final int MAXIMO_DO_CORPO = 256 * 1024;

    private static final String JSON_UTF8 = "application/json; charset=utf-8";
    private static final String TEXTO_UTF8 = "text/plain; charset=utf-8";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Um arquivo da página; o cache vale para o que não muda entre versões, como a imagem do baralho. */
    private record Arquivo(String recurso, String tipo, String cache) {}

    private static final String SEM_CACHE = "no-store";

    private static final Map<String, Arquivo> PAGINA = Map.of(
            "/", new Arquivo("/web/index.html", "text/html; charset=utf-8", SEM_CACHE),
            "/mesa.js", new Arquivo("/web/mesa.js", "text/javascript; charset=utf-8", SEM_CACHE),
            "/cartas.js", new Arquivo("/web/cartas.js", "text/javascript; charset=utf-8", SEM_CACHE),
            "/estilo.css", new Arquivo("/web/estilo.css", "text/css; charset=utf-8", SEM_CACHE),
            "/baralho-espanhol.png",
                    new Arquivo("/web/baralho-espanhol.png", "image/png", "public, max-age=604800, immutable"));

    private static final String SALAS = "/api/salas";
    private static final Pattern ROTA_DA_SALA = Pattern.compile("/api/salas/([^/]+)(/entrar|/jogadas)?");
    private static final Pattern DESDE = Pattern.compile("desde=(\\d{1,9})");
    private static final String SALA_NAO_EXISTE =
            "Não achei essa sala. Confira o código: a sala também some quando expira ou quando o servidor reinicia.";

    private final HttpServer servidor;

    private ServidorWeb(HttpServer servidor) {
        this.servidor = servidor;
    }

    /** Sobe o servidor na porta dada, em todos os endereços; com a porta 0, o sistema escolhe uma livre. */
    static ServidorWeb iniciar(int porta) throws IOException {
        return iniciar(porta, Salas.novas());
    }

    static ServidorWeb iniciar(int porta, Salas salas) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.createContext("/api/partida", ServidorWeb::partida);
        servidor.createContext(SALAS, troca -> salas(troca, salas));
        servidor.createContext("/saude", troca -> responder(troca, 200, TEXTO_UTF8, "ok"));
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
            responder(troca, 405, TEXTO_UTF8, "Método não permitido");
            return;
        }
        Arquivo arquivo = PAGINA.get(troca.getRequestURI().getPath());
        if (arquivo == null) {
            responder(troca, 404, TEXTO_UTF8, "Não encontrado");
            return;
        }
        try (InputStream conteudo = ServidorWeb.class.getResourceAsStream(arquivo.recurso())) {
            if (conteudo == null) {
                throw new IllegalStateException("Arquivo da página ausente: " + arquivo.recurso());
            }
            responder(troca, 200, arquivo.tipo(), arquivo.cache(), conteudo.readAllBytes());
        }
    }

    // ---------- Contra o bot ----------

    private static void partida(HttpExchange troca) throws IOException {
        if (!metodo(troca, "POST")) {
            return;
        }
        Optional<String> corpo = corpo(troca);
        if (corpo.isEmpty()) {
            return;
        }
        Pedido pedido;
        try {
            pedido = Pedido.ler(corpo.get());
        } catch (PedidoInvalido e) {
            responder(troca, 400, JSON_UTF8, RespostaJson.erro(e.getMessage()));
            return;
        }
        switch (PartidaReconstituida.reconstituir(pedido.seed(), pedido.acoes())) {
            case Reconstituida partida ->
                responder(troca, 200, JSON_UTF8, RespostaJson.partida(pedido.seed(), partida));
            case JogadaInvalida(int indice, Acao acao) ->
                responder(
                        troca,
                        400,
                        JSON_UTF8,
                        RespostaJson.erro("A jogada %d (%s) não é legal nesta partida"
                                .formatted(indice + 1, ProtocoloJson.escrever(acao))));
        }
    }

    /** O corpo de {@code POST /api/partida}: a seed (sorteada se ausente) e as suas jogadas, em ordem. */
    private record Pedido(long seed, List<Acao> acoes) {

        static Pedido ler(String corpo) throws PedidoInvalido {
            JsonNode raiz = objeto(corpo, Set.of("seed", "acoes"));
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
                    acoes.add(lerAcao(acaoJson, "Jogada %d inválida: ".formatted(acoes.size() + 1)));
                }
            }
            return new Pedido(seed, List.copyOf(acoes));
        }
    }

    // ---------- Salas, contra um amigo ----------

    private static void salas(HttpExchange troca, Salas salas) throws IOException {
        String caminho = troca.getRequestURI().getPath();
        if (caminho.equals(SALAS)) {
            if (metodo(troca, "POST") && corpo(troca).isPresent()) {
                criar(troca, salas);
            }
            return;
        }
        Matcher rota = ROTA_DA_SALA.matcher(caminho);
        if (!rota.matches()) {
            responder(troca, 404, JSON_UTF8, RespostaJson.erro("Não encontrado"));
            return;
        }
        String codigo = rota.group(1);
        switch (Objects.requireNonNullElse(rota.group(2), "")) {
            case "/entrar" -> {
                if (metodo(troca, "POST") && corpo(troca).isPresent()) {
                    entrar(troca, salas, codigo);
                }
            }
            case "/jogadas" -> {
                if (metodo(troca, "POST")) {
                    jogar(troca, salas, codigo);
                }
            }
            default -> {
                if (metodo(troca, "GET")) {
                    vista(troca, salas, codigo);
                }
            }
        }
    }

    private static void criar(HttpExchange troca, Salas salas) throws IOException {
        switch (salas.criar()) {
            case Salas.Criada(Sala sala, String token) ->
                responder(troca, 200, JSON_UTF8, RespostaJson.entrada(sala.codigo(), token, Sala.ANFITRIAO));
            case Salas.SemVaga semVaga ->
                responder(
                        troca,
                        503,
                        JSON_UTF8,
                        RespostaJson.erro("Há salas demais abertas agora. Tente de novo daqui a pouco."));
        }
    }

    private static void entrar(HttpExchange troca, Salas salas, String codigo) throws IOException {
        switch (salas.entrar(codigo)) {
            case Salas.Entrou(Sala sala, String token) ->
                responder(troca, 200, JSON_UTF8, RespostaJson.entrada(sala.codigo(), token, Sala.CONVIDADO));
            case Salas.NaoExiste naoExiste -> responder(troca, 404, JSON_UTF8, RespostaJson.erro(SALA_NAO_EXISTE));
            case Salas.Cheia cheia ->
                responder(troca, 409, JSON_UTF8, RespostaJson.erro("Essa sala já tem os dois jogadores."));
        }
    }

    private static void vista(HttpExchange troca, Salas salas, String codigo) throws IOException {
        Optional<Participante> participante = participante(troca, salas, codigo);
        if (participante.isEmpty()) {
            return;
        }
        String consulta = Objects.requireNonNullElse(troca.getRequestURI().getRawQuery(), "");
        Matcher desde = DESDE.matcher(consulta);
        OptionalInt cursor = consulta.isEmpty()
                ? OptionalInt.of(0)
                : desde.matches() ? OptionalInt.of(Integer.parseInt(desde.group(1))) : OptionalInt.empty();
        if (cursor.isEmpty()) {
            responder(troca, 400, JSON_UTF8, RespostaJson.erro("Use ?desde=N, com N inteiro"));
            return;
        }
        Sala sala = participante.get().sala();
        Sala.Vista vista = sala.vistaDe(participante.get().jogador(), cursor.getAsInt());
        responder(troca, 200, JSON_UTF8, RespostaJson.sala(sala.configuracao(), vista));
    }

    private static void jogar(HttpExchange troca, Salas salas, String codigo) throws IOException {
        Optional<String> corpo = corpo(troca);
        if (corpo.isEmpty()) {
            return;
        }
        Optional<Participante> participante = participante(troca, salas, codigo);
        if (participante.isEmpty()) {
            return;
        }
        PedidoDeJogada pedido;
        try {
            pedido = PedidoDeJogada.ler(corpo.get());
        } catch (PedidoInvalido e) {
            responder(troca, 400, JSON_UTF8, RespostaJson.erro(e.getMessage()));
            return;
        }
        Sala sala = participante.get().sala();
        switch (sala.jogar(participante.get().jogador(), pedido.acao(), pedido.cursor(), salas.agora())) {
            case Sala.Aceita(Sala.Vista vista) ->
                responder(troca, 200, JSON_UTF8, RespostaJson.sala(sala.configuracao(), vista));
            case Sala.Desatualizada desatualizada ->
                responder(
                        troca,
                        409,
                        JSON_UTF8,
                        RespostaJson.erro("A partida mudou antes da sua jogada. Veja a mesa e jogue de novo."));
            case Sala.Ilegal ilegal ->
                responder(troca, 400, JSON_UTF8, RespostaJson.erro("Essa jogada não vale agora."));
        }
    }

    /** Quem fez o pedido numa sala. */
    private record Participante(Sala sala, JogadorId jogador) {}

    /** A sala do código e o jogador dono do token; sem eles, responde o erro e devolve vazio. */
    private static Optional<Participante> participante(HttpExchange troca, Salas salas, String codigo)
            throws IOException {
        Optional<Sala> sala = salas.buscar(codigo);
        if (sala.isEmpty()) {
            responder(troca, 404, JSON_UTF8, RespostaJson.erro(SALA_NAO_EXISTE));
            return Optional.empty();
        }
        Optional<JogadorId> jogador = Optional.ofNullable(
                        troca.getRequestHeaders().getFirst("Authorization"))
                .filter(cabecalho -> cabecalho.startsWith("Bearer "))
                .map(cabecalho -> cabecalho.substring("Bearer ".length()).trim())
                .flatMap(sala.get()::jogadorDoToken);
        if (jogador.isEmpty()) {
            responder(troca, 401, JSON_UTF8, RespostaJson.erro("Você não está nesta sala."));
            return Optional.empty();
        }
        return Optional.of(new Participante(sala.get(), jogador.get()));
    }

    /** O corpo de {@code POST /api/salas/{codigo}/jogadas}: a ação e o cursor que o jogador tinha visto. */
    private record PedidoDeJogada(Acao acao, int cursor) {

        static PedidoDeJogada ler(String corpo) throws PedidoInvalido {
            JsonNode raiz = objeto(corpo, Set.of("acao", "cursor"));
            JsonNode acao = raiz.get("acao");
            JsonNode cursor = raiz.get("cursor");
            if (acao == null || cursor == null) {
                throw new PedidoInvalido("A jogada precisa de \"acao\" e \"cursor\"");
            }
            if (!cursor.isIntegralNumber() || !cursor.canConvertToInt() || cursor.intValue() < 0) {
                throw new PedidoInvalido("O cursor precisa ser um número inteiro, a partir de 0");
            }
            return new PedidoDeJogada(lerAcao(acao, "Jogada inválida: "), cursor.intValue());
        }
    }

    // ---------- Leitura dos pedidos ----------

    /** O pedido não segue o formato esperado; vira uma resposta 400. */
    private static final class PedidoInvalido extends Exception {

        private static final long serialVersionUID = 1L;

        PedidoInvalido(String mensagem) {
            super(mensagem);
        }
    }

    /** Um objeto JSON só com os campos permitidos. */
    private static JsonNode objeto(String corpo, Set<String> campos) throws PedidoInvalido {
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
            if (!campos.contains(campo)) {
                throw new PedidoInvalido("Campo desconhecido: " + campo);
            }
        }
        return raiz;
    }

    private static Acao lerAcao(JsonNode json, String prefixoDoErro) throws PedidoInvalido {
        return switch (ProtocoloJson.lerAcao(json.toString())) {
            case Leitura.Lida<Acao>(Acao acao) -> acao;
            case Leitura.Invalida<Acao>(String motivo) -> throw new PedidoInvalido(prefixoDoErro + motivo);
        };
    }

    /** Responde 405 e devolve falso se o método não é o esperado. */
    private static boolean metodo(HttpExchange troca, String esperado) throws IOException {
        if (troca.getRequestMethod().equals(esperado)) {
            return true;
        }
        responder(troca, 405, JSON_UTF8, RespostaJson.erro("Use " + esperado));
        return false;
    }

    /** O corpo do pedido, em UTF-8; se for grande demais, responde 413 e devolve vazio. */
    private static Optional<String> corpo(HttpExchange troca) throws IOException {
        byte[] corpo = troca.getRequestBody().readNBytes(MAXIMO_DO_CORPO + 1);
        if (corpo.length > MAXIMO_DO_CORPO) {
            responder(troca, 413, JSON_UTF8, RespostaJson.erro("Pedido grande demais"));
            return Optional.empty();
        }
        return Optional.of(new String(corpo, StandardCharsets.UTF_8));
    }

    private static void responder(HttpExchange troca, int status, String tipo, String corpo) throws IOException {
        responder(troca, status, tipo, SEM_CACHE, corpo.getBytes(StandardCharsets.UTF_8));
    }

    private static void responder(HttpExchange troca, int status, String tipo, String cache, byte[] corpo)
            throws IOException {
        troca.getResponseHeaders().set("Content-Type", tipo);
        troca.getResponseHeaders().set("Cache-Control", cache);
        troca.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        troca.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'");
        troca.sendResponseHeaders(status, corpo.length == 0 ? -1 : corpo.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(corpo);
        }
    }
}
