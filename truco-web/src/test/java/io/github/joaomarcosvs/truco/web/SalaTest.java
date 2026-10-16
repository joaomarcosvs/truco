package io.github.joaomarcosvs.truco.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.acao.VerCartasDoParceiro;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.web.Sala.Formato;
import io.github.joaomarcosvs.truco.web.Sala.Situacao;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Uma sala na memória do servidor. Os casos do descarte usam a primeira seed (a partir de 1) em que a 1ª rodada tem a
 * situação procurada: quem tem a carta da vez e quem abre a 1ª vaza.
 */
class SalaTest {

    private static final Instant AGORA = Instant.parse("2026-10-15T12:00:00Z");
    private static final MotorDeTruco MOTOR = Passo.MOTOR;
    private static final JogadorId LUGAR1 = new JogadorId("lugar1");
    private static final JogadorId LUGAR2 = new JogadorId("lugar2");
    private static final JogadorId LUGAR3 = new JogadorId("lugar3");
    private static final JogadorId LUGAR4 = new JogadorId("lugar4");
    private static final List<Acao> ABRIR_A_RODADA =
            List.of(new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new PedirAumento());

    /** Sala 1x1 com Ana (lugar 1) e Beto (lugar 2); a partida começa quando Beto entra. */
    private static Sala umContraUm(long seed) {
        Sala sala = new Sala("ABCDEF", Formato.UM_CONTRA_UM, seed, "Ana", "token-ana", AGORA);
        assertThat(sala.entrar("Beto", "token-beto", AGORA)).isEqualTo(new Sala.Entrou(LUGAR2));
        return sala;
    }

    /** Sala 2x2 com quatro pessoas, nos lugares 1 a 4, já começada. */
    private static Sala quatroPessoas(long seed) {
        Sala sala = new Sala("ABCDEF", Formato.DUPLAS, seed, "Ana", "token-ana", AGORA);
        sala.entrar("Beto", "token-beto", AGORA);
        sala.entrar("Carla", "token-carla", AGORA);
        sala.entrar("Davi", "token-davi", AGORA);
        assertThat(sala.comecar(LUGAR1, AGORA)).isInstanceOf(Sala.Feita.class);
        return sala;
    }

    private static EstadoDaPartida inicio(Formato formato, long seed) {
        return MOTOR.novaPartida(Sala.configuracao(formato, seed));
    }

    private static Carta cartaDaVez(Formato formato, long seed) {
        return ((FaseDaRodada.AguardandoDescarte) inicio(formato, seed).rodada().fase()).cartaDaVez();
    }

    /** Quem tem a 1ª carta da vez do descarte, na 1ª rodada; vazio se ninguém tem. */
    private static Optional<JogadorId> dono(Formato formato, long seed) {
        EstadoDaPartida estado = inicio(formato, seed);
        return estado.configuracao().jogadoresNaOrdemDaMesa().stream()
                .filter(jogador -> estado.rodada().maoDe(jogador).contains(cartaDaVez(formato, seed)))
                .findFirst();
    }

    /** Quem abre a 1ª vaza, depois do descarte. */
    private static JogadorId quemAbre(Formato formato, long seed) {
        EstadoDaPartida estado = inicio(formato, seed);
        for (JogadorId jogador : estado.configuracao().jogadoresNaOrdemDaMesa()) {
            estado = ((Aplicada) MOTOR.aplicar(estado, jogador, new RecusarDescarte())).novoEstado();
        }
        return ((FaseDaRodada.AguardandoJogada) estado.rodada().fase()).jogador();
    }

    private static Optional<JogadorId> dono(long seed) {
        return dono(Formato.UM_CONTRA_UM, seed);
    }

    private static JogadorId quemAbre(long seed) {
        return quemAbre(Formato.UM_CONTRA_UM, seed);
    }

    private static JogadorId outro(JogadorId jogador) {
        return jogador.equals(LUGAR1) ? LUGAR2 : LUGAR1;
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
        return acoes(sala, jogador, AGORA);
    }

