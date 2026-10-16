package io.github.joaomarcosvs.truco.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Uma ação no JSON, com o campo {@code "tipo"}: {@code {"tipo":"JogarCarta","indiceNaMao":0}}. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "tipo")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AcaoDto.JogarCarta.class, name = "JogarCarta"),
    @JsonSubTypes.Type(value = AcaoDto.JogarEncoberta.class, name = "JogarEncoberta"),
    @JsonSubTypes.Type(value = AcaoDto.Descartar.class, name = "Descartar"),
    @JsonSubTypes.Type(value = AcaoDto.RecusarDescarte.class, name = "RecusarDescarte"),
    @JsonSubTypes.Type(value = AcaoDto.PedirAumento.class, name = "PedirAumento"),
    @JsonSubTypes.Type(value = AcaoDto.Aceitar.class, name = "Aceitar"),
    @JsonSubTypes.Type(value = AcaoDto.Correr.class, name = "Correr"),
    @JsonSubTypes.Type(value = AcaoDto.VerCartasDoParceiro.class, name = "VerCartasDoParceiro")
})
sealed interface AcaoDto {

    record JogarCarta(int indiceNaMao) implements AcaoDto {}

    record JogarEncoberta(int indiceNaMao) implements AcaoDto {}

    record Descartar(int indiceNaMao) implements AcaoDto {}

    record RecusarDescarte() implements AcaoDto {}

    record PedirAumento() implements AcaoDto {}

    record Aceitar() implements AcaoDto {}

    record Correr() implements AcaoDto {}

    record VerCartasDoParceiro() implements AcaoDto {}
}
