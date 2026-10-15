package io.github.joaomarcosvs.truco.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Criar salas, entrar nelas, achá-las pelo código e fechar as que expiram. */
class SalasTest {

    private final RelogioDeTeste relogio = new RelogioDeTeste(Instant.parse("2026-10-15T12:00:00Z"));
    private final Salas salas = new Salas(relogio, new Random(42));

    private Salas.Criada criar() {
        return (Salas.Criada) salas.criar();
    }

    @Test
    @DisplayName("O código tem 6 caracteres fáceis de ditar e pode ser digitado em minúsculas, com espaço ou hífen")
    void codigo() {
        Salas.Criada criada = criar();
        String codigo = criada.sala().codigo();

        assertThat(codigo).matches("[" + Salas.LETRAS_DO_CODIGO + "]{6}");
        String digitado = codigo.substring(0, 3).toLowerCase(Locale.ROOT) + "-" + codigo.substring(3) + " ";
        assertThat(salas.buscar(digitado)).containsSame(criada.sala());
        assertThat(salas.buscar("ABC0EF")).isEmpty();
        assertThat(salas.buscar("ABCDE")).isEmpty();
    }

    @Test
    @DisplayName("Cada jogador recebe um token próprio; uma terceira pessoa encontra a sala cheia, e um código sem sala"
            + " não existe")
    void entrar() {
        Salas.Criada criada = criar();

        Salas.Entrada entrada = salas.entrar(criada.sala().codigo());

        assertThat(entrada).isInstanceOf(Salas.Entrou.class);
        String tokenDoConvidado = ((Salas.Entrou) entrada).token();
        assertThat(tokenDoConvidado).isNotEqualTo(criada.token()).matches("[A-Za-z0-9_-]{22}");
        assertThat(criada.sala().jogadorDoToken(criada.token())).contains(Sala.ANFITRIAO);
        assertThat(criada.sala().jogadorDoToken(tokenDoConvidado)).contains(Sala.CONVIDADO);
        assertThat(salas.entrar(criada.sala().codigo())).isEqualTo(new Salas.Cheia());
        String semSala = criada.sala().codigo().equals("ZZZZZZ") ? "YYYYYY" : "ZZZZZZ";
        assertThat(salas.entrar(semSala)).isEqualTo(new Salas.NaoExiste());
    }

    @Test
    @DisplayName("No máximo 100 salas abertas; as que expiram liberam vaga")
    void limite() {
        for (int i = 0; i < Salas.MAXIMO_DE_SALAS; i++) {
            assertThat(salas.criar()).isInstanceOf(Salas.Criada.class);
        }
        assertThat(salas.criar()).isEqualTo(new Salas.SemVaga());

        relogio.avancar(Sala.SEM_JOGADAS);

        assertThat(salas.quantas()).isZero();
        assertThat(salas.criar()).isInstanceOf(Salas.Criada.class);
    }

    @Test
    @DisplayName("Uma sala expirada não é mais encontrada")
    void expirada() {
        Salas.Criada criada = criar();

        relogio.avancar(Sala.SEM_JOGADAS.minusSeconds(1));
        assertThat(salas.buscar(criada.sala().codigo())).isPresent();

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(salas.buscar(criada.sala().codigo())).isEmpty();
        assertThat(salas.entrar(criada.sala().codigo())).isEqualTo(new Salas.NaoExiste());
    }
}