    private static List<Acao> acoes(Sala sala, JogadorId jogador, Instant agora) {
        return sala.vistaDe(jogador, 0, agora).opcoes().stream()
                .map(Sala.Opcao::acao)
                .toList();
    }

    private static int cursor(Sala sala, JogadorId jogador) {
        return sala.vistaDe(jogador, 0, AGORA).cursor();
    }

    /** Os tipos dos eventos que o jogador viu depois do cursor. */
    private static List<String> eventos(Sala sala, JogadorId jogador, int desde) {
        return sala.vistaDe(jogador, desde, AGORA).passos().stream()
                .flatMap(passo -> passo.eventos().stream())
                .map(evento -> evento.getClass().getSimpleName())
                .toList();
    }

    private static Sala.Jogada jogar(Sala sala, JogadorId jogador, Acao acao) {
        return sala.jogar(jogador, acao, cursor(sala, jogador), AGORA);
    }

    // ---------- Sala de espera, nomes e lugares ----------

    @Test
    @DisplayName(
            "No 1x1, a sala espera o segundo jogador, e a partida começa quando ele entra; cada um recebe a própria"
                    + " mão")
    void entradaNoUmContraUm() {
        Sala sala = new Sala("ABCDEF", Formato.UM_CONTRA_UM, 2026, "Ana", "token-ana", AGORA);
        Sala.Vista esperando = sala.vistaDe(LUGAR1, 0, AGORA);
        assertThat(esperando.situacao()).isEqualTo(Situacao.ESPERANDO_JOGADORES);
        assertThat(esperando.passos()).isEmpty();
        assertThat(esperando.opcoes()).isEmpty();
        assertThat(esperando.lugares())
                .extracting(Sala.Lugar::nome)
                .containsExactly(Optional.of("Ana"), Optional.empty());
        assertThat(sala.jogar(LUGAR1, new JogarCarta(0), 0, AGORA)).isEqualTo(new Sala.Ilegal());

        assertThat(sala.entrar("Beto", "token-beto", AGORA)).isEqualTo(new Sala.Entrou(LUGAR2));
        assertThat(sala.entrar("Carla", "token-carla", AGORA)).isEqualTo(new Sala.Cheia());
        for (JogadorId jogador : List.of(LUGAR1, LUGAR2)) {
            Sala.Vista vista = sala.vistaDe(jogador, 0, AGORA);
            assertThat(vista.situacao()).isEqualTo(Situacao.JOGANDO);
            assertThat(vista.cursor()).isEqualTo(1);
            assertThat(vista.passos().getFirst().eventos())
                    .hasSize(2)
                    .last()
                    .isEqualTo(new CartasDistribuidas(
                            jogador, inicio(Formato.UM_CONTRA_UM, 2026).rodada().maoDe(jogador)));
        }
    }

