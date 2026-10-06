package io.github.joaomarcosvs.truco;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Confere a fundação do build: Java 21, JUnit 5, AssertJ e jqwik. */
class FundacaoTest {

    @Test
    @DisplayName("Os testes rodam em Java 21 ou superior")
    void rodaEmJava21OuSuperior() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }

    @Property(tries = 100)
    @Label("O jqwik gera entradas dentro das restrições pedidas")
    void jqwikRespeitaRestricoes(@ForAll @IntRange(min = 0, max = 39) int indice) {
        assertThat(indice).isBetween(0, 39);
    }
}
