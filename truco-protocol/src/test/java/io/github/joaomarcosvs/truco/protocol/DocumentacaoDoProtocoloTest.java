package io.github.joaomarcosvs.truco.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.evento.Evento;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Os exemplos de {@code docs/protocolo.md} são os goldens do formato: cada um precisa ser lido sem erro, ser escrito de
 * volta com o mesmo conteúdo e ter os mesmos campos, na mesma ordem, que o record do modelo. Assim a documentação não
 * fica para trás quando o formato muda.
 */
class DocumentacaoDoProtocoloTest {

    private static final Path DOCUMENTO = Path.of("..", "docs", "protocolo.md");

    /** Um bloco de código marcado como {@code json acao}, {@code json evento} ou {@code json visao}. */
    private static final Pattern EXEMPLO = Pattern.compile("```json (acao|evento|visao)\\R(.*?)\\R```", Pattern.DOTALL);

    private static final JsonMapper ARVORE = JsonMapper.builder().build();

    private record Exemplo(String tipo, String json) {}

    private static List<Exemplo> exemplos(String tipo) throws IOException {
        String documento = Files.readString(DOCUMENTO, StandardCharsets.UTF_8);
        List<Exemplo> exemplos = new ArrayList<>();
        Matcher matcher = EXEMPLO.matcher(documento);
        while (matcher.find()) {
            if (matcher.group(1).equals(tipo)) {
                exemplos.add(new Exemplo(tipo, matcher.group(2)));
            }
        }
        return exemplos;
    }

    @Test
    @DisplayName("Os exemplos de ação são válidos, voltam iguais, têm os campos do modelo e cobrem todas as ações")
    void acoes() throws IOException {
        List<Exemplo> exemplos = exemplos("acao");

        Set<String> tipos = conferir(exemplos, ProtocoloJson::lerAcao, ProtocoloJson::escrever);

        assertThat(tipos).isEqualTo(nomes(Acao.class.getPermittedSubclasses()));
    }

    @Test
    @DisplayName("Os exemplos de evento são válidos, voltam iguais, têm os campos do modelo e cobrem todos os eventos")
    void eventos() throws IOException {
        List<Exemplo> exemplos = exemplos("evento");

        Set<String> tipos = conferir(exemplos, ProtocoloJson::lerEvento, ProtocoloJson::escrever);

        assertThat(tipos).isEqualTo(nomes(Evento.class.getPermittedSubclasses()));
    }

    @Test
    @DisplayName("Os exemplos de visão, no 1x1 e no 2x2, são válidos, voltam iguais e têm os campos da VisaoDoJogador,"
            + " na mesma ordem")
    void visao() throws IOException {
        List<Exemplo> exemplos = exemplos("visao");

        assertThat(exemplos).hasSize(2);
        conferir(exemplos, ProtocoloJson::lerVisao, ProtocoloJson::escrever);
    }

    /** Lê cada exemplo, escreve de volta e compara; devolve os valores de "tipo" encontrados. */
    private static <T> Set<String> conferir(
            List<Exemplo> exemplos, Function<String, Leitura<T>> ler, Function<T, String> escrever) {
        Set<String> tipos = new TreeSet<>();
        for (Exemplo exemplo : exemplos) {
            if (!(ler.apply(exemplo.json()) instanceof Leitura.Lida<T> lida)) {
                throw new AssertionError("Exemplo inválido: " + exemplo.json() + " -> " + ler.apply(exemplo.json()));
            }
            JsonNode documentado = ARVORE.readTree(exemplo.json());
            JsonNode escrito = ARVORE.readTree(escrever.apply(lida.valor()));
            List<String> componentes = componentes(lida.valor().getClass());

            assertThat(escrito)
                    .as("o exemplo %s escrito de volta", exemplo.json())
                    .isEqualTo(documentado);
            assertThat(campos(documentado))
                    .as("os campos do exemplo %s", exemplo.json())
                    .isEqualTo(componentes);
            assertThat(campos(escrito))
                    .as("os campos escritos de %s", exemplo.json())
                    .isEqualTo(componentes);
            if (documentado.has("tipo")) {
                tipos.add(documentado.get("tipo").asString());
            }
        }
        return tipos;
    }

    /** Os campos do objeto JSON, na ordem, sem o "tipo". */
    private static List<String> campos(JsonNode objeto) {
        return objeto.propertyNames().stream()
                .filter(nome -> !nome.equals("tipo"))
                .toList();
    }

    /** Os componentes do record do modelo, na ordem. */
    private static List<String> componentes(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static Set<String> nomes(Class<?>[] classes) {
        return Arrays.stream(classes).map(Class::getSimpleName).collect(Collectors.toCollection(TreeSet::new));
    }
}