    @Test
    @DisplayName("RG-2X2-1: no 2x2, quem entra pega o primeiro lugar vazio e pode trocar por outro; só quem criou"
            + " começa, e os lugares vazios viram bots")
    void salaDeEsperaEmDuplas() {
        Sala sala = new Sala("ABCDEF", Formato.DUPLAS, 2026, "Ana", "token-ana", AGORA);
        assertThat(sala.entrar("Beto", "token-beto", AGORA)).isEqualTo(new Sala.Entrou(LUGAR2));
        assertThat(sala.entrar("Carla", "token-carla", AGORA)).isEqualTo(new Sala.Entrou(LUGAR3));

        assertThat(sala.trocarDeLugar(LUGAR3, LUGAR4, AGORA)).isInstanceOf(Sala.Feita.class);
        assertThat(sala.trocarDeLugar(LUGAR2, LUGAR4, AGORA))
                .isEqualTo(new Sala.Recusada("Esse lugar já está ocupado."));
        assertThat(sala.trocarDeLugar(LUGAR2, new JogadorId("lugar9"), AGORA))
                .isEqualTo(new Sala.Recusada("Esse lugar não existe."));
        assertThat(sala.comecar(LUGAR2, AGORA))
                .isEqualTo(new Sala.Recusada("Só quem criou a sala pode começar a partida."));
        assertThat(sala.vistaDe(LUGAR1, 0, AGORA).situacao()).isEqualTo(Situacao.ESPERANDO_JOGADORES);

        assertThat(sala.comecar(LUGAR1, AGORA)).isInstanceOf(Sala.Feita.class);

        Sala.Vista vista = sala.vistaDe(LUGAR4, 0, AGORA);
        assertThat(vista.situacao()).isEqualTo(Situacao.JOGANDO);
        assertThat(vista.lugares())
                .containsExactly(
                        new Sala.Lugar(LUGAR1, new EquipeId("dupla1"), Optional.of("Ana"), false),
                        new Sala.Lugar(LUGAR2, new EquipeId("dupla2"), Optional.of("Beto"), false),
                        new Sala.Lugar(LUGAR3, new EquipeId("dupla1"), Optional.of("Bot 3"), true),
                        new Sala.Lugar(LUGAR4, new EquipeId("dupla2"), Optional.of("Carla"), false));
        assertThat(sala.entrar("Davi", "token-davi", AGORA)).isEqualTo(new Sala.Cheia());
        assertThat(sala.trocarDeLugar(LUGAR2, LUGAR3, AGORA)).isEqualTo(new Sala.Recusada("A partida já começou."));
    }

    @Test
    @DisplayName("Quem criou a sala continua sendo quem a começa depois de trocar de lugar")
    void anfitriaoTrocaDeLugar() {
        Sala sala = new Sala("ABCDEF", Formato.DUPLAS, 2026, "Ana", "token-ana", AGORA);

        sala.trocarDeLugar(LUGAR1, LUGAR3, AGORA);

        assertThat(sala.vistaDe(LUGAR3, 0, AGORA).anfitriao()).isEqualTo(LUGAR3);
        assertThat(sala.jogadorDoToken("token-ana")).contains(LUGAR3);
        assertThat(sala.comecar(LUGAR3, AGORA)).isInstanceOf(Sala.Feita.class);
    }

    @Test
    @DisplayName("Nomes: de 1 a 16 letras, números ou espaços, sem \"você\" nem \"Bot\" com número, e sem repetir na"
            + " sala")
    void nomes() {
        assertThat(Sala.nome("  Ana   Maria ")).contains("Ana Maria");
        assertThat(Sala.nome("João-Pedro")).contains("João-Pedro");
        assertThat(Sala.nome("")).isEmpty();
        assertThat(Sala.nome("Você")).isEmpty();
        assertThat(Sala.nome("bot 2")).isEmpty();
        assertThat(Sala.nome("x".repeat(17))).isEmpty();
        assertThat(Sala.nome("<b>Ana</b>")).isEmpty();

        Sala sala = new Sala("ABCDEF", Formato.DUPLAS, 2026, "Ana", "token-ana", AGORA);
        assertThat(sala.entrar("ANA", "token-outra", AGORA)).isEqualTo(new Sala.NomeRepetido());
    }

    @Test
    @DisplayName("Cada token identifica um lugar; um token desconhecido, nenhum")
    void tokens() {
        Sala sala = umContraUm(2026);

        assertThat(sala.jogadorDoToken("token-ana")).contains(LUGAR1);
        assertThat(sala.jogadorDoToken("token-beto")).contains(LUGAR2);
        assertThat(sala.jogadorDoToken("token")).isEmpty();
    }

    // ---------- Descarte (1x1) ----------

