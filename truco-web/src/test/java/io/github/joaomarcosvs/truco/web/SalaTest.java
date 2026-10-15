package io.github.joaomarcosvs.truco.web;

import static io.github.joaomarcosvs.truco.web.Sala.ANFITRIAO;
import static io.github.joaomarcosvs.truco.web.Sala.CONVIDADO;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.web.Sala.Situacao;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Uma sala com dois jogadores. Os casos do descarte usam a primeira seed (a partir de 1) em que a 1ª rodada tem a
 * situação procurada: quem tem a carta da vez e quem abre a 1ª vaza.
 */
class SalaTest {

    private static final Instant AGORA = Instant.parse("2026-10-15T12:00:00Z");
    private static final MotorDeTruco MOTOR = Passo.MOTOR;
    private static final List<Acao> ABRIR_A_RODADA =
            List.of(new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new PedirAumento());

    private static Sala comOsDois(long seed) {
        Sala sala = new Sala("ABCDEF", seed, "token-anfitriao", AGORA);
        assertThat(sala.entrar("token-convidado", AGORA)).isTrue();
        return sala;
    }

    private static EstadoDaPartida inicio(long seed) {
        return MOTOR.novaPartida(Sala.configuracao(seed));
    }

    private static Carta cartaDaVez(long seed) {
        return ((FaseDaRodada.AguardandoDescarte) inicio(seed).rodada().fase()).cartaDaVez();
    }

    /** Quem tem a 1ª carta da vez do descarte, na 1ª rodada; vazio se ninguém tem. */
    private static Optional<JogadorId> dono(long seed) {
        EstadoDaPartida estado = inicio(seed);
        return Stream.of(ANFITRIAO, CONVIDADO)
                .filter(jogador -> estado.rodada().maoDe(jogador).contains(cartaDaVez(seed)))
                .findFirst();
    }

    /** Quem abre a 1ª vaza, depois do descarte. */
    private static JogadorId quemAbre(long seed) {
        EstadoDaPartida estado = inicio(seed);
        for (JogadorId jogador : List.of(ANFITRIAO, CONVIDADO)) {
            estado = ((Aplicada) MOTOR.aplicar(estado, jogador, new RecusarDescarte())).novoEstado();
        }
        return ((FaseDaRodada.AguardandoJogada) estado.rodada().fase()).jogador();
    }

    private static JogadorId outro(JogadorId jogador) {
        return jogador.equals(ANFITRIAO) ? CONVIDADO : ANFITRIAO;
    }

    private static long seedEm(LongPredicate condicao) {
        return LongStream.rangeClosed(1, 10_000).filter(condicao).findFirst().orElseThrow();
    }

    private static long seedEmQueQuemNaoAbreTemACarta() {
        return seedEm(
                seed -> dono(seed).filter(dono -> !dono.equals(quemAbre(seed))).isPresent());
    }

    private static long seedEmQueQuemAbreTemACarta() {
        return seedEm(
                seed -> dono(seed).filter(dono -> dono.equals(quemAbre(seed))).isPresent());
    }

    private static long seedEmQueNinguemTemACarta() {
        return seedEm(seed -> dono(seed).isEmpty());
    }

    private static List<Acao> acoes(Sala sala, JogadorId jogador) {
        return sala.vistaDe(jogador, 0).opcoes().stream().map(Sala.Opcao::acao).toList();
    }

    private static int cursor(Sala sala, JogadorId jogador) {
        return sala.vistaDe(jogador, 0).cursor();
    }

    /** Os tipos dos eventos que o jogador viu depois do cursor. */
    private static List<String> eventos(Sala sala, JogadorId jogador, int desde) {
        return sala.vistaDe(jogador, desde).passos().stream()
                .flatMap(passo -> passo.eventos().stream())
                .map(evento -> evento.getClass().getSimpleName())
                .toList();
    }

    private static Sala.Jogada jogar(Sala sala, JogadorId jogador, Acao acao) {
        return sala.jogar(jogador, acao, cursor(sala, jogador), AGORA);
    }

    @Test
    @DisplayName("Antes de o convidado entrar, a sala espera e ninguém joga; depois, cada um recebe a própria mão")
    void entrada() {
        Sala sala = new Sala("ABCDEF", 2026, "token-anfitriao", AGORA);
        Sala.Vista esperando = sala.vistaDe(ANFITRIAO, 0);
        assertThat(esperando.situacao()).isEqualTo(Situacao.ESPERANDO_ADVERSARIO);
        assertThat(esperando.passos()).isEmpty();
        assertThat(esperando.opcoes()).isEmpty();
        assertThat(sala.jogar(ANFITRIAO, new JogarCarta(0), 0, AGORA)).isEqualTo(new Sala.Ilegal());

        assertThat(sala.entrar("token-convidado", AGORA)).isTrue();
        assertThat(sala.entrar("token-de-um-terceiro", AGORA)).isFalse();
        for (JogadorId jogador : List.of(ANFITRIAO, CONVIDADO)) {
            Sala.Vista vista = sala.vistaDe(jogador, 0);
            assertThat(vista.situacao()).isEqualTo(Situacao.JOGANDO);
            assertThat(vista.cursor()).isEqualTo(1);
            assertThat(vista.passos().getFirst().eventos())
                    .hasSize(2)
                    .last()
                    .isEqualTo(new CartasDistribuidas(
                            jogador, inicio(2026).rodada().maoDe(jogador)));
        }
    }

