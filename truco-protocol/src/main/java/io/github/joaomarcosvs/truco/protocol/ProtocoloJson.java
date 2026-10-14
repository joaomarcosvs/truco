package io.github.joaomarcosvs.truco.protocol;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.Objects;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * O JSON que servidor e clientes trocam: ações (do cliente para o servidor), eventos e visões (do servidor para o
 * cliente). O formato está descrito em {@code docs/protocolo.md}. O JSON sai compacto e sempre igual para o mesmo valor;
 * a leitura é estrita e nunca lança exceção.
 *
 * <p>O estado completo da partida nunca vira JSON, porque tem informação oculta. Cabe ao servidor mandar a cada jogador
 * só a visão dele e os eventos que ele pode ver ({@code Evento.visivelPara}).
 */
public final class ProtocoloJson {

    private static final JsonMapper JSON = JsonMapper.builder()
            // Os campos saem na ordem dos records. As chaves dos objetos (placar, cartas na mão) já vêm em ordem
            // alfabética do Conversor.
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            // Ações e eventos sem campos, como {"tipo":"Aceitar"}.
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            // Leitura estrita: nada de campo desconhecido, faltando, número nulo ou texto depois do JSON.
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            // Nem número como texto ("0") nem fração (0.5) no lugar de inteiro.
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();

    private ProtocoloJson() {}

    public static String escrever(Acao acao) {
        return JSON.writeValueAsString(Conversor.dto(acao));
    }

    public static String escrever(Evento evento) {
        return JSON.writeValueAsString(Conversor.dto(evento));
    }

    public static String escrever(VisaoDoJogador visao) {
        return JSON.writeValueAsString(Conversor.dto(visao));
    }

    public static Leitura<Acao> lerAcao(String json) {
        return ler(json, AcaoDto.class, Conversor::acao);
    }

    public static Leitura<Evento> lerEvento(String json) {
        return ler(json, EventoDto.class, Conversor::evento);
    }

    public static Leitura<VisaoDoJogador> lerVisao(String json) {
        return ler(json, VisaoDto.class, Conversor::visao);
    }

    private static <D, T> Leitura<T> ler(String json, Class<D> tipo, Function<D, T> conversao) {
        Objects.requireNonNull(json, "json");
        try {
            return new Leitura.Lida<>(conversao.apply(JSON.readValue(json, tipo)));
        } catch (JacksonException e) {
            return new Leitura.Invalida<>(e.getOriginalMessage());
        } catch (NullPointerException | IllegalArgumentException e) {
            // O próprio modelo recusa o valor: um campo nulo, um placar negativo e afins.
            return new Leitura.Invalida<>(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
