package io.github.joaomarcosvs.truco.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Um relógio parado, que só anda quando o teste manda. */
final class RelogioDeTeste extends Clock {

    private Instant agora;

    RelogioDeTeste(Instant inicio) {
        this.agora = inicio;
    }

    void avancar(Duration tempo) {
        agora = agora.plus(tempo);
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        throw new UnsupportedOperationException();
    }
}
