package io.github.joaomarcosvs.truco.partida;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.RodadaAnulada;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoJogada;
import io.github.joaomarcosvs.truco.regras.RegrasDeVaza;
import io.github.joaomarcosvs.truco.regras.VarianteDeRegras;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/** Motor genérico: todas as regras vêm da variante da configuração (CLAUDE.md, seção 7). */
final class MotorGenerico implements MotorDeTruco {

    @Override
    public EstadoDaPartida novaPartida(ConfiguracaoDaPartida configuracao) {
        exigirUmContraUm(configuracao);
        List<JogadorId> mesa = configuracao.jogadoresNaOrdemDaMesa();
        // RG-PARTIDA-4: o carteador da 1ª rodada é sorteado pela seed.
        JogadorId carteador = mesa.get(Sorteio.carteadorInicial(configuracao.seed(), mesa.size()));
        return iniciarRodada(configuracao, Placar.zerado(configuracao.equipes()), 1, carteador, new ArrayList<>());
    }

    @Override
    public List<Acao> acoesLegais(EstadoDaPartida estado, JogadorId jogador) {
        Rodada rodada = estado.rodada();
        return switch (rodada.fase()) {
            case AguardandoJogada(JogadorId daVez)
            when daVez.equals(jogador) ->
                IntStream.range(0, rodada.maoDe(jogador).size())
                        .<Acao>mapToObj(JogarCarta::new)
                        .toList();
            case AguardandoJogada outroJogador -> List.of();
        };
    }

    @Override
    public Resultado aplicar(EstadoDaPartida estado, JogadorId jogador, Acao acao) {
        Objects.requireNonNull(acao, "acao");
        if (!estado.configuracao().participa(jogador)) {
            return new Rejeitada(MotivoDeRejeicao.JOGADOR_DESCONHECIDO);
        }
        List<Acao> legais = acoesLegais(estado, jogador);
        if (!legais.contains(acao)) {
            return new Rejeitada(
                    legais.isEmpty() ? MotivoDeRejeicao.NAO_E_A_VEZ_DO_JOGADOR : MotivoDeRejeicao.ACAO_INVALIDA);
        }
        List<Evento> eventos = new ArrayList<>();
        EstadoDaPartida novoEstado = switch (acao) {
            case JogarCarta(int indiceNaMao) -> jogarCarta(estado, jogador, indiceNaMao, eventos);
        };
        return new Aplicada(novoEstado, eventos);
    }

    @Override
    public VisaoDoJogador visaoDe(EstadoDaPartida estado, JogadorId jogador) {
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        if (!configuracao.participa(jogador)) {
            throw new IllegalArgumentException("Jogador fora da partida: " + jogador);
        }
        Rodada rodada = estado.rodada();
        List<JogadorId> mesa = configuracao.jogadoresNaOrdemDaMesa();
        Map<JogadorId, Integer> cartasNaMao = mesa.stream()
                .collect(toMap(identity(), outro -> rodada.maoDe(outro).size()));
        Optional<JogadorId> vezDe = switch (rodada.fase()) {
            case AguardandoJogada(JogadorId daVez) -> Optional.of(daVez);
        };
        return new VisaoDoJogador(
                jogador,
                mesa,
                configuracao.equipes(),
                estado.placar(),
                estado.numeroDaRodada(),
                estado.carteador(),
                rodada.valor(),
                rodada.vira(),
                rodada.maoDe(jogador),
                cartasNaMao,
                rodada.vazas(),
                rodada.vazaAtual(),
                vezDe);
    }

    private static EstadoDaPartida jogarCarta(
            EstadoDaPartida estado, JogadorId jogador, int indiceNaMao, List<Evento> eventos) {
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        RegrasDeVaza regras = configuracao.variante().regrasDeVaza();
        Rodada rodada = estado.rodada();

        List<Carta> mao = new ArrayList<>(rodada.maoDe(jogador));
        Carta carta = mao.remove(indiceNaMao);
        Map<JogadorId, List<Carta>> maos = new HashMap<>(rodada.maos());
        maos.put(jogador, mao);
        eventos.add(new CartaJogada(jogador, carta, rodada.numeroDaVazaAtual()));

        List<Jogada> vazaAtual = comAcrescimo(rodada.vazaAtual(), new Jogada(jogador, carta));
        List<Vaza> vazas = rodada.vazas();
        if (vazaAtual.size() == configuracao.jogadoresNaOrdemDaMesa().size()) {
            ResultadoDaVaza resultado = regras.resultado(vazaAtual, rodada.vira(), configuracao::equipeDe);
            eventos.add(new VazaFinalizada(rodada.numeroDaVazaAtual(), resultado));
            vazas = comAcrescimo(vazas, new Vaza(vazaAtual, resultado));
            vazaAtual = List.of();
            Optional<DesfechoDaRodada> desfecho = regras.desfecho(vazas);
            if (desfecho.isPresent()) {
                return encerrarRodada(estado, desfecho.get(), eventos);
            }
        }
        FaseDaRodada fase = proximaFase(configuracao, estado.carteador(), vazas, vazaAtual);
        Rodada novaRodada =
                new Rodada(rodada.valor(), rodada.vira(), maos, rodada.baralhoRestante(), vazas, vazaAtual, fase);
        return new EstadoDaPartida(
                configuracao, estado.placar(), estado.numeroDaRodada(), estado.carteador(), novaRodada);
    }

