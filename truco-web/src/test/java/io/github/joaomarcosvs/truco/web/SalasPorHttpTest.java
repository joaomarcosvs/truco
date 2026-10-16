package io.github.joaomarcosvs.truco.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * As salas de ponta a ponta, por HTTP: cada navegador com o seu token. O relógio é de teste, para os bots jogarem
 * quando o teste avança o tempo.
 */
class SalasPorHttpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient CLIENTE = HttpClient.newHttpClient();
    private static final String SEM_TOKEN = "";
    private static final RelogioDeTeste RELOGIO = new RelogioDeTeste(Instant.parse("2026-10-15T12:00:00Z"));
    private static ServidorWeb servidor;

    /** Um jogador na sala, como a página guarda. */
    private record Jogador(String codigo, String token, String id) {}

    @BeforeAll
    static void subir() throws IOException {
        servidor = ServidorWeb.iniciar(0, new Salas(RELOGIO, new Random(7)));
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

    private static Jogador criar(String formato, String nome) throws Exception {
        return jogador(pedir(
                "POST", "/api/salas", SEM_TOKEN, "{\"formato\":\"%s\",\"nome\":\"%s\"}".formatted(formato, nome)));
    }

    private static Jogador entrar(String codigo, String nome) throws Exception {
        return jogador(
                pedir("POST", "/api/salas/" + codigo + "/entrar", SEM_TOKEN, "{\"nome\":\"%s\"}".formatted(nome)));
    }

    private static JsonNode ver(Jogador jogador, int desde) throws Exception {
        HttpResponse<String> resposta =
                pedir("GET", "/api/salas/" + jogador.codigo() + "?desde=" + desde, jogador.token(), "");
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(200);
        JsonNode json = JSON.readTree(resposta.body());
        // A seed não sai do servidor: com ela, daria para calcular as cartas de todos.
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
    @DisplayName("No 1x1, quem cria espera; quando o amigo entra, a partida começa para os dois, e uma terceira pessoa"
            + " encontra a sala cheia")
    void criarEEntrar() throws Exception {
        Jogador ana = criar("1x1", "Ana");
        assertThat(ana.id()).isEqualTo("lugar1");
        assertThat(ana.codigo()).matches("[" + Salas.LETRAS_DO_CODIGO + "]{6}");
        JsonNode esperando = ver(ana, 0);
        assertThat(esperando.get("situacao").asString()).isEqualTo("esperando-jogadores");
        assertThat(esperando.get("formato").asString()).isEqualTo("1x1");
        assertThat(esperando.get("lugares").toString())
                .isEqualTo("[{\"id\":\"lugar1\",\"equipe\":\"lugar1\",\"nome\":\"Ana\",\"bot\":false},"
                        + "{\"id\":\"lugar2\",\"equipe\":\"lugar2\",\"nome\":null,\"bot\":false}]");
        assertThat(esperando.get("passos")).isEmpty();

        Jogador beto = entrar(ana.codigo().toLowerCase(Locale.ROOT), "Beto");

        assertThat(beto.id()).isEqualTo("lugar2");
        for (Jogador jogador : List.of(ana, beto)) {
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
        assertThat(pedir("POST", "/api/salas/" + ana.codigo() + "/entrar", SEM_TOKEN, "{\"nome\":\"Carla\"}")
                        .statusCode())
                .isEqualTo(409);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            sem nome             | {"formato":"1x1"}
            nome vazio           | {"formato":"1x1","nome":"  "}
            nome reservado       | {"formato":"1x1","nome":"Você"}
            formato desconhecido | {"formato":"3x3","nome":"Ana"}
            campo desconhecido   | {"formato":"1x1","nome":"Ana","x":1}
            """)
    @DisplayName("Criar sala com nome ou formato inválido dá 400")
    void criacaoInvalida(String caso, String corpo) throws Exception {
        assertThat(pedir("POST", "/api/salas", SEM_TOKEN, corpo).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Sala que não existe dá 404; sem o token da sala, 401; cursor malformado, 400; nome repetido, 409")
    void acesso() throws Exception {
        Jogador ana = criar("2x2", "Ana");
        Jogador deOutraSala = criar("1x1", "Zé");

        assertThat(pedir("GET", "/api/salas/ZZZZZZ", ana.token(), "").statusCode())
                .isEqualTo(404);
        assertThat(pedir("POST", "/api/salas/nada/entrar", SEM_TOKEN, "{\"nome\":\"Beto\"}")
                        .statusCode())
                .isEqualTo(404);
        assertThat(pedir("GET", "/api/salas/" + ana.codigo(), SEM_TOKEN, "").statusCode())
                .isEqualTo(401);
        assertThat(pedir("GET", "/api/salas/" + ana.codigo(), deOutraSala.token(), "")
                        .statusCode())
                .isEqualTo(401);
        assertThat(pedir("GET", "/api/salas/" + ana.codigo() + "?desde=x", ana.token(), "")
                        .statusCode())
                .isEqualTo(400);
        assertThat(pedir("POST", "/api/salas/" + ana.codigo() + "/entrar", SEM_TOKEN, "{\"nome\":\"ana\"}")
                        .statusCode())
                .isEqualTo(409);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET, /api/salas, 405",
        "DELETE, /api/salas/ABCDEF, 405",
        "GET, /api/salas/ABCDEF/jogadas, 405",
        "GET, /api/salas/ABCDEF/comecar, 405",
        "GET, /api/salas/ABCDEF/jogar-de-novo, 405",
        "GET, /api/salas/ABCDEF/sair, 405",
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
        Jogador ana = criar("1x1", "Ana");
        Jogador beto = entrar(ana.codigo(), "Beto");
        Jogador abre = ver(ana, 0).get("acoesLegais").isEmpty() ? beto : ana;
        Jogador espera = abre == ana ? beto : ana;
        JsonNode jogarAPrimeira = JSON.readTree("{\"tipo\":\"JogarCarta\",\"indiceNaMao\":0}");

        String caminho = "/api/salas/" + abre.codigo() + "/jogadas";
        assertThat(pedir("POST", caminho, abre.token(), "{\"cursor\":1}").statusCode())
                .isEqualTo(400);
        assertThat(pedir("POST", caminho, abre.token(), "{\"acao\":{\"tipo\":\"Voar\"},\"cursor\":1}")
                        .statusCode())
                .isEqualTo(400);
        assertThat(pedir("POST", caminho, SEM_TOKEN, "{\"acao\":" + jogarAPrimeira + ",\"cursor\":1}")
                        .statusCode())
                .isEqualTo(401);
        assertThat(jogar(espera, jogarAPrimeira, 1).statusCode()).isEqualTo(400);

        assertThat(jogar(abre, jogarAPrimeira, 0).statusCode()).isEqualTo(409);
        assertThat(jogar(abre, jogarAPrimeira, 1).statusCode()).isEqualTo(200);
        assertThat(jogar(abre, jogarAPrimeira, 1).statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName(
            "RG-FIM-1: no 1x1, por HTTP, cada um jogando a primeira ação quando pode, a partida vai até o fim, e os"
                    + " textos chamam o outro pelo nome")
    void partidaInteira() throws Exception {
        Jogador ana = criar("1x1", "Ana");
        Jogador beto = entrar(ana.codigo(), "Beto");
        String textos = jogarAteOFim(List.of(ana, beto));

        assertThat(textos).contains("Beto jogou").contains("Ana jogou").doesNotContain("Adversário");
    }

    @Test
    @DisplayName("RG-2X2-1 e RG-FIM-1: no 2x2, duas pessoas escolhem os lugares, quem criou começa, os bots completam a"
            + " mesa, e a partida vai até o fim")
    void duplasComBots() throws Exception {
        Jogador ana = criar("2x2", "Ana");
        Jogador beto = entrar(ana.codigo(), "Beto");
        assertThat(beto.id()).isEqualTo("lugar2");
        // Beto passa para o lugar 3, em frente à Ana: os dois ficam na mesma dupla.
        HttpResponse<String> troca =
                pedir("POST", "/api/salas/" + ana.codigo() + "/lugar", beto.token(), "{\"lugar\":\"lugar3\"}");
        assertThat(troca.statusCode()).as(troca.body()).isEqualTo(200);
        beto = new Jogador(beto.codigo(), beto.token(), "lugar3");
        assertThat(pedir("POST", "/api/salas/" + ana.codigo() + "/comecar", beto.token(), "")
                        .statusCode())
                .isEqualTo(409);

        HttpResponse<String> comeco = pedir("POST", "/api/salas/" + ana.codigo() + "/comecar", ana.token(), "");

        assertThat(comeco.statusCode()).as(comeco.body()).isEqualTo(200);
        JsonNode lugares = JSON.readTree(comeco.body()).get("lugares");
        assertThat(lugares.toString())
                .isEqualTo("[{\"id\":\"lugar1\",\"equipe\":\"dupla1\",\"nome\":\"Ana\",\"bot\":false},"
                        + "{\"id\":\"lugar2\",\"equipe\":\"dupla2\",\"nome\":\"Bot 2\",\"bot\":true},"
                        + "{\"id\":\"lugar3\",\"equipe\":\"dupla1\",\"nome\":\"Beto\",\"bot\":false},"
                        + "{\"id\":\"lugar4\",\"equipe\":\"dupla2\",\"nome\":\"Bot 4\",\"bot\":true}]");
        String textos = jogarAteOFim(List.of(ana, beto));
        assertThat(textos).contains("Bot 2 jogou").contains("Beto jogou");
    }

    @Test
    @DisplayName("Depois do fim, por HTTP: a partida nova começa quando os dois pedem para jogar de novo; quem sai"
            + " impede a seguinte, e antes do fim nem uma coisa nem outra vale")
    void jogarDeNovoESair() throws Exception {
        Jogador ana = criar("1x1", "Ana");
        Jogador beto = entrar(ana.codigo(), "Beto");
        String jogarDeNovo = "/api/salas/" + ana.codigo() + "/jogar-de-novo";
        String sair = "/api/salas/" + ana.codigo() + "/sair";
        assertThat(pedir("POST", jogarDeNovo, ana.token(), "").statusCode()).isEqualTo(409);
        assertThat(pedir("POST", sair, ana.token(), "").statusCode()).isEqualTo(409);
        jogarAteOFim(List.of(ana, beto));

        JsonNode pedido =
                JSON.readTree(pedir("POST", jogarDeNovo, ana.token(), "").body());
        assertThat(pedido.get("situacao").asString()).isEqualTo("finalizada");
        assertThat(pedido.get("queremJogarDeNovo").toString()).isEqualTo("[\"lugar1\"]");
        JsonNode novaPartida =
                JSON.readTree(pedir("POST", jogarDeNovo, beto.token(), "").body());
        assertThat(novaPartida.get("situacao").asString()).isEqualTo("jogando");
        assertThat(novaPartida.get("partida").asInt()).isEqualTo(2);
        assertThat(novaPartida.get("queremJogarDeNovo").isEmpty()).isTrue();
        jogarAteOFim(List.of(ana, beto));

        JsonNode saida = JSON.readTree(pedir("POST", sair, beto.token(), "").body());
        assertThat(saida.get("sairam").toString()).isEqualTo("[\"lugar2\"]");
        HttpResponse<String> recusa = pedir("POST", jogarDeNovo, ana.token(), "");
        assertThat(recusa.statusCode()).isEqualTo(409);
        assertThat(recusa.body()).contains("Beto saiu da sala.");
    }

    /** Cada um faz a primeira ação quando pode, e o relógio anda para os bots jogarem; devolve os textos vistos. */
    private static String jogarAteOFim(List<Jogador> jogadores) throws Exception {
        Map<Jogador, Integer> cursores = new HashMap<>();
        jogadores.forEach(jogador -> cursores.put(jogador, 0));
        StringBuilder textos = new StringBuilder();
        for (int rodada = 0; ; rodada++) {
            assertThat(rodada).as("a partida deveria terminar").isLessThan(ServidorWeb.MAXIMO_DE_JOGADAS);
            RELOGIO.avancar(Duration.ofSeconds(6));
            Map<Jogador, JsonNode> vistas = new HashMap<>();
            for (Jogador jogador : jogadores) {
                JsonNode vista = ver(jogador, cursores.get(jogador));
                vista.get("passos").forEach(passo -> textos.append(passo.get("eventos")));
                cursores.put(jogador, vista.get("cursor").asInt());
                vistas.put(jogador, vista);
            }
            if (vistas.get(jogadores.getFirst()).get("situacao").asString().equals("finalizada")) {
                return textos.toString();
            }
            for (Jogador jogador : jogadores) {
                JsonNode acoes = vistas.get(jogador).get("acoesLegais");
                if (!acoes.isEmpty()) {
                    HttpResponse<String> resposta = jogar(jogador, acoes.get(0).get("acao"), cursores.get(jogador));
                    assertThat(resposta.statusCode()).as(resposta.body()).isIn(200, 409);
                    break;
                }
            }
        }
    }
}
