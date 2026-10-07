package io.github.joaomarcosvs.truco.regras.paulista;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.DesfechoDaRodada;
import io.github.joaomarcosvs.truco.partida.DesfechoDaRodada.Anulada;
import io.github.joaomarcosvs.truco.partida.DesfechoDaRodada.Vitoria;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.Jogada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Empatada;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Vencida;
import io.github.joaomarcosvs.truco.partida.Vaza;
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import io.github.joaomarcosvs.truco.regras.RegrasDeVaza;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Vazas do Truco Paulista: 3 cartas por jogador, a carta mais forte vence a vaza e os empates seguem RG-EMP-1 a
 * RG-EMP-6.
 */
final class RegrasDeVazaPaulista implements RegrasDeVaza {

    private final OrdemDeForca ordemDeForca;

    RegrasDeVazaPaulista(OrdemDeForca ordemDeForca) {
        this.ordemDeForca = ordemDeForca;
    }

    @Override
    public int cartasPorJogador() {
        return 3; // RG-PARTIDA-2
    }

    @Override
    public ResultadoDaVaza resultado(
            List<Jogada> jogadas, Optional<Carta> vira, Function<JogadorId, EquipeId> equipeDe) {
        // RG-VAZA-2: vence a carta mais forte; se as mais fortes são de equipes diferentes, a vaza empata.
        int maisForte = jogadas.stream()
                .mapToInt(jogada -> ordemDeForca.forca(jogada.carta(), vira))
                .max()
                .orElseThrow(() -> new IllegalArgumentException("Uma vaza precisa de jogadas"));
        List<Jogada> maisFortes = jogadas.stream()
                .filter(jogada -> ordemDeForca.forca(jogada.carta(), vira) == maisForte)
                .toList();
        long equipes = maisFortes.stream()
                .map(jogada -> equipeDe.apply(jogada.jogador()))
                .distinct()
                .count();
        if (equipes > 1) {
            return new Empatada();
        }
        JogadorId vencedor = maisFortes.getFirst().jogador();
        return new Vencida(vencedor, equipeDe.apply(vencedor));
    }

    @Override
    public JogadorId abreAProxima(Vaza vaza) {
        return switch (vaza.resultado()) {
            case Vencida vencida -> vencida.jogador(); // RG-VAZA-2
            case Empatada empatada -> vaza.abridor(); // RG-EMP-6
        };
    }

    @Override
    public Optional<DesfechoDaRodada> desfecho(List<Vaza> vazas) {
        List<ResultadoDaVaza> resultados = vazas.stream().map(Vaza::resultado).toList();
        for (int quantas = 2; quantas <= resultados.size(); quantas++) {
            Optional<DesfechoDaRodada> desfecho = desfechoAoFimDe(resultados.subList(0, quantas));
            if (desfecho.isPresent()) {
                return desfecho;
            }
        }
        return Optional.empty();
    }

    /** Desfecho ao fim da última das vazas dadas, sabendo que as anteriores não decidiram a rodada. */
    private static Optional<DesfechoDaRodada> desfechoAoFimDe(List<ResultadoDaVaza> resultados) {
        return switch (resultados.size()) {
            case 2 -> desfechoAposDuas(resultados.get(0), resultados.get(1));
            case 3 -> Optional.of(desfechoAposTres(resultados.get(0), resultados.get(2)));
            default -> throw new IllegalArgumentException("Uma rodada tem até 3 vazas: " + resultados.size());
        };
    }

    private static Optional<DesfechoDaRodada> desfechoAposDuas(ResultadoDaVaza primeira, ResultadoDaVaza segunda) {
        if (primeira instanceof Vencida primeiraVencida && segunda instanceof Vencida segundaVencida) {
            // RG-VAZA-3: quem vence as duas primeiras vence a rodada; com uma para cada, a terceira decide.
            EquipeId equipe = primeiraVencida.equipe();
            return equipe.equals(segundaVencida.equipe()) ? Optional.of(new Vitoria(equipe)) : Optional.empty();
        }
        if (primeira instanceof Vencida primeiraVencida) {
            return Optional.of(new Vitoria(primeiraVencida.equipe())); // RG-EMP-2
        }
        if (segunda instanceof Vencida segundaVencida) {
            return Optional.of(new Vitoria(segundaVencida.equipe())); // RG-EMP-1
        }
        return Optional.empty(); // RG-EMP-3: com as duas primeiras empatadas, a terceira decide.
    }

    private static DesfechoDaRodada desfechoAposTres(ResultadoDaVaza primeira, ResultadoDaVaza terceira) {
        if (terceira instanceof Vencida terceiraVencida) {
            return new Vitoria(terceiraVencida.equipe()); // RG-VAZA-3 ou RG-EMP-3
        }
        if (primeira instanceof Vencida primeiraVencida) {
            return new Vitoria(primeiraVencida.equipe()); // RG-EMP-4
        }
        return new Anulada(); // RG-EMP-5
    }
}