    private static EstadoDaPartida encerrarRodada(
            EstadoDaPartida estado, DesfechoDaRodada desfecho, List<Evento> eventos) {
        int valor = estado.rodada().valor();
        Placar placar = switch (desfecho) {
            case DesfechoDaRodada.Vitoria(EquipeId equipe) -> {
                Placar atualizado = estado.placar().somando(equipe, valor);
                eventos.add(new RodadaFinalizada(estado.numeroDaRodada(), equipe, valor));
                eventos.add(new PlacarAtualizado(atualizado));
                yield atualizado;
            }
            case DesfechoDaRodada.Anulada anulada -> {
                eventos.add(new RodadaAnulada(estado.numeroDaRodada())); // RG-EMP-5: ninguém pontua.
                yield estado.placar();
            }
        };
        // RG-PARTIDA-4: o carteador passa para o jogador à direita.
        JogadorId carteador = estado.configuracao().aDireitaDe(estado.carteador());
        return iniciarRodada(estado.configuracao(), placar, estado.numeroDaRodada() + 1, carteador, eventos);
    }

    private static EstadoDaPartida iniciarRodada(
            ConfiguracaoDaPartida configuracao,
            Placar placar,
            int numeroDaRodada,
            JogadorId carteador,
            List<Evento> eventos) {
        VarianteDeRegras variante = configuracao.variante();
        List<Carta> baralho =
                Sorteio.embaralhar(variante.composicaoDoBaralho().cartas(), configuracao.seed(), numeroDaRodada);
        List<JogadorId> ordem = configuracao.jogadoresAPartirDe(configuracao.aDireitaDe(carteador));

        // RG-PARTIDA-2 e RG-PARTIDA-4: uma carta por vez, começando pelo jogador à direita do carteador.
        Map<JogadorId, List<Carta>> maos = new LinkedHashMap<>();
        ordem.forEach(jogador -> maos.put(jogador, new ArrayList<>()));
        int proxima = 0;
        for (int volta = 0; volta < variante.regrasDeVaza().cartasPorJogador(); volta++) {
            for (JogadorId jogador : ordem) {
                maos.get(jogador).add(baralho.get(proxima++));
            }
        }
        // RG-CARTAS-3: depois de distribuir, vira-se a carta seguinte do baralho.
        Optional<Carta> vira =
                variante.ordemDeForca().usaVira() ? Optional.of(baralho.get(proxima++)) : Optional.empty();
        int valor = variante.escadaDeApostas().valorInicial(); // RG-PARTIDA-3

        eventos.add(new RodadaIniciada(numeroDaRodada, carteador, valor, vira));
        ordem.forEach(jogador -> eventos.add(new CartasDistribuidas(jogador, maos.get(jogador))));

        FaseDaRodada fase = proximaFase(configuracao, carteador, List.of(), List.of());
        Rodada rodada =
                new Rodada(valor, vira, maos, baralho.subList(proxima, baralho.size()), List.of(), List.of(), fase);
        return new EstadoDaPartida(configuracao, placar, numeroDaRodada, carteador, rodada);
    }

    /**
     * Ponto único que decide a fase seguinte (CLAUDE.md, seção 7): com uma vaza em andamento, joga quem está à direita
     * do último; no começo da rodada, quem está à direita do carteador; depois de uma vaza, quem a variante indicar.
     */
    private static FaseDaRodada proximaFase(
            ConfiguracaoDaPartida configuracao, JogadorId carteador, List<Vaza> vazas, List<Jogada> vazaAtual) {
        if (!vazaAtual.isEmpty()) {
            return new AguardandoJogada(
                    configuracao.aDireitaDe(vazaAtual.getLast().jogador())); // RG-VAZA-1
        }
        if (vazas.isEmpty()) {
            return new AguardandoJogada(configuracao.aDireitaDe(carteador)); // RG-PARTIDA-4
        }
        // RG-VAZA-2 e RG-EMP-6
        return new AguardandoJogada(configuracao.variante().regrasDeVaza().abreAProxima(vazas.getLast()));
    }

    private static void exigirUmContraUm(ConfiguracaoDaPartida configuracao) {
        // RG-ESC-1: a v1 é 1x1; as regras de duplas (seção 11 das regras) ainda não existem.
        boolean umContraUm = configuracao.equipes().size() == 2
                && configuracao.equipes().stream()
                        .allMatch(equipe -> equipe.jogadores().size() == 1);
        if (!umContraUm) {
            throw new IllegalArgumentException("A v1 só aceita partidas 1x1 (RG-ESC-1)");
        }
    }

    private static <T> List<T> comAcrescimo(List<T> lista, T elemento) {
        List<T> nova = new ArrayList<>(lista);
        nova.add(elemento);
        return List.copyOf(nova);
    }
}
