package io.github.joaomarcosvs.truco.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A CLI de ponta a ponta, com a entrada roteirizada e a saída capturada. */
class TrucoCliTest {

    /** O que a CLI escreveu e o código de saída. */
    private record Execucao(int codigo, String saida) {

        List<String> linhas() {
            return saida.lines().toList();
        }
    }

    private static Execucao executar(String entrada, String... args) {
        return executar(StandardCharsets.UTF_8, entrada, args);
    }

    private static Execucao executar(Charset charsetDoTerminal, String entrada, String... args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream saida = new PrintStream(bytes, true, charsetDoTerminal);
        int codigo = TrucoCli.executar(args, new BufferedReader(new StringReader(entrada)), saida, () -> 77);
        return new Execucao(codigo, bytes.toString(charsetDoTerminal));
    }

    /** Sempre a opção 1, mais vezes do que uma partida precisa. */
    private static final String SEMPRE_A_PRIMEIRA = "1\n".repeat(2_000);

    @Test
    @DisplayName("Seed 2026: o começo da partida até a sua primeira decisão, sem mostrar as cartas do bot")
    void comeco() {
        Execucao execucao = executar("sair\n", "--seed", "2026");

        // As mesmas cartas do golden do core: você dá as cartas; o bot recebe 5♥ 10♣ 7♦ e abre com o 5♥. Ninguém tem
        // o 4♦, a primeira carta da sequência de descarte (a vira Q♦ faz do J a manilha).
        assertThat(execucao.codigo()).isEqualTo(1);
        assertThat(execucao.linhas())
                .containsExactly(
                        "Truco Paulista: você contra o bot. Seed 2026 (use --seed 2026 para repetir esta partida).",
                        "Em cada jogada, digite o número da opção e Enter; \"sair\" encerra.",
                        "",
                        "Rodada 1: você dá as cartas, vira Q♦.",
                        "Você recebeu 2♣ 3♣ J♦.",
                        "Descarte: a carta da vez é 4♦; você não pode descartar e passa.",
                        "Fim do descarte.",
                        "Bot jogou 5♥.",
                        "",
                        "Rodada 1 | vale 1 | você 0 x 0 bot | vira Q♦",
                        "Vaza 1: bot 5♥",
                        "Sua mão: [1] 2♣ [2] 3♣ [3] J♦ | bot: 2 cartas",
                        "O que você faz?",
                        "  1) Jogar 2♣",
                        "  2) Jogar 3♣",
                        "  3) Jogar J♦",
                        "  4) Pedir aumento para 3",
                        "> ",
                        "Partida interrompida. Seed: 2026.");
    }

    @Test
    @DisplayName("RG-FIM-1: escolhendo sempre a opção 1, a partida vai até o fim")
    void partidaInteira() {
        Execucao execucao = executar(SEMPRE_A_PRIMEIRA, "--seed", "2026");

        assertThat(execucao.codigo()).isZero();
        assertThat(execucao.linhas()).anyMatch(linha -> linha.startsWith("Fim de partida: "));
        assertThat(execucao.saida()).doesNotContain("Opção inválida");
    }

    @Test
    @DisplayName("RG-VIS-2: a CLI só narra os eventos que você pode ver: as cartas que o bot recebe nunca aparecem")
    void soOsSeusEventos() {
        Execucao execucao = executar(SEMPRE_A_PRIMEIRA, "--seed", "2026");

        long rodadas = execucao.linhas().stream()
                .filter(linha -> linha.matches("Rodada \\d+: .* dá as cartas.*"))
                .count();
        long escurinhos = execucao.linhas().stream()
                .filter(linha -> linha.startsWith("Rodada Escurinho: ninguém"))
                .count();
        assertThat(rodadas).isGreaterThan(1);
        // Na Escurinho, nem você recebe a identidade das cartas (RG-VIS-3).
        assertThat(execucao.linhas())
                .filteredOn(linha -> linha.startsWith("Você recebeu "))
                .hasSize((int) (rodadas - escurinhos));
        assertThat(execucao.linhas())
                .noneMatch(linha -> linha.startsWith("Bot recebeu ") || linha.startsWith("Bot comprou "));
    }

    @Test
    @DisplayName("Determinismo: a mesma seed com as mesmas escolhas dá a mesma partida")
    void deterministica() {
        assertThat(executar(SEMPRE_A_PRIMEIRA, "--seed", "99")).isEqualTo(executar(SEMPRE_A_PRIMEIRA, "--seed", "99"));
    }

    @Test
    @DisplayName("Entrada inválida pede de novo, sem chegar ao motor")
    void entradaInvalida() {
        Execucao execucao = executar("x\n0\n99\n\n" + SEMPRE_A_PRIMEIRA, "--seed", "2026");

        assertThat(execucao.codigo()).isZero();
        assertThat(execucao.linhas())
                .filteredOn(linha -> linha.contains("Opção inválida: digite um número de 1 a "))
                .hasSize(4);
    }

    @Test
    @DisplayName("\"sair\" ou o fim da entrada interrompem a partida, que pode ser repetida pela seed")
    void interromper() {
        Execucao comSair = executar("sair\n", "--seed", "2026");
        Execucao semEntrada = executar("", "--seed", "2026");

        assertThat(comSair.codigo()).isEqualTo(1);
        assertThat(comSair.linhas()).last().isEqualTo("Partida interrompida. Seed: 2026.");
        assertThat(semEntrada.codigo()).isEqualTo(1);
        assertThat(semEntrada.linhas()).last().isEqualTo("Partida interrompida. Seed: 2026.");
    }

    @Test
    @DisplayName("Sem --seed, a CLI sorteia uma seed e a mostra, para dar para repetir a partida")
    void seedSorteada() {
        assertThat(executar("sair\n").linhas().getFirst())
                .isEqualTo("Truco Paulista: você contra o bot. Seed 77 (use --seed 77 para repetir esta partida).");
    }

    @Test
    @DisplayName("Terminal sem os símbolos de naipe (ou --letras): as cartas aparecem com letras")
    void letras() {
        Execucao latin1 = executar(StandardCharsets.ISO_8859_1, "sair\n", "--seed", "2026");
        Execucao forcada = executar("sair\n", "--seed", "2026", "--letras");

        for (Execucao execucao : List.of(latin1, forcada)) {
            assertThat(execucao.linhas()).contains("Naipes: c = copas, e = espadas, o = ouros, p = paus.");
            assertThat(execucao.saida()).doesNotContain("♥", "♠", "♦", "♣");
        }
    }

    @Test
    @DisplayName("--ajuda mostra o uso; opção desconhecida ou seed inválida mostram o uso com erro")
    void opcoes() {
        Execucao ajuda = executar("", "--ajuda");
        assertThat(ajuda.codigo()).isZero();
        assertThat(ajuda.linhas().getFirst()).startsWith("Uso: java -jar truco-cli.jar");

        for (String[] invalidas :
                List.of(new String[] {"--turbo"}, new String[] {"--seed"}, new String[] {"--seed", "abc"})) {
            Execucao execucao = executar("", invalidas);
            assertThat(execucao.codigo()).isEqualTo(2);
            assertThat(execucao.linhas().getFirst()).startsWith("Opção inválida: ");
            assertThat(execucao.saida()).contains("Uso: java -jar truco-cli.jar");
        }
    }
}
