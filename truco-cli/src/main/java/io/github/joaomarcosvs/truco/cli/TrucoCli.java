package io.github.joaomarcosvs.truco.cli;

import io.github.joaomarcosvs.truco.bots.Arbitro;
import io.github.joaomarcosvs.truco.bots.BotAleatorio;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * Cliente de console para jogar uma partida de Truco Paulista contra o {@link BotAleatorio}. A seed da partida também
 * alimenta o bot: a mesma seed com as mesmas jogadas repete a partida inteira.
 */
public final class TrucoCli {

    static final JogadorId VOCE = new JogadorId("voce");
    static final JogadorId BOT = new JogadorId("bot");

    private static final Map<JogadorId, String> NOMES = Map.of(VOCE, "você", BOT, "bot");

    private static final String USO = """
            Uso: java -jar truco-cli.jar [--seed N] [--letras]
              --seed N   repete uma partida: a mesma seed com as mesmas jogadas dá a mesma partida
              --letras   mostra os naipes com letras (c, e, o, p) em vez de símbolos""";

    private TrucoCli() {}

    public static void main(String[] args) {
        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in, System.out.charset()));
        int codigo = executar(
                args, entrada, System.out, () -> ThreadLocalRandom.current().nextLong(1_000_000));
        if (codigo != 0) {
            System.exit(codigo);
        }
    }

    /**
     * Lê as opções e joga uma partida.
     *
     * @param sorteioDaSeed a seed usada quando não vem {@code --seed}
     * @return o código de saída: 0 se a partida terminou ou foi pedida a ajuda, 1 se foi interrompida, 2 se as opções
     *     são inválidas
     */
    static int executar(String[] args, BufferedReader entrada, PrintStream saida, LongSupplier sorteioDaSeed) {
        Long seed = null;
        boolean letras = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> {
                    if (i + 1 >= args.length) {
                        return opcaoInvalida(saida, "--seed precisa de um número");
                    }
                    try {
                        seed = Long.parseLong(args[++i]);
                    } catch (NumberFormatException e) {
                        return opcaoInvalida(saida, "seed inválida: " + args[i]);
                    }
                }
                case "--letras" -> letras = true;
                case "--ajuda", "--help", "-h" -> {
                    saida.println(USO);
                    return 0;
                }
                default -> {
                    return opcaoInvalida(saida, "opção desconhecida: " + args[i]);
                }
            }
        }
        long seedDaPartida = seed != null ? seed : sorteioDaSeed.getAsLong();
        Notacao notacao = letras ? Notacao.LETRAS : Notacao.para(saida.charset());
        return jogar(seedDaPartida, notacao, entrada, saida);
    }

    /** Joga uma partida contra o bot; devolve 0 se ela terminou e 1 se o jogador a interrompeu. */
    static int jogar(long seed, Notacao notacao, BufferedReader entrada, PrintStream saida) {
        ConfiguracaoDaPartida configuracao = new ConfiguracaoDaPartida(
                List.of(Equipe.individual(VOCE), Equipe.individual(BOT)), new TrucoPaulista(), seed);
        MotorDeTruco motor = MotorDeTruco.novo();
        Narrador narrador = new Narrador(VOCE, NOMES, configuracao, notacao);
        JogadorHumano voce = new JogadorHumano(new Tela(narrador, configuracao.variante()), narrador, entrada, saida);
        Arbitro arbitro = new Arbitro(motor, Map.of(VOCE, voce, BOT, new BotAleatorio(seed)));

        saida.println("Truco Paulista: você contra o bot. Seed %d (use --seed %d para repetir esta partida)."
                .formatted(seed, seed));
        if (notacao == Notacao.LETRAS) {
            saida.println("Naipes: c = copas, e = espadas, o = ouros, p = paus.");
        }
        saida.println("Em cada jogada, digite o número da opção e Enter; \"sair\" encerra.");

        // A 1ª rodada já vem distribuída no estado inicial, sem eventos: é narrada como as seguintes.
        EstadoDaPartida inicio = motor.novaPartida(configuracao);
        VisaoDoJogador visao = motor.visaoDe(inicio, VOCE);
        narrar(
                new RodadaIniciada(
                        visao.numeroDaRodada(),
                        visao.carteador(),
                        visao.aposta().valor(),
                        visao.vira()),
                narrador,
                saida);
        narrar(new CartasDistribuidas(VOCE, visao.mao()), narrador, saida);
        try {
            arbitro.jogar(
                    inicio,
                    (antes, jogador, acao, aplicada) -> aplicada.eventos().stream()
                            .filter(evento -> evento.visivelPara(VOCE))
                            .forEach(evento -> narrar(evento, narrador, saida)));
        } catch (PartidaInterrompida interrompida) {
            saida.println();
            saida.println("Partida interrompida. Seed: %d.".formatted(seed));
            return 1;
        }
        return 0;
    }

    private static void narrar(Evento evento, Narrador narrador, PrintStream saida) {
        if (evento instanceof RodadaIniciada) {
            saida.println();
        }
        saida.println(narrador.descrever(evento));
    }

    private static int opcaoInvalida(PrintStream saida, String problema) {
        saida.println("Opção inválida: " + problema + ".");
        saida.println(USO);
        return 2;
    }
}
