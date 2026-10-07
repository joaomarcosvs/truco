package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracao;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NovaPartidaTest {

    private final ConfiguracaoDaPartida configuracao = configuracao(2026);
    private final EstadoDaPartida estado = MOTOR.novaPartida(configuracao);
    private final Rodada rodada = estado.rodada();

    @Test
    @DisplayName("A partida começa na rodada 1, com o placar zerado")
    void comeco() {
        assertThat(estado.numeroDaRodada()).isEqualTo(1);
        assertThat(estado.placar().pontosDe(EQUIPE_DA_ANA)).isZero();
        assertThat(estado.placar().pontosDe(EQUIPE_DO_BETO)).isZero();
    }

    @Test
    @DisplayName("RG-PARTIDA-2: cada jogador recebe 3 cartas")
    void tresCartasParaCada() {
        assertThat(rodada.maoDe(ANA)).hasSize(3);
        assertThat(rodada.maoDe(BETO)).hasSize(3);
    }

    @Test
    @DisplayName("RG-CARTAS-3: depois da distribuição vira-se uma carta, que não fica na mão de ninguém nem no baralho")
    void vira() {
        Carta vira = rodada.vira().orElseThrow();

        assertThat(rodada.maoDe(ANA)).doesNotContain(vira);
        assertThat(rodada.maoDe(BETO)).doesNotContain(vira);
        assertThat(rodada.baralhoRestante()).doesNotContain(vira).hasSize(40 - 6 - 1);
    }

    @Test
    @DisplayName("RG-CARTAS-3: a vira não é jogada: só as posições da mão viram jogadas")
    void viraNaoEJogada() {
        JogadorId daVez = configuracao.aDireitaDe(estado.carteador());

        assertThat(MOTOR.acoesLegais(estado, daVez))
                .containsExactly(
                        new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new PedirAumento(), new Correr());
    }

    @Test
    @DisplayName("RG-PARTIDA-2 e RG-PARTIDA-4: as cartas saem uma a uma, a começar pelo jogador à direita do carteador,"
            + " e a vira é a carta seguinte")
    void ordemDaDistribuicao() {
        List<Carta> baralho =
                Sorteio.embaralhar(new TrucoPaulista().composicaoDoBaralho().cartas(), 2026, 1);
        JogadorId primeiro = configuracao.aDireitaDe(estado.carteador());

        assertThat(rodada.maoDe(primeiro)).containsExactly(baralho.get(0), baralho.get(2), baralho.get(4));
        assertThat(rodada.maoDe(estado.carteador())).containsExactly(baralho.get(1), baralho.get(3), baralho.get(5));
        assertThat(rodada.vira()).contains(baralho.get(6));
        assertThat(rodada.baralhoRestante()).isEqualTo(baralho.subList(7, 40));
    }

    @Test
    @DisplayName("RG-PARTIDA-3: a rodada começa valendo 1 ponto")
    void valeUmPonto() {
        assertThat(rodada.aposta()).isEqualTo(Aposta.inicial(1));
    }

    @Test
    @DisplayName("RG-PARTIDA-4: o carteador da 1ª rodada é sorteado pela seed")
    void carteadorSorteado() {
        Set<JogadorId> carteadores = LongStream.range(0, 50)
                .mapToObj(seed -> MOTOR.novaPartida(configuracao(seed)).carteador())
                .collect(toSet());

        assertThat(MOTOR.novaPartida(configuracao(2026)).carteador()).isEqualTo(estado.carteador());
        assertThat(carteadores).containsExactlyInAnyOrder(ANA, BETO);
    }

    @Test
    @DisplayName("RG-PARTIDA-4: abre a 1ª vaza o jogador à direita do carteador")
    void abreADireitaDoCarteador() {
        JogadorId aDireita = configuracao.aDireitaDe(estado.carteador());

        assertThat(rodada.fase()).isEqualTo(new FaseDaRodada.AguardandoJogada(aDireita));
        assertThat(MOTOR.acoesLegais(estado, estado.carteador())).isEmpty();
    }

    @Test
    @DisplayName("A mesma configuração gera sempre a mesma partida")
    void deterministica() {
        assertThat(MOTOR.novaPartida(configuracao(2026))).isEqualTo(estado);
    }

    @Test
    @DisplayName("RG-ESC-1: a v1 só aceita partidas 1x1")
    void soUmContraUm() {
        ConfiguracaoDaPartida duplas = new ConfiguracaoDaPartida(
                List.of(
                        new Equipe(new EquipeId("a"), List.of(new JogadorId("a1"), new JogadorId("a2"))),
                        new Equipe(new EquipeId("b"), List.of(new JogadorId("b1"), new JogadorId("b2")))),
                new TrucoPaulista(),
                0);

        assertThatIllegalArgumentException().isThrownBy(() -> MOTOR.novaPartida(duplas));
    }
}
