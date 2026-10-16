package io.github.joaomarcosvs.truco.web;

import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * As salas abertas, só na memória do servidor: somem quando expiram ({@link Sala#expirada}) ou quando o servidor
 * reinicia. O código da sala, o token de cada jogador e a seed saem do gerador aleatório ({@link SecureRandom} no
 * servidor).
 */
final class Salas {

    /** Para não lotar a memória do servidor gratuito. */
    static final int MAXIMO_DE_SALAS = 100;

    /** Sem 0, O, 1, I e L, que se confundem ao ditar o código. */
    static final String LETRAS_DO_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    static final int TAMANHO_DO_CODIGO = 6;

    private static final int BYTES_DO_TOKEN = 16;

    private final Clock relogio;
    private final RandomGenerator aleatorio;
    private final Map<String, Sala> abertas = new HashMap<>();

    Salas(Clock relogio, RandomGenerator aleatorio) {
        this.relogio = relogio;
        this.aleatorio = aleatorio;
    }

    /** As salas do servidor: relógio do sistema e {@link SecureRandom}. */
    static Salas novas() {
        return new Salas(Clock.systemUTC(), new SecureRandom());
    }

    /** O resultado de criar uma sala. */
    sealed interface Criacao {}

    /** A sala nova, com o token do anfitrião. */
    record Criada(Sala sala, String token) implements Criacao {}

    /** Já há {@link #MAXIMO_DE_SALAS} salas abertas. */
    record SemVaga() implements Criacao {}

    /** O resultado de entrar numa sala. */
    sealed interface Entrada {}

    /** A pessoa entrou, com este token, e sentou neste lugar. */
    record Entrou(Sala sala, String token, JogadorId lugar) implements Entrada {}

    /** Não há sala aberta com esse código. */
    record NaoExiste() implements Entrada {}

    /** A sala não tem lugar vazio, ou a partida já começou. */
    record Cheia() implements Entrada {}

    /** Já há alguém com esse nome na sala. */
    record NomeRepetido() implements Entrada {}

    /** Cria uma sala do formato dado, com quem a criou, já com nome válido ({@link Sala#nome}), no primeiro lugar. */
    synchronized Criacao criar(Sala.Formato formato, String nome) {
        Instant agora = relogio.instant();
        fecharExpiradas(agora);
        if (abertas.size() >= MAXIMO_DE_SALAS) {
            return new SemVaga();
        }
        String codigo = novoCodigo();
        while (abertas.containsKey(codigo)) {
            codigo = novoCodigo();
        }
        String token = novoToken();
        Sala sala = new Sala(codigo, formato, aleatorio.nextLong(), nome, token, agora);
        abertas.put(codigo, sala);
        return new Criada(sala, token);
    }

    /** A pessoa, já com nome válido ({@link Sala#nome}), entra na sala do código. */
    synchronized Entrada entrar(String codigo, String nome) {
        Optional<Sala> sala = buscar(codigo);
        if (sala.isEmpty()) {
            return new NaoExiste();
        }
        String token = novoToken();
        return switch (sala.get().entrar(nome, token, relogio.instant())) {
            case Sala.Entrou(JogadorId lugar) -> new Entrou(sala.get(), token, lugar);
            case Sala.Cheia cheia -> new Cheia();
            case Sala.NomeRepetido repetido -> new NomeRepetido();
        };
    }

    /** A sala aberta com esse código, digitado como for (ver {@link #normalizar}). */
    synchronized Optional<Sala> buscar(String codigo) {
        fecharExpiradas(relogio.instant());
        return normalizar(codigo).map(abertas::get);
    }

    synchronized int quantas() {
        fecharExpiradas(relogio.instant());
        return abertas.size();
    }

    Instant agora() {
        return relogio.instant();
    }

    /** O código em maiúsculas e sem espaços nem hífens; vazio se não puder ser um código de sala. */
    static Optional<String> normalizar(String digitado) {
        String codigo = digitado.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        boolean valido = codigo.length() == TAMANHO_DO_CODIGO
                && codigo.chars().allMatch(letra -> LETRAS_DO_CODIGO.indexOf(letra) >= 0);
        return valido ? Optional.of(codigo) : Optional.empty();
    }

    private void fecharExpiradas(Instant agora) {
        abertas.values().removeIf(sala -> sala.expirada(agora));
    }

    private String novoCodigo() {
        StringBuilder codigo = new StringBuilder(TAMANHO_DO_CODIGO);
        for (int i = 0; i < TAMANHO_DO_CODIGO; i++) {
            codigo.append(LETRAS_DO_CODIGO.charAt(aleatorio.nextInt(LETRAS_DO_CODIGO.length())));
        }
        return codigo.toString();
    }

    private String novoToken() {
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
