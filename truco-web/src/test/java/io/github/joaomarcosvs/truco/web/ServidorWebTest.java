package io.github.joaomarcosvs.truco.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** O servidor de ponta a ponta, por HTTP, numa porta livre. */
class ServidorWebTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient CLIENTE = HttpClient.newHttpClient();
    private static ServidorWeb servidor;

    @BeforeAll
    static void subir() throws IOException {
        servidor = ServidorWeb.iniciar(0);
    }

    @AfterAll
    static void parar() {
        servidor.parar();
    }

    private static HttpResponse<String> get(String caminho) throws IOException, InterruptedException {
        return CLIENTE.send(
                HttpRequest.newBuilder(endereco(caminho)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> post(String corpo) throws IOException, InterruptedException {
        return CLIENTE.send(
                HttpRequest.newBuilder(endereco("/api/partida"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static URI endereco(String caminho) {
        return URI.create("http://localhost:" + servidor.porta() + caminho);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "/, text/html",
        "/mesa.js, text/javascript",
        "/cartas.js, text/javascript",
        "/baralho-espanhol.png, image/png",
        "/estilo.css, text/css",
        "/saude, text/plain"
    })
    @DisplayName("A página, o script, o estilo e a saúde do servidor")
    void pagina(String caminho, String tipo) throws Exception {
        HttpResponse<String> resposta = get(caminho);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(valor -> assertThat(valor).startsWith(tipo));
    }

    @Test
    @DisplayName("A imagem do baralho vai inteira e pode ficar em cache; a página, não")
    void cacheDaImagem() throws Exception {
        HttpResponse<byte[]> imagem = CLIENTE.send(
                HttpRequest.newBuilder(endereco("/baralho-espanhol.png")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        byte[] original;
        try (InputStream recurso = ServidorWeb.class.getResourceAsStream("/web/baralho-espanhol.png")) {
            original = recurso.readAllBytes();
        }

        assertThat(imagem.body()).isEqualTo(original);
        assertThat(imagem.headers().firstValue("Cache-Control")).hasValue("public, max-age=604800, immutable");
        assertThat(get("/").headers().firstValue("Cache-Control")).hasValue("no-store");
    }

    @Test
    @DisplayName("Caminho desconhecido dá 404, e a API só aceita POST")
    void caminhosEMetodos() throws Exception {
        assertThat(get("/../pom.xml").statusCode()).isEqualTo(404);
        assertThat(get("/web/index.html").statusCode()).isEqualTo(404);
        assertThat(get("/api/partida").statusCode()).isEqualTo(405);
    }

    @Test
    @DisplayName("Seed 2026 sem jogadas: a sua visão, os eventos com texto e as ações legais com texto")
    void comeco() throws Exception {
        HttpResponse<String> resposta = post("{\"seed\":2026,\"acoes\":[]}");

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode json = JSON.readTree(resposta.body());
        assertThat(json.get("seed").asLong()).isEqualTo(2026);
        JsonNode primeiro = json.get("passos").get(0);
        assertThat(primeiro.get("eventos").get(0).get("texto").asString())
                .isEqualTo("Rodada 1: você dá as cartas, vira cavalo de ouros.");
        assertThat(primeiro.get("visao").get("jogador").asString()).isEqualTo("voce");
        JsonNode acoes = json.get("acoesLegais");
        assertThat(acoes.get(0).get("acao").toString()).isEqualTo("{\"tipo\":\"JogarCarta\",\"indiceNaMao\":0}");
        assertThat(acoes.get(0).get("texto").asString()).isEqualTo("Jogar 2 de bastos");
        assertThat(acoes.get(acoes.size() - 1).get("texto").asString()).isEqualTo("Pedir aumento para 3");
    }

    @Test
    @DisplayName("Sem seed, o servidor sorteia uma e a devolve, para a página guardar")
    void seedSorteada() throws Exception {
        JsonNode json = JSON.readTree(post("{}").body());

        assertThat(json.get("seed").isIntegralNumber()).isTrue();
    }

    @Test
    @DisplayName("RG-FIM-1: por HTTP, jogando sempre a primeira ação legal, a partida vai até o fim")
    void partidaInteira() throws Exception {
        StringBuilder jogadas = new StringBuilder();
        JsonNode json = JSON.readTree(post("{\"seed\":7,\"acoes\":[]}").body());
        int pedidos = 0;
        while (!json.get("acoesLegais").isEmpty()) {
            assertThat(pedidos++).isLessThan(ServidorWeb.MAXIMO_DE_JOGADAS);
            if (!jogadas.isEmpty()) {
                jogadas.append(',');
            }
            jogadas.append(json.get("acoesLegais").get(0).get("acao"));
            HttpResponse<String> resposta = post("{\"seed\":7,\"acoes\":[" + jogadas + "]}");
            assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(200);
            json = JSON.readTree(resposta.body());
        }

        JsonNode ultima = json.get("passos").get(json.get("passos").size() - 1).get("visao");
        assertThat(ultima.get("vencedoraDaPartida").isNull()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            não é JSON              | jogar
            lista                   | []
            campo desconhecido      | {"seed":1,"x":2}
            seed como texto         | {"seed":"1"}
            ações que não são lista | {"seed":1,"acoes":{}}
            ação malformada         | {"seed":1,"acoes":[{"tipo":"Voar"}]}
            jogada ilegal           | {"seed":2026,"acoes":[{"tipo":"Correr"}]}
            """)
    @DisplayName("Pedidos inválidos dão 400 com o motivo")
    void pedidosInvalidos(String caso, String corpo) throws Exception {
        HttpResponse<String> resposta = post(corpo);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(resposta.body()).get("erro").asString()).isNotBlank();
    }
}