    @Test
    @DisplayName("Cada token identifica um jogador; um token desconhecido, nenhum")
    void tokens() {
        Sala sala = comOsDois(2026);

        assertThat(sala.jogadorDoToken("token-anfitriao")).contains(ANFITRIAO);
        assertThat(sala.jogadorDoToken("token-convidado")).contains(CONVIDADO);
        assertThat(sala.jogadorDoToken("token")).isEmpty();
    }

    @Test
    @DisplayName("RG-DESC-1 e RG-DESC-4: quem tem a carta da vez descarta antes de quem abre jogar; a jogada que quem"
            + " abre tinha preparado fica desatualizada")
    void descarteAntesDaPrimeiraJogada() {
        long seed = seedEmQueQuemNaoAbreTemACarta();
        JogadorId dono = dono(seed).orElseThrow();
        JogadorId abre = quemAbre(seed);
        int posicao = inicio(seed).rodada().maoDe(dono).indexOf(cartaDaVez(seed));
        Sala sala = comOsDois(seed);
        assertThat(acoes(sala, dono)).containsExactly(new Descartar(posicao));
        assertThat(acoes(sala, abre)).isEqualTo(ABRIR_A_RODADA);

        int cursorDeQuemAbre = cursor(sala, abre);
        assertThat(jogar(sala, dono, new Descartar(posicao))).isInstanceOf(Sala.Aceita.class);

        // RG-DESC-8: o descarte é público; a carta comprada, privada.
        assertThat(eventos(sala, dono, 1)).startsWith("CartaDescartada", "CartaRecebidaPorDescarte");
        assertThat(eventos(sala, abre, 1)).startsWith("CartaDescartada").doesNotContain("CartaRecebidaPorDescarte");
        assertThat(sala.jogar(abre, new JogarCarta(0), cursorDeQuemAbre, AGORA)).isEqualTo(new Sala.Desatualizada());
        assertThat(jogar(sala, abre, new JogarCarta(0))).isInstanceOf(Sala.Aceita.class);
    }

    @Test
    @DisplayName("RG-DESC-1: quando quem abre joga, o descarte acaba, e quem tinha a carta da vez não descarta mais")
    void primeiraJogadaFechaODescarte() {
        long seed = seedEmQueQuemNaoAbreTemACarta();
        JogadorId dono = dono(seed).orElseThrow();
        JogadorId abre = quemAbre(seed);
        int posicao = inicio(seed).rodada().maoDe(dono).indexOf(cartaDaVez(seed));
        Sala sala = comOsDois(seed);

        assertThat(jogar(sala, abre, new JogarCarta(0))).isInstanceOf(Sala.Aceita.class);

        for (JogadorId jogador : List.of(ANFITRIAO, CONVIDADO)) {
            assertThat(eventos(sala, jogador, 1)).containsExactly("DescarteEncerrado", "CartaJogada");
        }
        assertThat(acoes(sala, dono)).doesNotHaveAnyElementsOfTypes(Descartar.class);
        assertThat(jogar(sala, dono, new Descartar(posicao))).isEqualTo(new Sala.Ilegal());
    }

    @Test
    @DisplayName("RG-DESC-2: pedir aumento também fecha o descarte")
    void aumentoFechaODescarte() {
        long seed = seedEmQueQuemNaoAbreTemACarta();
        JogadorId dono = dono(seed).orElseThrow();
        Sala sala = comOsDois(seed);

        assertThat(jogar(sala, quemAbre(seed), new PedirAumento())).isInstanceOf(Sala.Aceita.class);

        assertThat(eventos(sala, dono, 1)).containsExactly("DescarteEncerrado", "AumentoPedido");
        assertThat(acoes(sala, dono)).containsExactly(new Aceitar(), new Correr(), new PedirAumento());
    }

    @Test
    @DisplayName("RG-DESC-4: se quem abre tem a carta da vez, pode descartá-la e depois jogar, e o outro só espera")
    void quemAbreTemACarta() {
        long seed = seedEmQueQuemAbreTemACarta();
        JogadorId abre = quemAbre(seed);
        int posicao = inicio(seed).rodada().maoDe(abre).indexOf(cartaDaVez(seed));
        Sala sala = comOsDois(seed);
        assertThat(acoes(sala, abre))
                .startsWith(new Descartar(posicao))
                .containsSubsequence(ABRIR_A_RODADA)
                .hasSize(5);
        assertThat(acoes(sala, outro(abre))).isEmpty();

        assertThat(jogar(sala, abre, new Descartar(posicao))).isInstanceOf(Sala.Aceita.class);

        assertThat(acoes(sala, abre)).containsSubsequence(ABRIR_A_RODADA);
        assertThat(jogar(sala, abre, new JogarCarta(0))).isInstanceOf(Sala.Aceita.class);
    }

