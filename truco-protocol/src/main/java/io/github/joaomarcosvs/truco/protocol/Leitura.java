package io.github.joaomarcosvs.truco.protocol;

import java.util.Objects;

/**
 * O resultado de ler um JSON: o valor lido ou o motivo de o JSON ser inválido. Ler nunca lança exceção, porque o JSON vem
 * de fora (o servidor lê o que os clientes mandam).
 */
public sealed interface Leitura<T> {

    /** O JSON era válido e virou este valor. */
    record Lida<T>(T valor) implements Leitura<T> {

        public Lida {
            Objects.requireNonNull(valor, "valor");
        }
    }

    /** O JSON era inválido: malformado, com {@code "tipo"} desconhecido, campo faltando ou valor fora do modelo. */
    record Invalida<T>(String motivo) implements Leitura<T> {

        public Invalida {
            Objects.requireNonNull(motivo, "motivo");
        }
    }
}
