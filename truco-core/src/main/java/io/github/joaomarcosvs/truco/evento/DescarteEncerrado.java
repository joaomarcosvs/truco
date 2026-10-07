package io.github.joaomarcosvs.truco.evento;

/**
 * O descarte acabou. É o mesmo evento quando o dono da carta da vez recusa e quando ninguém a tinha, para não revelar
 * quem a tinha (RG-DESC-10).
 */
public record DescarteEncerrado() implements Evento {

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
