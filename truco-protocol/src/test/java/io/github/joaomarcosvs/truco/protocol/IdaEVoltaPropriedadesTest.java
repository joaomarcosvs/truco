package io.github.joaomarcosvs.truco.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
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
 * Partidas inteiras com escolhas aleatórias: a cada passo, toda ação legal, todo evento e a visão de cada jogador viram
 * JSON e voltam iguais. Assim o formato cobre o que o motor de fato produz: Onze, Escurinho, encobertas, descarte e fim
 * de partida.
 */
class IdaEVoltaPropriedadesTest {

    private static final MotorDeTruco MOTOR = MotorDeTruco.novo();
    private static final JogadorId ANA = new JogadorId("ana");
    private static final JogadorId BETO = new JogadorId("beto");
    private static final List<JogadorId> JOGADORES = List.of(ANA, BETO);
    private static final int LIMITE_DE_PASSOS = 5_000;

    @Property(tries = 200)
    @Label("Ida e volta: ações, eventos e visões de partidas inteiras viram JSON e voltam iguais, e o mesmo valor dá"
            + " sempre o mesmo JSON")
    void idaEVolta(@ForAll long seed, @ForAll long sementeDasEscolhas) {
        Random escolhas = new Random(sementeDasEscolhas);
        EstadoDaPartida estado = MOTOR.novaPartida(new ConfiguracaoDaPartida(
                List.of(Equipe.individual(ANA), Equipe.individual(BETO)), new TrucoPaulista(), seed));
        conferirVisoes(estado);
        for (int passo = 0; !(estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada); passo++) {
            assertThat(passo).as("a partida deveria terminar").isLessThan(LIMITE_DE_PASSOS);
            EstadoDaPartida atual = estado;
            JogadorId jogador = JOGADORES.stream()
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
            conferirVisoes(estado);
        }
    }

    private static void conferirVisoes(EstadoDaPartida estado) {
        for (JogadorId jogador : JOGADORES) {
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
