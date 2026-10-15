package io.github.joaomarcosvs.truco.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** As salas de ponta a ponta, por HTTP: dois navegadores, cada um com o seu token. */
class SalasPorHttpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient CLIENTE = HttpClient.newHttpClient();
    private static final String SEM_TOKEN = "";
    private static ServidorWeb servidor;

    /** Um jogador na sala, como a página guarda. */
    private record Jogador(String codigo, String token, String id) {}

    @BeforeAll
    static void subir() throws IOException {
        servidor = ServidorWeb.iniciar(0);
    }

    @AfterAll
    static void parar() {
        servidor.parar();
    }

    private static HttpResponse<String> pedir(String metodo, String caminho, String token, String corpo)
            throws IOException, InterruptedException {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + servidor.porta() + caminho))
                .method(metodo, HttpRequest.BodyPublishers.ofString(corpo, UTF_8));
        if (!token.isEmpty()) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return CLIENTE.send(pedido.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
    }

    private static Jogador jogador(HttpResponse<String> resposta) {
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(200);
        JsonNode json = JSON.readTree(resposta.body());
        return new Jogador(
                json.get("codigo").asString(),
                json.get("token").asString(),
                json.get("voce").asString());
    }

    private static Jogador criar() throws Exception {
        return jogador(pedir("POST", "/api/salas", SEM_TOKEN, ""));
    }

    private static Jogador entrar(String codigo) throws Exception {
        return jogador(pedir("POST", "/api/salas/" + codigo + "/entrar", SEM_TOKEN, ""));
    }

    private static JsonNode ver(Jogador jogador, int desde) throws Exception {
        HttpResponse<String> resposta =
                pedir("GET", "/api/salas/" + jogador.codigo() + "?desde=" + desde, jogador.token(), "");
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(200);
        JsonNode json = JSON.readTree(resposta.body());
        // A seed não sai do servidor: com ela, daria para calcular as cartas dos dois.
        assertThat(json.has("seed")).isFalse();
        return json;
    }

    private static HttpResponse<String> jogar(Jogador jogador, JsonNode acao, int cursor) throws Exception {
        return pedir(
                "POST",
                "/api/salas/" + jogador.codigo() + "/jogadas",
                jogador.token(),
                "{\"acao\":" + acao + ",\"cursor\":" + cursor + "}");
    }

    @Test
    @DisplayName("Quem cria a sala espera; quando o amigo entra, a partida começa para os dois, e uma terceira pessoa"
            + " encontra a sala cheia")
    void criarEEntrar() throws Exception {
        Jogador anfitriao = criar();
        assertThat(anfitriao.id()).isEqualTo("anfitriao");
        assertThat(anfitriao.codigo()).matches("[" + Salas.LETRAS_DO_CODIGO + "]{6}");
        JsonNode esperando = ver(anfitriao, 0);
        assertThat(esperando.get("situacao").asString()).isEqualTo("esperando-adversario");
        assertThat(esperando.get("passos")).isEmpty();
        assertThat(esperando.get("acoesLegais")).isEmpty();

        Jogador convidado = entrar(anfitriao.codigo().toLowerCase(Locale.ROOT));

        assertThat(convidado.id()).isEqualTo("convidado");
        for (Jogador jogador : List.of(anfitriao, convidado)) {
            JsonNode vista = ver(jogador, 0);
            assertThat(vista.get("situacao").asString()).isEqualTo("jogando");
            assertThat(vista.get("voce").asString()).isEqualTo(jogador.id());
            assertThat(vista.get("cursor").asInt()).isEqualTo(1);
            assertThat(vista.get("passos")
                            .get(0)
                            .get("eventos")
                            .get(0)
                            .get("texto")
                            .asString())
                    .startsWith("Rodada 1: ");
        }
        assertThat(pedir("POST", "/api/salas/" + anfitriao.codigo() + "/entrar", SEM_TOKEN, "")
                        .statusCode())
                .isEqualTo(409);
    }

    @Test
    @DisplayName("Sala que não existe dá 404; sem o token da sala, 401; cursor malformado, 400")
    void acesso() throws Exception {
        Jogador anfitriao = criar();
        Jogador deOutraSala = criar();

        assertThat(pedir("GET", "/api/salas/ZZZZZZ", anfitriao.token(), "").statusCode())
                .isEqualTo(404);
        assertThat(pedir("POST", "/api/salas/nada/entrar", SEM_TOKEN, "").statusCode())
                .isEqualTo(404);
        assertThat(pedir("GET", "/api/salas/" + anfitriao.codigo(), SEM_TOKEN, "")
                        .statusCode())
                .isEqualTo(401);
        assertThat(pedir("GET", "/api/salas/" + anfitriao.codigo(), deOutraSala.token(), "")
                        .statusCode())
                .isEqualTo(401);
        assertThat(pedir("GET", "/api/salas/" + anfitriao.codigo() + "?desde=x", anfitriao.token(), "")
                        .statusCode())
                .isEqualTo(400);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET, /api/salas, 405",
        "DELETE, /api/salas/ABCDEF, 405",
        "GET, /api/salas/ABCDEF/jogadas, 405",
        "POST, /api/salas/ABCDEF/outra, 404",
        "POST, /api/salasX, 404"
    })
    @DisplayName("Rotas e métodos das salas")
    void rotas(String metodo, String caminho, int status) throws Exception {
        assertThat(pedir(metodo, caminho, SEM_TOKEN, "").statusCode()).isEqualTo(status);
    }

    @Test
    @DisplayName("Jogada malformada ou fora da vez dá 400; com um cursor velho, 409")
    void jogadasRecusadas() throws Exception {
        Jogador anfitriao = criar();
        Jogador convidado = entrar(anfitriao.codigo());
        JsonNode vistaDoAnfitriao = ver(anfitriao, 0);
        JsonNode vistaDoConvidado = ver(convidado, 0);
        Jogador abre = vistaDoAnfitriao.get("acoesLegais").isEmpty() ? convidado : anfitriao;
        Jogador espera = abre == anfitriao ? convidado : anfitriao;
        JsonNode jogarAPrimeira = JSON.readTree("{\"tipo\":\"JogarCarta\",\"indiceNaMao\":0}");

        String caminho = "/api/salas/" + abre.codigo() + "/jogadas";
        assertThat(pedir("POST", caminho, abre.token(), "{\"cursor\":1}").statusCode())
                .isEqualTo(400);
        assertThat(pedir("POST", caminho, abre.token(), "{\"acao\":{\"tipo\":\"Voar\"},\"cursor\":1}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(pedir("POST", caminho, abre.token(), "{\"acao\":" + jogarAPrimeira + ",\"cursor\":\"1\"}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(pedir("POST", caminho, SEM_TOKEN, "{\"acao\":" + jogarAPrimeira + ",\"cursor\":1}")
                        .statusCode())
                .isEqualTo(401);
        // Quem não abre só pode descartar, se tiver a carta da vez; jogar uma carta não vale.
        assertThat(jogar(espera, jogarAPrimeira, 1).statusCode()).isEqualTo(400);
        assertThat(vistaDoConvidado.get("cursor").asInt()).isEqualTo(1);

        assertThat(jogar(abre, jogarAPrimeira, 0).statusCode()).isEqualTo(409);
        assertThat(jogar(abre, jogarAPrimeira, 1).statusCode()).isEqualTo(200);
        assertThat(jogar(abre, jogarAPrimeira, 1).statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName(
            "RG-FIM-1: por HTTP, cada um jogando a primeira ação quando pode, a partida vai até o fim para os dois")
    void partidaInteira() throws Exception {
        Jogador anfitriao = criar();
        Jogador convidado = entrar(anfitriao.codigo());
        Map<Jogador, Integer> cursores = new HashMap<>(Map.of(anfitriao, 0, convidado, 0));
        Map<Jogador, JsonNode> vistas = new HashMap<>();
        StringBuilder textos = new StringBuilder();
        int jogadas = 0;
        while (true) {
            for (Jogador jogador : List.of(anfitriao, convidado)) {
                JsonNode vista = ver(jogador, cursores.get(jogador));
                vista.get("passos").forEach(passo -> textos.append(passo.get("eventos")));
                cursores.put(jogador, vista.get("cursor").asInt());
                vistas.put(jogador, vista);
            }
            if (vistas.get(anfitriao).get("situacao").asString().equals("finalizada")) {
                break;
            }
            Jogador quemJoga = vistas.get(anfitriao).get("acoesLegais").isEmpty() ? convidado : anfitriao;
            assertThat(jogadas++).isLessThan(ServidorWeb.MAXIMO_DE_JOGADAS);
            JsonNode acao = vistas.get(quemJoga).get("acoesLegais").get(0).get("acao");
            HttpResponse<String> resposta = jogar(quemJoga, acao, cursores.get(quemJoga));
            assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(200);
        }

        assertThat(vistas.get(convidado).get("situacao").asString()).isEqualTo("finalizada");
        assertThat(vistas.get(convidado).get("acoesLegais")).isEmpty();
        // O outro jogador é chamado de "adversário" nos textos.
        assertThat(textos.toString()).contains("Adversário jogou");
    }
}
