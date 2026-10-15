package io.github.joaomarcosvs.truco.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Uma partida entre dois navegadores, guardada na memória do servidor. Quem cria a sala é o anfitrião; quem entra com o
 * código é o convidado. A seed fica só no servidor: com ela, daria para calcular as cartas dos dois.
 *
 * <p>Descarte (RG-DESC-1, RG-DESC-2 e RG-DESC-10): quem tem a carta da vez pode descartá-la até a primeira ação de quem
 * vem depois do descarte (jogar uma carta, pedir aumento ou, na Rodada de Onze, decidir). Essa ação fecha o descarte: o
 * servidor responde "não descartar" por todos e só então a aplica. Assim ninguém espera um prazo, e a sala nunca guarda
 * uma resposta pendente. Para quem não tem a carta, "o dono não descartou" e "ninguém tinha a carta" são iguais.
 */
final class Sala {

    static final JogadorId ANFITRIAO = new JogadorId("anfitriao");
    static final JogadorId CONVIDADO = new JogadorId("convidado");

    /** Sem jogadas por esse tempo, a sala some. */
    static final Duration SEM_JOGADAS = Duration.ofHours(2);

    /** Esse tempo depois do fim da partida, a sala some. */
    static final Duration DEPOIS_DO_FIM = Duration.ofMinutes(30);

    private static final MotorDeTruco MOTOR = Passo.MOTOR;

    enum Situacao {
        ESPERANDO_ADVERSARIO,
        JOGANDO,
        FINALIZADA
    }

    /** Uma ação que o jogador pode fazer agora, com a visão em que o texto dela é escrito. */
    record Opcao(Acao acao, VisaoDoJogador visao) {}

    /** O que um jogador vê da sala: os passos depois do cursor que ele mandou e o que pode fazer agora. */
    record Vista(Situacao situacao, JogadorId voce, int cursor, List<Passo> passos, List<Opcao> opcoes) {

        Vista {
            passos = List.copyOf(passos);
            opcoes = List.copyOf(opcoes);
        }
    }

    /** O resultado de uma jogada. */
    sealed interface Jogada {}

    /** A jogada foi aplicada; a vista vai do cursor mandado até agora. */
    record Aceita(Vista vista) implements Jogada {}

    /** A partida andou depois do cursor mandado: o jogador precisa ver o que mudou antes de jogar. */
    record Desatualizada() implements Jogada {}

    /** A jogada não vale agora (fora da vez, partida que não começou ou já acabou, ação que não está entre as opções). */
    record Ilegal() implements Jogada {}

    private final String codigo;
    private final ConfiguracaoDaPartida configuracao;
    private final Map<JogadorId, String> tokens = new HashMap<>();
    private final Map<JogadorId, List<Passo>> historicos = new HashMap<>();
    private EstadoDaPartida estado;
    private Instant ultimaAtividade;

    Sala(String codigo, long seed, String tokenDoAnfitriao, Instant agora) {
        this.codigo = codigo;
        this.configuracao = configuracao(seed);
        this.estado = MOTOR.novaPartida(configuracao);
        this.tokens.put(ANFITRIAO, tokenDoAnfitriao);
        this.ultimaAtividade = agora;
    }

    /** O anfitrião contra o convidado, no Truco Paulista. */
    static ConfiguracaoDaPartida configuracao(long seed) {
        return new ConfiguracaoDaPartida(
                List.of(Equipe.individual(ANFITRIAO), Equipe.individual(CONVIDADO)), new TrucoPaulista(), seed);
    }

    String codigo() {
        return codigo;
    }

    /** A configuração, para narrar a partida. Leva a seed, que não pode sair do servidor. */
    ConfiguracaoDaPartida configuracao() {
        return configuracao;
    }

    /** O convidado entra com o token dado, e a partida começa. Falso se a sala já tem os dois jogadores. */
    synchronized boolean entrar(String token, Instant agora) {
        if (tokens.containsKey(CONVIDADO)) {
            return false;
        }
        tokens.put(CONVIDADO, token);
        for (JogadorId jogador : configuracao.jogadoresNaOrdemDaMesa()) {
            historicos.put(jogador, new ArrayList<>(List.of(Passo.inicio(jogador, estado))));
        }
        ultimaAtividade = agora;
        return true;
    }