    @Test
    @DisplayName("RG-DESC-10: para quem não tem a carta da vez, \"o dono não descartou\" e \"ninguém tinha a carta\""
            + " dão as mesmas opções e os mesmos eventos")
    void naoRevelaQuemTemACarta() {
        long semDono = seedEmQueNinguemTemACarta();
        // Quem abre não tem a carta: ou o outro tem e não descarta, ou ninguém tem.
        for (long seed : new long[] {seedEmQueQuemNaoAbreTemACarta(), semDono}) {
            Sala sala = comOsDois(seed);
            JogadorId abre = quemAbre(seed);
            assertThat(acoes(sala, abre)).as("seed %d", seed).isEqualTo(ABRIR_A_RODADA);
            jogar(sala, abre, new JogarCarta(0));
            assertThat(eventos(sala, abre, 1)).as("seed %d", seed).containsExactly("DescarteEncerrado", "CartaJogada");
        }
        // Quem não abre não tem a carta: ou quem abre tem e joga sem descartar, ou ninguém tem.
        for (long seed : new long[] {seedEmQueQuemAbreTemACarta(), semDono}) {
            Sala sala = comOsDois(seed);
            JogadorId espera = outro(quemAbre(seed));
            assertThat(acoes(sala, espera)).as("seed %d", seed).isEmpty();
            jogar(sala, quemAbre(seed), new JogarCarta(0));
            assertThat(eventos(sala, espera, 1))
                    .as("seed %d", seed)
                    .containsExactly("DescarteEncerrado", "CartaJogada");
        }
    }

    @Test
    @DisplayName("Fora da vez a jogada não vale; um cursor que não é o último está desatualizado")
    void foraDaVezECursor() {
        long seed = seedEmQueNinguemTemACarta();
        JogadorId abre = quemAbre(seed);
        Sala sala = comOsDois(seed);

        assertThat(jogar(sala, outro(abre), new JogarCarta(0))).isEqualTo(new Sala.Ilegal());
        assertThat(sala.jogar(abre, new JogarCarta(0), 0, AGORA)).isEqualTo(new Sala.Desatualizada());
        assertThat(sala.jogar(abre, new JogarCarta(0), 5, AGORA)).isEqualTo(new Sala.Desatualizada());
        assertThat(jogar(sala, abre, new JogarCarta(7))).isEqualTo(new Sala.Ilegal());
    }

    @Test
    @DisplayName("RG-FIM-1: jogando sempre a primeira opção, a partida acaba; depois do fim ninguém joga, e a sala some"
            + " 30 minutos depois")
    void partidaInteiraEExpiracao() {
        Sala sala = comOsDois(2026);
        Instant agora = AGORA;
        int jogadas = 0;
        while (sala.vistaDe(ANFITRIAO, 0).situacao() == Situacao.JOGANDO) {
            assertThat(jogadas++).isLessThan(ServidorWeb.MAXIMO_DE_JOGADAS);
            JogadorId quemJoga = Stream.of(ANFITRIAO, CONVIDADO)
                    .filter(jogador -> !acoes(sala, jogador).isEmpty())
                    .findFirst()
                    .orElseThrow();
            agora = agora.plusSeconds(1);
            assertThat(sala.jogar(quemJoga, acoes(sala, quemJoga).getFirst(), cursor(sala, quemJoga), agora))
                    .isInstanceOf(Sala.Aceita.class);
        }

        assertThat(sala.vistaDe(CONVIDADO, 0).situacao()).isEqualTo(Situacao.FINALIZADA);
        assertThat(acoes(sala, ANFITRIAO)).isEmpty();
        assertThat(acoes(sala, CONVIDADO)).isEmpty();
        assertThat(sala.jogar(ANFITRIAO, new JogarCarta(0), cursor(sala, ANFITRIAO), agora))
                .isEqualTo(new Sala.Ilegal());
        assertThat(sala.expirada(agora.plus(Sala.DEPOIS_DO_FIM).minusSeconds(1)))
                .isFalse();
        assertThat(sala.expirada(agora.plus(Sala.DEPOIS_DO_FIM))).isTrue();
    }

    @Test
    @DisplayName("Sem jogadas, a sala some depois de 2 horas")
    void expiraSemJogadas() {
        Sala sala = comOsDois(2026);

        assertThat(sala.expirada(AGORA.plus(Sala.SEM_JOGADAS).minusSeconds(1))).isFalse();
        assertThat(sala.expirada(AGORA.plus(Sala.SEM_JOGADAS))).isTrue();
    }
}
