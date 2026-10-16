package io.github.joaomarcosvs.truco.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;

/**
 * Partidas inteiras com escolhas aleatórias, no 1x1 e no 2x2: a cada passo, toda ação legal, todo evento e a visão de
 * cada jogador viram JSON e voltam iguais. Assim o formato cobre o que o motor de fato produz: Onze, Escurinho,
 * encobertas, descarte, a dupla se vendo e fim de partida.
 */
class IdaEVoltaPropriedadesTest {

    private static final MotorDeTruco MOTOR = MotorDeTruco.novo();
    private static final JogadorId ANA = new JogadorId("ana");
    private static final JogadorId BETO = new JogadorId("beto");
    private static final JogadorId CARLA = new JogadorId("carla");
    private static final JogadorId DAVI = new JogadorId("davi");
    private static final int LIMITE_DE_PASSOS = 5_000;

    @Property(tries = 200)
    @Label("Ida e volta: ações, eventos e visões de partidas inteiras, 1x1 e 2x2, viram JSON e voltam iguais, e o mesmo"
            + " valor dá sempre o mesmo JSON")
    void idaEVolta(@ForAll boolean emDuplas, @ForAll long seed, @ForAll long sementeDasEscolhas) {
        Random escolhas = new Random(sementeDasEscolhas);
        ConfiguracaoDaPartida configuracao = emDuplas
                ? new ConfiguracaoDaPartida(
                        List.of(
                                new Equipe(new EquipeId("ana-carla"), List.of(ANA, CARLA)),
                                new Equipe(new EquipeId("beto-davi"), List.of(BETO, DAVI))),
                        new TrucoPaulista(),
                        seed)
                : new ConfiguracaoDaPartida(
                        List.of(Equipe.individual(ANA), Equipe.individual(BETO)), new TrucoPaulista(), seed);
        List<JogadorId> jogadores = configuracao.jogadoresNaOrdemDaMesa();
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao);
        conferirVisoes(estado, jogadores);
        for (int passo = 0; !(estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada); passo++) {
            assertThat(passo).as("a partida deveria terminar").isLessThan(LIMITE_DE_PASSOS);
            EstadoDaPartida atual = estado;
            JogadorId jogador = jogadores.stream()
                    .filter(candidato -> !MOTOR.acoesLegais(atual, candidato).isEmpty())
                    .findFirst()
                    .orElseThrow();
            List<Acao> legais = MOTOR.acoesLegais(estado, jogador);
            for (Acao acao : legais) {
                conferir(acao, ProtocoloJson.escrever(acao), ProtocoloJson.lerAcao(ProtocoloJson.escrever(acao)));
            }
            Aplicada aplicada = (Aplicada) MOTOR.aplicar(estado, jogador, legais.get(escolhas.nextInt(legais.size())));
            for (Evento evento : aplicada.eventos()) {
                String json = ProtocoloJson.escrever(evento);
                conferir(evento, json, ProtocoloJson.lerEvento(json));
            }
            estado = aplicada.novoEstado();
            conferirVisoes(estado, jogadores);
        }
    }

    private static void conferirVisoes(EstadoDaPartida estado, List<JogadorId> jogadores) {
        for (JogadorId jogador : jogadores) {
            VisaoDoJogador visao = MOTOR.visaoDe(estado, jogador);
            String json = ProtocoloJson.escrever(visao);
            conferir(visao, json, ProtocoloJson.lerVisao(json));
        }
    }

    private static <T> void conferir(T original, String json, Leitura<T> leitura) {
        assertThat(leitura).as("ida e volta de %s", json).isEqualTo(new Leitura.Lida<>(original));
        assertThat(json).as("JSON de %s", original).isEqualTo(escreverDeNovo(original));
    }

    /** O mesmo valor escrito de novo, para conferir que o JSON não depende de nada além do valor. */
    private static String escreverDeNovo(Object valor) {
        return switch (valor) {
            case Acao acao -> ProtocoloJson.escrever(acao);
            case Evento evento -> ProtocoloJson.escrever(evento);
            case VisaoDoJogador visao -> ProtocoloJson.escrever(visao);
            default -> throw new IllegalArgumentException(valor.toString());
        };
    }
}
