package io.github.joaomarcosvs.truco.regras.paulista;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static org.assertj.core.api.Assertions.assertThat;

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
import io.github.joaomarcosvs.truco.regras.RegrasDeVaza;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegrasDeVazaPaulistaTest {

    private static final JogadorId ANA = new JogadorId("ana");
    private static final JogadorId BETO = new JogadorId("beto");
    private static final EquipeId EQUIPE_DA_ANA = new EquipeId("ana");
    private static final EquipeId EQUIPE_DO_BETO = new EquipeId("beto");
    /** Com vira 7♦, a manilha é o 10. */
    private static final Optional<Carta> VIRA = Optional.of(carta("7♦"));

    private final RegrasDeVaza regras = new TrucoPaulista().regrasDeVaza();

    @Test
    @DisplayName("RG-PARTIDA-2: cada jogador recebe 3 cartas")
    void tresCartasPorJogador() {
        assertThat(regras.cartasPorJogador()).isEqualTo(3);
    }

    @Test
    @DisplayName("RG-VAZA-2: vence a vaza quem jogou a carta mais forte")
    void maisForteVence() {
        assertThat(resultado(new Jogada(BETO, carta("5♦")), new Jogada(ANA, carta("3♠"))))
                .isEqualTo(new Vencida(ANA, EQUIPE_DA_ANA));
        assertThat(resultado(new Jogada(BETO, carta("10♦")), new Jogada(ANA, carta("3♠"))))
                .isEqualTo(new Vencida(BETO, EQUIPE_DO_BETO));
    }

    @Test
    @DisplayName("RG-CARTAS-2: cartas comuns de mesmo valor, de equipes adversárias, empatam a vaza")
    void mesmoValorEmpata() {
        assertThat(resultado(new Jogada(BETO, carta("A♥")), new Jogada(ANA, carta("A♠"))))
                .isEqualTo(new Empatada());
    }

    @Test
    @DisplayName("RG-VAZA-2: quem vence a vaza abre a seguinte")
    void vencedorAbreASeguinte() {
        Vaza vaza = new Vaza(
                List.of(new Jogada(BETO, carta("5♦")), new Jogada(ANA, carta("3♠"))), new Vencida(ANA, EQUIPE_DA_ANA));

        assertThat(regras.abreAProxima(vaza)).isEqualTo(ANA);
    }

    @Test
    @DisplayName("RG-EMP-6: depois de uma vaza empatada, abre a seguinte quem abriu a empatada")
    void quemAbriuOEmpateAbreASeguinte() {
        Vaza vaza = new Vaza(List.of(new Jogada(ANA, carta("A♠")), new Jogada(BETO, carta("A♥"))), new Empatada());

        assertThat(regras.abreAProxima(vaza)).isEqualTo(ANA);
    }

    @ParameterizedTest(name = "{2}: vazas {0} → {1}")
    @CsvSource({
        "A,   pendente, RG-VAZA-3",
        "E,   pendente, RG-EMP-1",
        "AA,  ana,      RG-VAZA-3",
        "AB,  pendente, RG-VAZA-3",
        "EA,  ana,      RG-EMP-1",
        "AE,  ana,      RG-EMP-2",
        "BE,  beto,     RG-EMP-2",
        "EE,  pendente, RG-EMP-3",
        "ABA, ana,      RG-VAZA-3",
        "ABB, beto,     RG-VAZA-3",
        "AAB, ana,      RG-VAZA-3",
        "ABE, ana,      RG-EMP-4",
        "BAE, beto,     RG-EMP-4",
        "EEA, ana,      RG-EMP-3",
        "EEE, anulada,  RG-EMP-5"
    })
    @DisplayName("RG-VAZA-3 e RG-EMP-1 a RG-EMP-5: desfecho da rodada pelas vazas (A: Ana venceu, B: Beto venceu, E:"
            + " empate)")
    void desfecho(String vazas, String esperado, String regra) {
        Optional<DesfechoDaRodada> desfecho = regras.desfecho(
                vazas.chars().mapToObj(letra -> vaza((char) letra)).toList());

        switch (esperado) {
            case "pendente" -> assertThat(desfecho).isEmpty();
            case "anulada" -> assertThat(desfecho).contains(new Anulada());
            default -> assertThat(desfecho).contains(new Vitoria(new EquipeId(esperado)));
        }
    }

    private ResultadoDaVaza resultado(Jogada... jogadas) {
        return regras.resultado(List.of(jogadas), VIRA, jogador -> new EquipeId(jogador.valor()));
    }

    /** Uma vaza resumida pelo resultado; as cartas não importam para o desfecho. */
    private static Vaza vaza(char resultado) {
        List<Jogada> jogadas = List.of(new Jogada(BETO, carta("4♦")), new Jogada(ANA, carta("4♠")));
        return new Vaza(
                jogadas,
                switch (resultado) {
                    case 'A' -> new Vencida(ANA, EQUIPE_DA_ANA);
                    case 'B' -> new Vencida(BETO, EQUIPE_DO_BETO);
                    case 'E' -> new Empatada();
                    default -> throw new IllegalArgumentException("Resultado desconhecido: " + resultado);
                });
    }
}
