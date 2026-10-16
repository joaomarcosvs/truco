package io.github.joaomarcosvs.truco.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.acao.VerCartasDoParceiro;
import io.github.joaomarcosvs.truco.bots.BotAleatorio;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.partida.PedidoDeAumento;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Uma partida entre navegadores, 1x1 ou 2x2, guardada na memória do servidor. Cada lugar da mesa tem um id fixo
 * ({@code lugar1}, {@code lugar2}...) e é ocupado por uma pessoa, com nome e token, ou por um bot. A seed fica só no
 * servidor: com ela, daria para calcular as cartas de todos.
 *
 * <p>Antes da partida, a sala espera os jogadores. No 1x1, ela começa quando o segundo entra. No 2x2, quem entra pega o
 * primeiro lugar vazio e pode trocar por outro; quem criou a sala a começa, e os lugares vazios viram bots.
 *
 * <p>Descarte (RG-DESC-1, RG-DESC-2 e RG-DESC-10): quem tem a carta da vez pode descartá-la até a primeira ação de quem
 * vem depois do descarte (jogar uma carta, pedir aumento ou, na Rodada de Onze, decidir). Essa ação fecha o descarte: o
 * servidor responde "não descartar" por todos e só então a aplica. Assim ninguém espera um prazo, e a sala nunca guarda
 * uma resposta pendente. Para quem não tem a carta, "o dono não descartou" e "ninguém tinha a carta" são iguais.
 *
 * <p>Bots ({@link BotAleatorio}): agem quando alguém pergunta pela sala, um de cada vez, {@link #ESPERA_DO_BOT} depois
 * da última mudança, para dar para ver cada jogada. Esperam {@link #ESPERA_LONGA_DO_BOT} quando a jogada deles fecharia
 * o descarte ou responderia a um aumento que um parceiro humano pode querer ver (RG-2X2-9). As ações opcionais
 * (descartar, querer ver as cartas) são oferecidas a cada bot uma vez por momento da rodada, e ele pode não fazer nada.
 *
 * <p>Depois do fim, cada pessoa pode pedir para jogar de novo ou sair. Quando todas as pessoas pedem (os bots sempre
 * querem), outra partida começa na mesma sala, com os mesmos lugares e outra seed. Se alguém sai, não há revanche. O
 * cursor de cada jogador continua contando de uma partida para a outra: assim, uma jogada feita com o cursor da partida
 * anterior fica desatualizada, e quem pergunta com ele recebe a partida nova desde o começo.
 */
final class Sala {

    /** Quantos jogadores a sala tem. */
    enum Formato {
        UM_CONTRA_UM(2),
        DUPLAS(4);

        private final int lugares;

        Formato(int lugares) {
            this.lugares = lugares;
        }

        int lugares() {
            return lugares;
        }
    }

    enum Situacao {
        ESPERANDO_JOGADORES,
        JOGANDO,
        FINALIZADA
    }

    /** Sem atividade de quem joga por esse tempo, a sala some. */
    static final Duration SEM_JOGADAS = Duration.ofHours(2);

    /** Esse tempo depois do fim da partida, a sala some. */
    static final Duration DEPOIS_DO_FIM = Duration.ofMinutes(30);

    /** Quanto um bot espera, depois da última mudança, para agir. */
    static final Duration ESPERA_DO_BOT = Duration.ofMillis(1_500);

    /** Quanto um bot espera quando a ação dele fecharia o descarte ou responderia a um aumento com parceiro humano. */
    static final Duration ESPERA_LONGA_DO_BOT = Duration.ofSeconds(5);

    /** O nome que cada pessoa escolhe: letras, números, espaço, ponto, hífen e apóstrofo, de 1 a 16 caracteres. */
    private static final Pattern NOME = Pattern.compile("[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} .'-]{0,15}");

    /** Nomes que confundiriam os textos: "você" e os nomes dos bots. */
    private static final Pattern NOME_RESERVADO = Pattern.compile("(?iu)voc[eê]|bot \\d+");

    private static final MotorDeTruco MOTOR = Passo.MOTOR;

    /** Uma ação que o jogador pode fazer agora, com a visão em que o texto dela é escrito. */
    record Opcao(Acao acao, VisaoDoJogador visao) {}

    /** Um lugar da mesa, como todos o veem: o id, a equipe, o nome de quem está nele (vazio se ninguém) e se é bot. */
    record Lugar(JogadorId id, EquipeId equipe, Optional<String> nome, boolean bot) {}

    /**
     * O que um jogador vê da sala: os lugares, o número da partida (1 na primeira, 2 depois de jogarem de novo...), quem
     * já pediu para jogar de novo e quem saiu, os passos depois do cursor que ele mandou e o que pode fazer agora.
     */
    record Vista(
            Situacao situacao,
            Formato formato,
            JogadorId voce,
            JogadorId anfitriao,
            List<Lugar> lugares,
            int partida,
            List<JogadorId> queremJogarDeNovo,
            List<JogadorId> sairam,
            int cursor,
            List<Passo> passos,
            List<Opcao> opcoes) {

        Vista {
            lugares = List.copyOf(lugares);
            queremJogarDeNovo = List.copyOf(queremJogarDeNovo);
            sairam = List.copyOf(sairam);
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

    /** O resultado de entrar na sala. */
    sealed interface Entrada {}

    /** A pessoa entrou e sentou neste lugar. */
    record Entrou(JogadorId lugar) implements Entrada {}

    /** Não há lugar vazio, ou a partida já começou. */
    record Cheia() implements Entrada {}

    /** Já há alguém com esse nome na sala. */
    record NomeRepetido() implements Entrada {}

    /** O resultado de trocar de lugar, começar a partida, pedir para jogar de novo ou sair. */
    sealed interface Mudanca {}

    /** Feito; a vista é a de quem pediu. */
    record Feita(Vista vista) implements Mudanca {}

    /** Não deu, pelo motivo dado. */
    record Recusada(String motivo) implements Mudanca {}

    /** Quem ocupa um lugar. */
    private sealed interface Ocupante {
        String nome();
    }

    private record Pessoa(String nome, String token) implements Ocupante {}

    private record Robo(String nome, BotAleatorio estrategia) implements Ocupante {}

    /** Um bot consultado sobre as ações opcionais num momento da rodada. */
    private record Consulta(JogadorId bot, int rodada, FaseDaRodada fase, Optional<PedidoDeAumento> pedido) {}

    /** O próximo bot a agir, se a ação é opcional e quanto ele espera depois da última mudança. */
    private record VezDoBot(JogadorId bot, boolean opcional, Duration espera) {}

    private final String codigo;
    private final Formato formato;
    private final Map<JogadorId, Ocupante> ocupantes = new HashMap<>();
    /** Os passos de cada jogador na partida atual. */
    private final Map<JogadorId, List<Passo>> historicos = new HashMap<>();
    /** Quantos passos cada jogador viu nas partidas anteriores da sala: o cursor continua dali. */
    private final Map<JogadorId, Integer> passosAnteriores = new HashMap<>();

    private final Set<Consulta> consultas = new HashSet<>();
    private final Set<JogadorId> queremJogarDeNovo = new HashSet<>();
    private final Set<JogadorId> sairam = new HashSet<>();
    private long seed;
    private ConfiguracaoDaPartida configuracao;
    private int partida = 1;
    private JogadorId anfitriao;
    private Optional<EstadoDaPartida> estado = Optional.empty();
    private Instant ultimaAtividade;
    private Instant ultimaMudanca;

    /** A sala nova, com quem a criou sentado no primeiro lugar. */
    Sala(String codigo, Formato formato, long seed, String nomeDoAnfitriao, String tokenDoAnfitriao, Instant agora) {
        this.codigo = codigo;
        this.formato = formato;
        this.seed = seed;
        this.configuracao = configuracao(formato, seed);
        this.anfitriao = configuracao.jogadoresNaOrdemDaMesa().getFirst();
        this.ocupantes.put(anfitriao, new Pessoa(nomeDoAnfitriao, tokenDoAnfitriao));
        this.ultimaAtividade = agora;
        this.ultimaMudanca = agora;
    }

    /**
     * A mesa do formato, com os lugares na ordem da mesa: no 1x1, {@code lugar1} contra {@code lugar2}; no 2x2,
     * {@code lugar1} e {@code lugar3} (dupla 1) contra {@code lugar2} e {@code lugar4} (dupla 2), que se alternam
     * (RG-2X2-1).
     */
    static ConfiguracaoDaPartida configuracao(Formato formato, long seed) {
        List<JogadorId> lugares = IntStream.rangeClosed(1, formato.lugares())
                .mapToObj(numero -> new JogadorId("lugar" + numero))
                .toList();
        List<Equipe> equipes = formato == Formato.UM_CONTRA_UM
                ? List.of(Equipe.individual(lugares.get(0)), Equipe.individual(lugares.get(1)))
                : List.of(
                        new Equipe(new EquipeId("dupla1"), List.of(lugares.get(0), lugares.get(2))),
                        new Equipe(new EquipeId("dupla2"), List.of(lugares.get(1), lugares.get(3))));
        return new ConfiguracaoDaPartida(equipes, new TrucoPaulista(), seed);
    }

    /** O nome como a pessoa digitou, sem espaços sobrando; vazio se não serve como nome na sala. */
    static Optional<String> nome(String digitado) {
        String nome = digitado.strip().replaceAll("\\s+", " ");
        boolean valido =
                NOME.matcher(nome).matches() && !NOME_RESERVADO.matcher(nome).matches();
        return valido ? Optional.of(nome) : Optional.empty();
    }

    String codigo() {
        return codigo;
    }

    /** A configuração da partida atual, para narrá-la. Leva a seed, que não pode sair do servidor. */
    synchronized ConfiguracaoDaPartida configuracao() {
        return configuracao;
    }

    /** A pessoa entra com o nome e o token dados, no primeiro lugar vazio. No 1x1, a partida começa quando ela entra. */
    synchronized Entrada entrar(String nome, String token, Instant agora) {
        if (situacao() != Situacao.ESPERANDO_JOGADORES) {
            return new Cheia();
        }
        if (ocupantes.values().stream().anyMatch(ocupante -> mesmoNome(ocupante.nome(), nome))) {
            return new NomeRepetido();
        }
        Optional<JogadorId> vazio = configuracao.jogadoresNaOrdemDaMesa().stream()
                .filter(lugar -> !ocupantes.containsKey(lugar))
                .findFirst();
        if (vazio.isEmpty()) {
            return new Cheia();
        }
        ocupantes.put(vazio.get(), new Pessoa(nome, token));
        ultimaAtividade = agora;
        if (formato == Formato.UM_CONTRA_UM && ocupantes.size() == formato.lugares()) {
            iniciar(agora);
        }
        return new Entrou(vazio.get());
    }

    /** Antes da partida, a pessoa troca para um lugar vazio. */
    synchronized Mudanca trocarDeLugar(JogadorId jogador, JogadorId lugar, Instant agora) {
        if (situacao() != Situacao.ESPERANDO_JOGADORES) {
            return new Recusada("A partida já começou.");
        }
        if (!configuracao.participa(lugar)) {
            return new Recusada("Esse lugar não existe.");
        }
        if (ocupantes.containsKey(lugar)) {
            return new Recusada("Esse lugar já está ocupado.");
        }
        ocupantes.put(lugar, ocupantes.remove(jogador));
        if (jogador.equals(anfitriao)) {
            anfitriao = lugar;
        }
        ultimaAtividade = agora;
        return new Feita(vistaDe(lugar, 0, agora));
    }

    /** Quem criou a sala começa a partida; os lugares vazios viram bots. */
    synchronized Mudanca comecar(JogadorId jogador, Instant agora) {
        if (situacao() != Situacao.ESPERANDO_JOGADORES) {
            return new Recusada("A partida já começou.");
        }
        if (!jogador.equals(anfitriao)) {
            return new Recusada("Só quem criou a sala pode começar a partida.");
        }
        iniciar(agora);
        return new Feita(vistaDe(jogador, 0, agora));
    }

    /**
     * Depois do fim, a pessoa pede para jogar de novo. Quando todas as pessoas da sala pediram, outra partida começa,
     * com os mesmos lugares e outra seed; a vista traz a partida nova desde o começo.
     */
    synchronized Mudanca jogarDeNovo(JogadorId jogador, Instant agora) {
        if (situacao() != Situacao.FINALIZADA) {
            return new Recusada("A partida ainda não acabou.");
        }
        if (sairam.contains(jogador)) {
            return new Recusada("Você saiu da sala.");
        }
        Optional<String> quemSaiu = configuracao.jogadoresNaOrdemDaMesa().stream()
                .filter(sairam::contains)
                .map(lugar -> ocupantes.get(lugar).nome())
                .findFirst();
        if (quemSaiu.isPresent()) {
            return new Recusada(quemSaiu.get() + " saiu da sala.");
        }
        int visto = cursorDe(jogador);
        queremJogarDeNovo.add(jogador);
        ultimaAtividade = agora;
        boolean todos = ocupantes.entrySet().stream()
                .filter(entrada -> entrada.getValue() instanceof Pessoa)
                .allMatch(entrada -> queremJogarDeNovo.contains(entrada.getKey()));
        if (todos) {
            recomecar(agora);
        }
        return new Feita(vistaDe(jogador, visto, agora));
    }

    /** Depois do fim, a pessoa sai da sala: ninguém mais joga de novo nela. */
    synchronized Mudanca sair(JogadorId jogador, Instant agora) {
        if (situacao() != Situacao.FINALIZADA) {
            return new Recusada("A partida ainda não acabou.");
        }
        sairam.add(jogador);
        queremJogarDeNovo.remove(jogador);
        ultimaAtividade = agora;
        return new Feita(vistaDe(jogador, cursorDe(jogador), agora));
    }

    /** O jogador dono do token, comparado em tempo constante. */
    synchronized Optional<JogadorId> jogadorDoToken(String token) {
        byte[] recebido = token.getBytes(UTF_8);
        return ocupantes.entrySet().stream()
                .filter(entrada -> entrada.getValue() instanceof Pessoa pessoa
                        && MessageDigest.isEqual(pessoa.token().getBytes(UTF_8), recebido))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * A sala como o jogador a vê: os lugares, os passos que ele ainda não viu, a partir do cursor {@code desde}, e o que
     * ele pode fazer agora. Antes, os bots fazem as jogadas cuja hora já chegou.
     */
    synchronized Vista vistaDe(JogadorId jogador, int desde, Instant agora) {
        avancarBots(agora);
        List<Passo> historico = historicos.getOrDefault(jogador, List.of());
        int anteriores = passosAnteriores.getOrDefault(jogador, 0);
        int inicio = Math.clamp((long) desde - anteriores, 0, historico.size());
        List<JogadorId> mesa = configuracao.jogadoresNaOrdemDaMesa();
        return new Vista(
                situacao(),
                formato,
                jogador,
                anfitriao,
                lugares(),
                partida,
                mesa.stream().filter(queremJogarDeNovo::contains).toList(),
                mesa.stream().filter(sairam::contains).toList(),
                anteriores + historico.size(),
                historico.subList(inicio, historico.size()),
                opcoesDe(jogador));
    }

    /**
     * Aplica a ação do jogador. O {@code cursor} é o que ele tinha visto: se a partida andou depois disso (o adversário
     * descartou, ou um bot jogou, por exemplo), a jogada é recusada em vez de cair num lugar que ele não viu.
     */
    synchronized Jogada jogar(JogadorId jogador, Acao acao, int cursor, Instant agora) {
        avancarBots(agora);
        if (situacao() != Situacao.JOGANDO) {
            return new Ilegal();
        }
        if (cursor != cursorDe(jogador)) {
            return new Desatualizada();
        }
        if (opcoesDe(jogador).stream().noneMatch(opcao -> opcao.acao().equals(acao))) {
            return new Ilegal();
        }
        aplicarJogada(jogador, acao);
        ultimaAtividade = agora;
        ultimaMudanca = agora;
        return new Aceita(vistaDe(jogador, cursor, agora));
    }

    /** Se a sala já pode sumir: muito tempo sem atividade de quem joga, ou a partida acabou há um tempo. */
    synchronized boolean expirada(Instant agora) {
        Duration limite = situacao() == Situacao.FINALIZADA ? DEPOIS_DO_FIM : SEM_JOGADAS;
        return !agora.isBefore(ultimaAtividade.plus(limite));
    }

    private Situacao situacao() {
        if (estado.isEmpty()) {
            return Situacao.ESPERANDO_JOGADORES;
        }
        return estado.get().rodada().fase() instanceof FaseDaRodada.PartidaFinalizada
                ? Situacao.FINALIZADA
                : Situacao.JOGANDO;
    }

    private List<Lugar> lugares() {
        return configuracao.jogadoresNaOrdemDaMesa().stream()
                .map(lugar -> new Lugar(
                        lugar,
                        configuracao.equipeDe(lugar),
                        Optional.ofNullable(ocupantes.get(lugar)).map(Ocupante::nome),
                        ocupantes.get(lugar) instanceof Robo))
                .toList();
    }

    /** O cursor do jogador: todos os passos que ele já pôde ver na sala, desta partida e das anteriores. */
    private int cursorDe(JogadorId jogador) {
        return passosAnteriores.getOrDefault(jogador, 0)
                + historicos.getOrDefault(jogador, List.of()).size();
    }

    /**
     * Os lugares vazios viram bots, e a partida começa, com a 1ª rodada já distribuída. Cada bot joga com uma estratégia
     * nova, tirada da seed da partida.
     */
    private void iniciar(Instant agora) {
        List<JogadorId> mesa = configuracao.jogadoresNaOrdemDaMesa();
        for (int i = 0; i < mesa.size(); i++) {
            Ocupante ocupante = ocupantes.get(mesa.get(i));
            if (!(ocupante instanceof Pessoa)) {
                String nome = ocupante == null ? "Bot " + (i + 1) : ocupante.nome();
                ocupantes.put(mesa.get(i), new Robo(nome, new BotAleatorio(seed + i)));
            }
        }
        EstadoDaPartida inicio = MOTOR.novaPartida(configuracao);
        estado = Optional.of(inicio);
        for (JogadorId jogador : mesa) {
            historicos.put(jogador, new ArrayList<>(List.of(Passo.inicio(jogador, inicio))));
        }
        ultimaAtividade = agora;
        ultimaMudanca = agora;
    }

    /** Outra partida na mesma sala: outra seed, os mesmos lugares, e o cursor de cada um continua de onde estava. */
    private void recomecar(Instant agora) {
        seed = new SplittableRandom(seed).nextLong();
        configuracao = configuracao(formato, seed);
        historicos.forEach((jogador, historico) -> passosAnteriores.merge(jogador, historico.size(), Integer::sum));
        partida++;
        queremJogarDeNovo.clear();
        consultas.clear();
        iniciar(agora);
    }

    /**
     * No descarte, o dono da carta da vez pode descartá-la, e quem vem depois do descarte já pode agir: as ações dele
     * são as do estado em que todos responderam "não descartar". Fora do descarte, são as ações legais do motor.
     */
    private List<Opcao> opcoesDe(JogadorId jogador) {
        if (situacao() != Situacao.JOGANDO) {
            return List.of();
        }
        EstadoDaPartida atual = estado.orElseThrow();
        VisaoDoJogador visao = MOTOR.visaoDe(atual, jogador);
        if (!(atual.rodada().fase() instanceof FaseDaRodada.AguardandoDescarte)) {
            return MOTOR.acoesLegais(atual, jogador).stream()
                    .map(acao -> new Opcao(acao, visao))
                    .toList();
        }
        List<Opcao> opcoes = new ArrayList<>();
        MOTOR.acoesLegais(atual, jogador).stream()
                .filter(Descartar.class::isInstance)
                .forEach(acao -> opcoes.add(new Opcao(acao, visao)));
        EstadoDaPartida semDescarte = passarNoDescarte(atual, new ArrayList<>());
        VisaoDoJogador visaoSemDescarte = MOTOR.visaoDe(semDescarte, jogador);
        MOTOR.acoesLegais(semDescarte, jogador).forEach(acao -> opcoes.add(new Opcao(acao, visaoSemDescarte)));
        return List.copyOf(opcoes);
    }

    /** Aplica uma ação que está entre as opções do jogador e registra os passos de todos. */
    private void aplicarJogada(JogadorId jogador, Acao acao) {
        EstadoDaPartida atual = estado.orElseThrow();
        List<Aplicada> aplicadas = new ArrayList<>();
        EstadoDaPartida depois;
        if (!(atual.rodada().fase() instanceof FaseDaRodada.AguardandoDescarte)) {
            depois = aplicar(atual, jogador, acao, aplicadas);
        } else if (acao instanceof Descartar) {
            // RG-DESC-4: o dono descarta; os outros não têm a carta e passam, e o passo se resolve.
            depois = passarNoDescarte(aplicar(atual, jogador, acao, aplicadas), aplicadas);
        } else {
            // RG-DESC-1 e RG-DESC-2: a primeira ação depois do descarte o fecha.
            depois = aplicar(passarNoDescarte(atual, aplicadas), jogador, acao, aplicadas);
        }
        registrar(aplicadas, depois);
    }

    /** Os bots fazem, em ordem, as jogadas cuja hora já chegou. */
    private void avancarBots(Instant agora) {
        while (situacao() == Situacao.JOGANDO) {
            Optional<VezDoBot> vez = vezDoBot();
            if (vez.isEmpty()) {
                return;
            }
            Instant quando = ultimaMudanca.plus(vez.get().espera());
            if (agora.isBefore(quando)) {
                return;
            }
            JogadorId bot = vez.get().bot();
            BotAleatorio estrategia = ((Robo) ocupantes.get(bot)).estrategia();
            List<Acao> acoes = opcoesDe(bot).stream().map(Opcao::acao).toList();
            VisaoDoJogador visao = MOTOR.visaoDe(estado.orElseThrow(), bot);
            Optional<Acao> escolha;
            if (vez.get().opcional()) {
                consultas.add(consulta(bot));
                escolha = estrategia.decidirOpcional(visao, acoes);
            } else {
                escolha = Optional.of(estrategia.decidir(visao, acoes));
            }
            escolha.ifPresent(acao -> aplicarJogada(bot, acao));
            ultimaMudanca = quando;
        }
    }

    /**
     * O próximo bot a agir: primeiro um que só tenha ações opcionais e ainda não tenha sido consultado neste momento da
     * rodada; senão, o bot que tem a vez, se quem tem a vez for um bot.
     */
    private Optional<VezDoBot> vezDoBot() {
        List<JogadorId> mesa = configuracao.jogadoresNaOrdemDaMesa();
        for (JogadorId jogador : mesa) {
            if (ocupantes.get(jogador) instanceof Robo) {
                List<Opcao> opcoes = opcoesDe(jogador);
                boolean soOpcionais = !opcoes.isEmpty() && opcoes.stream().allMatch(opcao -> opcional(opcao.acao()));
                if (soOpcionais && !consultas.contains(consulta(jogador))) {
                    return Optional.of(new VezDoBot(jogador, true, ESPERA_DO_BOT));
                }
            }
        }
        return mesa.stream()
                .filter(jogador -> opcoesDe(jogador).stream().anyMatch(opcao -> !opcional(opcao.acao())))
                .findFirst()
                .filter(jogador -> ocupantes.get(jogador) instanceof Robo)
                .map(bot -> new VezDoBot(bot, false, espera(bot)));
    }

    /** O bot espera mais quando a jogada dele fecharia o descarte ou responderia a um aumento com parceiro humano. */
    private Duration espera(JogadorId bot) {
        FaseDaRodada fase = estado.orElseThrow().rodada().fase();
        boolean fechaODescarte = fase instanceof FaseDaRodada.AguardandoDescarte;
        boolean parceiroPodeQuererVer = fase instanceof FaseDaRodada.AguardandoRespostaDeAumento
                && configuracao.parceirosDe(bot).stream()
                        .anyMatch(parceiro -> ocupantes.get(parceiro) instanceof Pessoa);
        return fechaODescarte || parceiroPodeQuererVer ? ESPERA_LONGA_DO_BOT : ESPERA_DO_BOT;
    }

    private Consulta consulta(JogadorId bot) {
        EstadoDaPartida atual = estado.orElseThrow();
        return new Consulta(
                bot,
                atual.numeroDaRodada(),
                atual.rodada().fase(),
                atual.rodada().aposta().pedidoPendente());
    }

    /** Ações que o jogador pode deixar de fazer, sem que a partida espere por ele. */
    private static boolean opcional(Acao acao) {
        return acao instanceof Descartar || acao instanceof VerCartasDoParceiro;
    }

    private static boolean mesmoNome(String um, String outro) {
        return um.toLowerCase(Locale.ROOT).equals(outro.toLowerCase(Locale.ROOT));
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
        estado = Optional.of(depois);
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
