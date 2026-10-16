package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.CARLA;
import static io.github.joaomarcosvs.truco.partida.Partidas.DAVI;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracao;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracaoEmDuplas;
import static io.github.joaomarcosvs.truco.partida.Partidas.passarDescarte;
import static io.github.joaomarcosvs.truco.partida.Partidas.primeiraAcaoLegal;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.ArrayList;
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

        assertThat(MOTOR.acoesLegais(passarDescarte(estado), daVez))
                .containsExactly(new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new PedirAumento());
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
        EstadoDaPartida primeiraVaza = passarDescarte(estado);

        assertThat(primeiraVaza.rodada().fase()).isEqualTo(new FaseDaRodada.AguardandoJogada(aDireita));
        assertThat(MOTOR.acoesLegais(primeiraVaza, estado.carteador())).isEmpty();
    }

    @Test
    @DisplayName("RG-DESC-2: a rodada começa pelo descarte, com a primeira carta da sequência como carta da vez")
    void comecaPeloDescarte() {
        Carta primeiraDaSequencia =
                new TrucoPaulista().regrasDeDescarte().sequencia(rodada.vira()).getFirst();

        assertThat(rodada.fase()).isEqualTo(new FaseDaRodada.AguardandoDescarte(primeiraDaSequencia));
        assertThat(MOTOR.acoesLegais(estado, ANA)).contains(new RecusarDescarte());
        assertThat(MOTOR.acoesLegais(estado, BETO)).contains(new RecusarDescarte());
    }

    @Test
    @DisplayName("A mesma configuração gera sempre a mesma partida")
    void deterministica() {
        assertThat(MOTOR.novaPartida(configuracao(2026))).isEqualTo(estado);
    }

    @Test
    @DisplayName("RG-ESC-1: o Truco Paulista não aceita equipes de três nem mais de duas equipes")
    void formatosRecusados() {
        ConfiguracaoDaPartida trios = new ConfiguracaoDaPartida(
                List.of(
                        new Equipe(new EquipeId("a"), List.of(jogador("a1"), jogador("a2"), jogador("a3"))),
                        new Equipe(new EquipeId("b"), List.of(jogador("b1"), jogador("b2"), jogador("b3")))),
                new TrucoPaulista(),
                0);
        ConfiguracaoDaPartida tresEquipes = new ConfiguracaoDaPartida(
                List.of(
                        Equipe.individual(jogador("a")),
                        Equipe.individual(jogador("b")),
                        Equipe.individual(jogador("c"))),
                new TrucoPaulista(),
                0);

        assertThatIllegalArgumentException().isThrownBy(() -> MOTOR.novaPartida(trios));
        assertThatIllegalArgumentException().isThrownBy(() -> MOTOR.novaPartida(tresEquipes));
    }

    @Test
    @DisplayName("RG-ESC-1 e RG-2X2-1: no 2x2, as duplas se alternam na mesa, e cada um tem o parceiro em frente")
    void duplasNaMesa() {
        ConfiguracaoDaPartida duplas = configuracaoEmDuplas(2026);

        assertThat(duplas.jogadoresNaOrdemDaMesa()).containsExactly(ANA, BETO, CARLA, DAVI);
        assertThat(duplas.parceirosDe(ANA)).containsExactly(CARLA);
        assertThat(duplas.parceirosDe(DAVI)).containsExactly(BETO);
        assertThat(configuracao.parceirosDe(ANA)).isEmpty();
    }

    @Test
    @DisplayName("RG-2X2-2: no 2x2, cada um dos quatro recebe 3 cartas, uma por vez a partir da direita do carteador,"
            + " e sobram a vira e 27 cartas no baralho")
    void distribuicaoEmDuplas() {
        EstadoDaPartida duplas = MOTOR.novaPartida(configuracaoEmDuplas(2026));
        List<Carta> baralho =
                Sorteio.embaralhar(new TrucoPaulista().composicaoDoBaralho().cartas(), 2026, 1);
        List<JogadorId> ordem =
                duplas.configuracao().jogadoresAPartirDe(duplas.configuracao().aDireitaDe(duplas.carteador()));

        for (int posicao = 0; posicao < 4; posicao++) {
            assertThat(duplas.rodada().maoDe(ordem.get(posicao)))
                    .containsExactly(baralho.get(posicao), baralho.get(posicao + 4), baralho.get(posicao + 8));
        }
        assertThat(duplas.rodada().vira()).contains(baralho.get(12));
        assertThat(duplas.rodada().baralhoRestante()).hasSize(27);
        assertThat(passarDescarte(duplas).rodada().fase())
                .isEqualTo(new FaseDaRodada.AguardandoJogada(ordem.getFirst()));
    }

    @Test
    @DisplayName("RG-2X2-2: no 2x2, o carteador passa para a direita a cada rodada, pelos quatro")
    void carteadorGiraEmDuplas() {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracaoEmDuplas(7));
        List<JogadorId> carteadores = new ArrayList<>(List.of(estado.carteador()));
        while (carteadores.size() < 5) {
            Aplicada aplicada = primeiraAcaoLegal(estado);
            if (aplicada.novoEstado().numeroDaRodada() != estado.numeroDaRodada()) {
                carteadores.add(aplicada.novoEstado().carteador());
            }
            estado = aplicada.novoEstado();
        }

        for (int i = 1; i < carteadores.size(); i++) {
            assertThat(carteadores.get(i)).isEqualTo(estado.configuracao().aDireitaDe(carteadores.get(i - 1)));
        }
    }

    private static JogadorId jogador(String nome) {
        return new JogadorId(nome);
    }
}