    @Test
    @DisplayName("RG-DESC-1 e RG-DESC-4: quem tem a carta da vez descarta antes de quem abre jogar; a jogada que quem"
            + " abre tinha preparado fica desatualizada")
    void descarteAntesDaPrimeiraJogada() {
        long seed = seedEmQueQuemNaoAbreTemACarta();
        JogadorId dono = dono(seed).orElseThrow();
        JogadorId abre = quemAbre(seed);
        int posicao =
                inicio(Formato.UM_CONTRA_UM, seed).rodada().maoDe(dono).indexOf(cartaDaVez(Formato.UM_CONTRA_UM, seed));
        Sala sala = umContraUm(seed);
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
        int posicao =
                inicio(Formato.UM_CONTRA_UM, seed).rodada().maoDe(dono).indexOf(cartaDaVez(Formato.UM_CONTRA_UM, seed));
        Sala sala = umContraUm(seed);

        assertThat(jogar(sala, abre, new JogarCarta(0))).isInstanceOf(Sala.Aceita.class);

        for (JogadorId jogador : List.of(LUGAR1, LUGAR2)) {
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
        Sala sala = umContraUm(seed);

        assertThat(jogar(sala, quemAbre(seed), new PedirAumento())).isInstanceOf(Sala.Aceita.class);

        assertThat(eventos(sala, dono, 1)).containsExactly("DescarteEncerrado", "AumentoPedido");
        assertThat(acoes(sala, dono)).containsExactly(new Aceitar(), new Correr(), new PedirAumento());
    }

    @Test
    @DisplayName("RG-DESC-4: se quem abre tem a carta da vez, pode descartá-la e depois jogar, e o outro só espera")
    void quemAbreTemACarta() {
        long seed = seedEmQueQuemAbreTemACarta();
        JogadorId abre = quemAbre(seed);
        int posicao =
                inicio(Formato.UM_CONTRA_UM, seed).rodada().maoDe(abre).indexOf(cartaDaVez(Formato.UM_CONTRA_UM, seed));
        Sala sala = umContraUm(seed);
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
            Sala sala = umContraUm(seed);
            JogadorId abre = quemAbre(seed);
            assertThat(acoes(sala, abre)).as("seed %d", seed).isEqualTo(ABRIR_A_RODADA);
            jogar(sala, abre, new JogarCarta(0));
            assertThat(eventos(sala, abre, 1)).as("seed %d", seed).containsExactly("DescarteEncerrado", "CartaJogada");
        }
        // Quem não abre não tem a carta: ou quem abre tem e joga sem descartar, ou ninguém tem.
        for (long seed : new long[] {seedEmQueQuemAbreTemACarta(), semDono}) {
            Sala sala = umContraUm(seed);
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
        Sala sala = umContraUm(seed);

        assertThat(jogar(sala, outro(abre), new JogarCarta(0))).isEqualTo(new Sala.Ilegal());
        assertThat(sala.jogar(abre, new JogarCarta(0), 0, AGORA)).isEqualTo(new Sala.Desatualizada());
        assertThat(sala.jogar(abre, new JogarCarta(0), 5, AGORA)).isEqualTo(new Sala.Desatualizada());
        assertThat(jogar(sala, abre, new JogarCarta(7))).isEqualTo(new Sala.Ilegal());
    }

    // ---------- 2x2 ----------

    @Test
    @DisplayName("RG-2X2-9 e RG-2X2-10: no 2x2, a dupla que recebe o pedido pode se ver; quando os dois querem, cada"
            + " um vê a mão do outro até responder")
    void duplaSeVeNaSala() {
        // A primeira seed em que ninguém tem a carta da vez: quem abre pede truco direto.
        long seed = seedEm(s -> dono(Formato.DUPLAS, s).isEmpty());
        ConfiguracaoDaPartida configuracao = Sala.configuracao(Formato.DUPLAS, seed);
        JogadorId abre = quemAbre(Formato.DUPLAS, seed);
        JogadorId recebe = configuracao.aDireitaDe(abre);
        JogadorId parceiro = configuracao.parceirosDe(recebe).getFirst();
        Sala sala = quatroPessoas(seed);

        jogar(sala, abre, new PedirAumento());
        assertThat(acoes(sala, parceiro)).containsExactly(new VerCartasDoParceiro());
        jogar(sala, parceiro, new VerCartasDoParceiro());
        jogar(sala, recebe, new VerCartasDoParceiro());

        Sala.Vista vista = sala.vistaDe(recebe, 0, AGORA);
        assertThat(vista.passos().getLast().visaoDe(recebe).maosDosParceiros()).containsOnlyKeys(parceiro);
        assertThat(sala.vistaDe(abre, 0, AGORA).passos().getLast().visaoDe(abre).maosDosParceiros())
                .isEmpty();
        assertThat(eventos(sala, abre, 0))
                .contains("QuisVerAsCartas", "DuplaViuAsCartas")
                .doesNotContain("CartasDoParceiroVistas");
    }

    // ---------- Bots ----------

    @Test
    @DisplayName("Bots: agem sozinhos quando alguém pergunta pela sala; quem abre a rodada, se é bot, espera 5 s antes"
            + " de fechar o descarte, e os outros bots 1,5 s depois da última mudança")
    void botsEsperam() {
        // Ana sozinha no 2x2, com três bots; ninguém tem a carta da vez, e quem abre é bot.
        long seed = seedEm(s -> dono(Formato.DUPLAS, s).isEmpty()
                && !quemAbre(Formato.DUPLAS, s).equals(LUGAR1));
        Sala sala = new Sala("ABCDEF", Formato.DUPLAS, seed, "Ana", "token-ana", AGORA);
        sala.comecar(LUGAR1, AGORA);
        int inicio = sala.vistaDe(LUGAR1, 0, AGORA).cursor();

        Instant quaseLa = AGORA.plus(Sala.ESPERA_LONGA_DO_BOT).minusMillis(1);
        assertThat(sala.vistaDe(LUGAR1, 0, quaseLa).cursor()).isEqualTo(inicio);
        Instant depois = AGORA.plus(Sala.ESPERA_LONGA_DO_BOT);
        assertThat(sala.vistaDe(LUGAR1, 0, depois).cursor()).isGreaterThan(inicio);
        assertThat(eventos(sala, LUGAR1, inicio)).startsWith("DescarteEncerrado");
    }

    @Test
    @DisplayName(
            "RG-FIM-1: uma pessoa e três bots jogam uma partida 2x2 inteira, com a pessoa fazendo sempre a primeira"
                    + " opção")
    void partidaComBots() {
        Sala sala = new Sala("ABCDEF", Formato.DUPLAS, 2026, "Ana", "token-ana", AGORA);
        sala.comecar(LUGAR1, AGORA);
        Instant agora = AGORA;
        for (int passo = 0; sala.vistaDe(LUGAR1, 0, agora).situacao() == Situacao.JOGANDO; passo++) {
            assertThat(passo).as("a partida deveria terminar").isLessThan(5_000);
            agora = agora.plus(Duration.ofSeconds(1));
            Sala.Vista vista = sala.vistaDe(LUGAR1, 0, agora);
            if (!vista.opcoes().isEmpty()) {
                sala.jogar(LUGAR1, vista.opcoes().getFirst().acao(), vista.cursor(), agora);
            }
        }

        assertThat(sala.vistaDe(LUGAR1, 0, agora).situacao()).isEqualTo(Situacao.FINALIZADA);
    }

    @Test
    @DisplayName("RG-FIM-1: jogando sempre a primeira opção, a partida 1x1 acaba; depois do fim ninguém joga, e a sala"
            + " some 30 minutos depois")
    void partidaInteiraEExpiracao() {
        Sala sala = umContraUm(2026);
        Instant agora = AGORA;
        int jogadas = 0;
        while (sala.vistaDe(LUGAR1, 0, agora).situacao() == Situacao.JOGANDO) {
            assertThat(jogadas++).isLessThan(ServidorWeb.MAXIMO_DE_JOGADAS);
            Instant momento = agora;
            JogadorId quemJoga = List.of(LUGAR1, LUGAR2).stream()
                    .filter(jogador -> !acoes(sala, jogador, momento).isEmpty())
                    .findFirst()
                    .orElseThrow();
            agora = agora.plusSeconds(1);
            assertThat(sala.jogar(quemJoga, acoes(sala, quemJoga, agora).getFirst(), cursor(sala, quemJoga), agora))
                    .isInstanceOf(Sala.Aceita.class);
        }

        assertThat(sala.vistaDe(LUGAR2, 0, agora).situacao()).isEqualTo(Situacao.FINALIZADA);
        assertThat(acoes(sala, LUGAR1, agora)).isEmpty();
        assertThat(sala.jogar(LUGAR1, new JogarCarta(0), cursor(sala, LUGAR1), agora))
                .isEqualTo(new Sala.Ilegal());
        assertThat(sala.expirada(agora.plus(Sala.DEPOIS_DO_FIM).minusSeconds(1)))
                .isFalse();
        assertThat(sala.expirada(agora.plus(Sala.DEPOIS_DO_FIM))).isTrue();
    }

    @Test
    @DisplayName("Sem atividade, a sala some depois de 2 horas")
    void expiraSemJogadas() {
        Sala sala = umContraUm(2026);

        assertThat(sala.expirada(AGORA.plus(Sala.SEM_JOGADAS).minusSeconds(1))).isFalse();
        assertThat(sala.expirada(AGORA.plus(Sala.SEM_JOGADAS))).isTrue();
    }

    // ---------- Jogar de novo e sair ----------

    /** As pessoas jogam sempre a primeira opção, e os bots no tempo deles, até a partida acabar; devolve o momento. */
    private static Instant jogarAteOFim(Sala sala, List<JogadorId> pessoas, Instant inicio) {
        Instant agora = inicio;
        for (int passo = 0; sala.vistaDe(pessoas.getFirst(), 0, agora).situacao() == Situacao.JOGANDO; passo++) {
            assertThat(passo).as("a partida deveria terminar").isLessThan(10_000);
            agora = agora.plusSeconds(1);
            for (JogadorId pessoa : pessoas) {
                Sala.Vista vista = sala.vistaDe(pessoa, 0, agora);
                if (!vista.opcoes().isEmpty()) {
                    sala.jogar(pessoa, vista.opcoes().getFirst().acao(), vista.cursor(), agora);
                    break;
                }
            }
        }
        return agora;
    }

    @Test
    @DisplayName(
            "Jogar de novo: só depois do fim; a partida nova começa quando as duas pessoas pedem, com outra seed, e"
                    + " o cursor de cada uma continua contando")
    void jogarDeNovoNoUmContraUm() {
        Sala sala = umContraUm(2026);
        assertThat(sala.jogarDeNovo(LUGAR1, AGORA)).isEqualTo(new Sala.Recusada("A partida ainda não acabou."));
        assertThat(sala.sair(LUGAR1, AGORA)).isEqualTo(new Sala.Recusada("A partida ainda não acabou."));
        Instant fim = jogarAteOFim(sala, List.of(LUGAR1, LUGAR2), AGORA);
        int cursorDaAna = sala.vistaDe(LUGAR1, 0, fim).cursor();
        int cursorDoBeto = sala.vistaDe(LUGAR2, 0, fim).cursor();

        Sala.Vista ana = ((Sala.Feita) sala.jogarDeNovo(LUGAR1, fim)).vista();
        assertThat(ana.situacao()).isEqualTo(Situacao.FINALIZADA);
        assertThat(ana.queremJogarDeNovo()).containsExactly(LUGAR1);
        assertThat(ana.passos()).isEmpty();
        assertThat(sala.vistaDe(LUGAR2, cursorDoBeto, fim).queremJogarDeNovo()).containsExactly(LUGAR1);

        Sala.Vista beto = ((Sala.Feita) sala.jogarDeNovo(LUGAR2, fim)).vista();
        assertThat(beto.situacao()).isEqualTo(Situacao.JOGANDO);
        assertThat(beto.partida()).isEqualTo(2);
        assertThat(beto.queremJogarDeNovo()).isEmpty();
        assertThat(beto.passos().getFirst().estado().numeroDaRodada()).isEqualTo(1);
        assertThat(beto.passos().getFirst().estado().placar().pontos().values()).containsOnly(0);
        assertThat(beto.cursor()).isEqualTo(cursorDoBeto + beto.passos().size());
        assertThat(sala.configuracao().seed()).isNotEqualTo(2026);
        assertThat(sala.vistaDe(LUGAR1, 0, fim).lugares())
                .extracting(lugar -> lugar.nome().orElseThrow())
                .containsExactly("Ana", "Beto");

        // Com o cursor da partida anterior, Ana recebe a partida nova desde o começo e não joga às cegas.
        Sala.Vista anaDepois = sala.vistaDe(LUGAR1, cursorDaAna, fim);
        assertThat(anaDepois.passos().getFirst().estado().numeroDaRodada()).isEqualTo(1);
        assertThat(anaDepois.cursor())
                .isEqualTo(cursorDaAna + anaDepois.passos().size());
        assertThat(sala.jogar(LUGAR1, new RecusarDescarte(), cursorDaAna, fim)).isEqualTo(new Sala.Desatualizada());

        jogarAteOFim(sala, List.of(LUGAR1, LUGAR2), fim);
    }

    @Test
    @DisplayName("Sair: depois do fim, quem sai impede a partida nova, e os outros veem quem saiu")
    void sairImpedeJogarDeNovo() {
        Sala sala = umContraUm(2026);
        Instant fim = jogarAteOFim(sala, List.of(LUGAR1, LUGAR2), AGORA);
        sala.jogarDeNovo(LUGAR1, fim);

        Sala.Vista beto = ((Sala.Feita) sala.sair(LUGAR2, fim)).vista();
        assertThat(beto.sairam()).containsExactly(LUGAR2);
        assertThat(sala.vistaDe(LUGAR1, 0, fim).sairam()).containsExactly(LUGAR2);
        assertThat(sala.jogarDeNovo(LUGAR1, fim)).isEqualTo(new Sala.Recusada("Beto saiu da sala."));
        assertThat(sala.jogarDeNovo(LUGAR2, fim)).isEqualTo(new Sala.Recusada("Você saiu da sala."));
        assertThat(sala.vistaDe(LUGAR1, 0, fim).situacao()).isEqualTo(Situacao.FINALIZADA);
    }

    @Test
    @DisplayName("Jogar de novo contra bots: os bots sempre querem, então a partida nova começa na hora, com os mesmos"
            + " bots")
    void jogarDeNovoContraBots() {
        Sala sala = new Sala("ABCDEF", Formato.DUPLAS, 2026, "Ana", "token-ana", AGORA);
        sala.comecar(LUGAR1, AGORA);
        Instant fim = jogarAteOFim(sala, List.of(LUGAR1), AGORA);

        Sala.Vista ana = ((Sala.Feita) sala.jogarDeNovo(LUGAR1, fim)).vista();
        assertThat(ana.situacao()).isEqualTo(Situacao.JOGANDO);
        assertThat(ana.partida()).isEqualTo(2);
        assertThat(ana.lugares())
                .extracting(lugar -> lugar.nome().orElseThrow(), Sala.Lugar::bot)
                .containsExactly(tuple("Ana", false), tuple("Bot 2", true), tuple("Bot 3", true), tuple("Bot 4", true));
        assertThat(sala.vistaDe(LUGAR1, 0, jogarAteOFim(sala, List.of(LUGAR1), fim))
                        .situacao())
                .isEqualTo(Situacao.FINALIZADA);
    }
}
