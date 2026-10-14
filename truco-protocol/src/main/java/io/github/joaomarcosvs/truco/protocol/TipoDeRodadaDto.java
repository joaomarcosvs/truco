package io.github.joaomarcosvs.truco.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** O tipo da rodada no JSON: {@code {"tipo":"Normal"}}, {@code {"tipo":"DeOnze",...}} ou {@code {"tipo":"Escurinho"}}. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "tipo")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TipoDeRodadaDto.Normal.class, name = "Normal"),
    @JsonSubTypes.Type(value = TipoDeRodadaDto.DeOnze.class, name = "DeOnze"),
    @JsonSubTypes.Type(value = TipoDeRodadaDto.Escurinho.class, name = "Escurinho")
})
sealed interface TipoDeRodadaDto {

    record Normal() implements TipoDeRodadaDto {}

    record DeOnze(String equipeComOnze, boolean decidiuJogar) implements TipoDeRodadaDto {}

    record Escurinho() implements TipoDeRodadaDto {}
}
