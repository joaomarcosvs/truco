package io.github.joaomarcosvs.truco.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** O resultado de uma vaza no JSON: {@code {"tipo":"Vencida","jogador":...,"equipe":...}} ou {@code {"tipo":"Empatada"}}. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "tipo")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ResultadoDaVazaDto.Vencida.class, name = "Vencida"),
    @JsonSubTypes.Type(value = ResultadoDaVazaDto.Empatada.class, name = "Empatada")
})
sealed interface ResultadoDaVazaDto {

    record Vencida(String jogador, String equipe) implements ResultadoDaVazaDto {}

    record Empatada() implements ResultadoDaVazaDto {}
}