    /** O jogador dono do token, comparado em tempo constante. */
    synchronized Optional<JogadorId> jogadorDoToken(String token) {
        byte[] recebido = token.getBytes(UTF_8);
        return tokens.entrySet().stream()
                .filter(entrada -> MessageDigest.isEqual(entrada.getValue().getBytes(UTF_8), recebido))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Os passos que o jogador ainda não viu, a partir do cursor {@code desde}, e o que ele pode fazer agora. */
    synchronized Vista vistaDe(JogadorId jogador, int desde) {
        List<Passo> historico = historicos.getOrDefault(jogador, List.of());
        int inicio = Math.clamp(desde, 0, historico.size());
        return new Vista(
                situacao(), jogador, historico.size(), historico.subList(inicio, historico.size()), opcoesDe(jogador));
    }

    /**
     * Aplica a ação do jogador. O {@code cursor} é o que ele tinha visto: se a partida andou depois disso (o adversário
     * descartou, por exemplo), a jogada é recusada em vez de cair num lugar que ele não viu.
     */
    synchronized Jogada jogar(JogadorId jogador, Acao acao, int cursor, Instant agora) {
        if (situacao() != Situacao.JOGANDO) {
            return new Ilegal();
        }
        if (cursor != historicos.get(jogador).size()) {
            return new Desatualizada();
        }
        if (opcoesDe(jogador).stream().noneMatch(opcao -> opcao.acao().equals(acao))) {
            return new Ilegal();
        }
        List<Aplicada> aplicadas = new ArrayList<>();
        EstadoDaPartida depois;
        if (!(estado.rodada().fase() instanceof FaseDaRodada.AguardandoDescarte)) {
            depois = aplicar(estado, jogador, acao, aplicadas);
        } else if (acao instanceof Descartar) {
            // RG-DESC-4: o dono descarta; os outros não têm a carta e passam, e o passo se resolve.
            depois = passarNoDescarte(aplicar(estado, jogador, acao, aplicadas), aplicadas);
        } else {
            // RG-DESC-1 e RG-DESC-2: a primeira ação depois do descarte o fecha.
            depois = aplicar(passarNoDescarte(estado, aplicadas), jogador, acao, aplicadas);
        }
        registrar(aplicadas, depois);
        ultimaAtividade = agora;
        return new Aceita(vistaDe(jogador, cursor));
    }

    /** Se a sala já pode sumir: muito tempo sem jogadas, ou a partida acabou há um tempo. */
    synchronized boolean expirada(Instant agora) {
        Duration limite = situacao() == Situacao.FINALIZADA ? DEPOIS_DO_FIM : SEM_JOGADAS;
        return !agora.isBefore(ultimaAtividade.plus(limite));
    }

    private Situacao situacao() {
        if (!tokens.containsKey(CONVIDADO)) {
            return Situacao.ESPERANDO_ADVERSARIO;
        }
        return estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada
                ? Situacao.FINALIZADA
                : Situacao.JOGANDO;
    }

    /**
     * No descarte, o dono da carta da vez pode descartá-la, e quem vem depois do descarte já pode agir: as ações dele
     * são as do estado em que todos responderam "não descartar". Fora do descarte, são as ações legais do motor.
     */
    private List<Opcao> opcoesDe(JogadorId jogador) {
        if (situacao() != Situacao.JOGANDO) {
            return List.of();
        }
        VisaoDoJogador visao = MOTOR.visaoDe(estado, jogador);
        if (!(estado.rodada().fase() instanceof FaseDaRodada.AguardandoDescarte)) {
            return MOTOR.acoesLegais(estado, jogador).stream()
                    .map(acao -> new Opcao(acao, visao))
                    .toList();
        }
        List<Opcao> opcoes = new ArrayList<>();
        MOTOR.acoesLegais(estado, jogador).stream()
                .filter(Descartar.class::isInstance)
                .forEach(acao -> opcoes.add(new Opcao(acao, visao)));
        EstadoDaPartida semDescarte = passarNoDescarte(estado, new ArrayList<>());
        VisaoDoJogador visaoSemDescarte = MOTOR.visaoDe(semDescarte, jogador);
        MOTOR.acoesLegais(semDescarte, jogador).forEach(acao -> opcoes.add(new Opcao(acao, visaoSemDescarte)));
        return List.copyOf(opcoes);
    }

    /**
     * Responde "não descartar" por quem ainda não respondeu à carta da vez, o que resolve o passo do descarte. Só os que
     * faltavam antes de começar: se alguém descartou, a carta da vez seguinte é um passo novo, que fica em aberto.
     */
    private static EstadoDaPartida passarNoDescarte(EstadoDaPartida antes, List<Aplicada> aplicadas) {
        RecusarDescarte passar = new RecusarDescarte();
        List<JogadorId> faltam = antes.configuracao().jogadoresNaOrdemDaMesa().stream()
                .filter(jogador -> MOTOR.acoesLegais(antes, jogador).contains(passar))
                .toList();
        EstadoDaPartida depois = antes;
        for (JogadorId jogador : faltam) {
            depois = aplicar(depois, jogador, passar, aplicadas);
        }
        return depois;
    }

    private static EstadoDaPartida aplicar(
            EstadoDaPartida antes, JogadorId jogador, Acao acao, List<Aplicada> aplicadas) {
        if (!(MOTOR.aplicar(antes, jogador, acao) instanceof Aplicada aplicada)) {
            throw new IllegalStateException("O motor rejeitou uma ação legal: " + acao);
        }
        aplicadas.add(aplicada);
        return aplicada.novoEstado();
    }

    /**
     * Cada jogador ganha um passo por ação aplicada em que viu algum evento, e pelo menos um por jogada: assim o cursor
     * de todos anda a cada mudança da partida.
     */
    private void registrar(List<Aplicada> aplicadas, EstadoDaPartida depois) {
        estado = depois;
        for (JogadorId jogador : configuracao.jogadoresNaOrdemDaMesa()) {
            List<Passo> historico = historicos.get(jogador);
            int antes = historico.size();
            for (Aplicada aplicada : aplicadas) {
                Passo passo = Passo.visto(jogador, aplicada.eventos(), aplicada.novoEstado());
                if (!passo.eventos().isEmpty()) {
                    historico.add(passo);
                }
            }
            if (historico.size() == antes) {
                historico.add(new Passo(List.of(), depois));
            }
        }
    }
}
