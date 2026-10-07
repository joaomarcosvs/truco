package io.github.joaomarcosvs.truco.partida;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.JogarEncoberta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.AumentoAceito;
import io.github.joaomarcosvs.truco.evento.AumentoPedido;
import io.github.joaomarcosvs.truco.evento.CartaDescartada;
import io.github.joaomarcosvs.truco.evento.CartaEncobertaJogada;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.CartaRecebidaPorDescarte;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.DescarteEncerrado;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.JogadorCorreu;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.RodadaAnulada;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoDescarte;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoJogada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoRespostaDeAumento;
import io.github.joaomarcosvs.truco.regras.EscadaDeApostas;
import io.github.joaomarcosvs.truco.regras.RegrasDeVaza;
import io.github.joaomarcosvs.truco.regras.VarianteDeRegras;
import io.github.joaomarcosvs.truco.visao.JogadaVisivel;
import io.github.joaomarcosvs.truco.visao.VazaVisivel;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        if (!configuracao.participa(jogador)) {
            return List.of();
        }
        EscadaDeApostas escada = configuracao.variante().escadaDeApostas();
        Rodada rodada = estado.rodada();
        List<Acao> acoes = new ArrayList<>();
        switch (rodada.fase()) {
            case AguardandoDescarte aguardando
            when !rodada.descarte().jaResponderam().contains(jogador) -> {
                // RG-DESC-4 e RG-DESC-10: todos respondem, mas só o dono da carta da vez pode descartá-la.
                int posicao = rodada.maoDe(jogador).indexOf(aguardando.cartaDaVez());
                if (posicao >= 0 && !rodada.baralhoRestante().isEmpty()) {
                    acoes.add(new Descartar(posicao));
                }
                acoes.add(new RecusarDescarte());
            }
            case AguardandoJogada aguardando
            when aguardando.jogador().equals(jogador) -> {
                int cartasNaMao = rodada.maoDe(jogador).size();
                for (int indiceNaMao = 0; indiceNaMao < cartasNaMao; indiceNaMao++) {
                    acoes.add(new JogarCarta(indiceNaMao));
                }
                // RG-ENC-1: carta encoberta só a partir da 2ª vaza.
                if (configuracao.variante().regrasDeVaza().permiteEncoberta(rodada.numeroDaVazaAtual())) {
                    for (int indiceNaMao = 0; indiceNaMao < cartasNaMao; indiceNaMao++) {
                        acoes.add(new JogarEncoberta(indiceNaMao));
                    }
                }
                // RG-AUM-2 e RG-AUM-5: na sua vez, antes de jogar, quem tem o direito pode pedir aumento.
                if (escada.podeAumentar(rodada.aposta(), configuracao.equipeDe(jogador))) {
                    acoes.add(new PedirAumento());
                }
                acoes.add(new Correr()); // RG-AUM-6
            }
            case AguardandoRespostaDeAumento resposta
            when resposta.respondedor().equals(jogador) -> {
                acoes.add(new Aceitar());
                acoes.add(new Correr());
                // RG-AUM-3: pedir mais é aceitar o pedido e já propor o nível seguinte, se houver.
                EquipeId equipe = configuracao.equipeDe(jogador);
                if (escada.podeAumentar(aceita(rodada.aposta(), equipe), equipe)) {
                    acoes.add(new PedirAumento());
                }
            }
            case AguardandoDescarte jaRespondeu -> {}
            case AguardandoJogada outroJogador -> {}
            case AguardandoRespostaDeAumento outroJogador -> {}
        }
        return List.copyOf(acoes);
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
            case Descartar descartar -> responderDescarte(estado, jogador, true, eventos);
            case RecusarDescarte recusa -> responderDescarte(estado, jogador, false, eventos);
            case JogarCarta(int indiceNaMao) -> jogarCarta(estado, jogador, indiceNaMao, false, eventos);
            case JogarEncoberta(int indiceNaMao) -> jogarCarta(estado, jogador, indiceNaMao, true, eventos);
            case PedirAumento pedido -> pedirAumento(estado, jogador, eventos);
            case Aceitar aceite -> aceitarAumento(estado, jogador, eventos);
            case Correr corrida -> correr(estado, jogador, eventos);
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
        Optional<Carta> cartaDaVezNoDescarte = Optional.empty();
        Optional<JogadorId> vezDe = Optional.empty();
        switch (rodada.fase()) {
            // RG-DESC-10: no descarte todos decidem, e não se revela quem tem a carta da vez.
            case AguardandoDescarte(Carta cartaDaVez) -> cartaDaVezNoDescarte = Optional.of(cartaDaVez);
            case AguardandoJogada(JogadorId daVez) -> vezDe = Optional.of(daVez);
            case AguardandoRespostaDeAumento resposta -> vezDe = Optional.of(resposta.respondedor());
        }
        return new VisaoDoJogador(
                jogador,
                mesa,
                configuracao.equipes(),
                estado.placar(),
                estado.numeroDaRodada(),
                estado.carteador(),
                rodada.aposta(),
                rodada.vira(),
                rodada.descarte().descartadas(),
                cartaDaVezNoDescarte,
                rodada.maoDe(jogador),
                cartasNaMao,
                rodada.vazas().stream()
                        .map(vaza -> new VazaVisivel(visiveis(vaza.jogadas(), jogador), vaza.resultado()))
                        .toList(),
                visiveis(rodada.vazaAtual(), jogador),
                vezDe);
    }

    /** As jogadas como o observador as vê: a carta encoberta só aparece para quem a jogou (RG-ENC-3). */
    private static List<JogadaVisivel> visiveis(List<Jogada> jogadas, JogadorId observador) {
        return jogadas.stream()
                .map(jogada -> new JogadaVisivel(
                        jogada.jogador(),
                        jogada.encoberta() && !jogada.jogador().equals(observador)
                                ? Optional.empty()
                                : Optional.of(jogada.carta()),
                        jogada.encoberta()))
                .toList();
    }

    private static EstadoDaPartida responderDescarte(
            EstadoDaPartida estado, JogadorId jogador, boolean descarta, List<Evento> eventos) {
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        Rodada rodada = estado.rodada();
        Descarte descarte = rodada.descarte();
        Set<JogadorId> responderam = new HashSet<>(descarte.jaResponderam());
        responderam.add(jogador);
        boolean donoDescarta = descarte.donoDescarta() || descarta;
        if (responderam.size() < configuracao.jogadoresNaOrdemDaMesa().size()) {
            // RG-DESC-10: o passo só se resolve quando todos responderam, para não revelar quem tem a carta da vez.
            return comRodada(
                    estado, rodada.comDescarte(new Descarte(descarte.descartadas(), false, responderam, donoDescarta)));
        }
        if (!donoDescarta) {
            // RG-DESC-5 e RG-DESC-6: o dono recusou ou ninguém tinha a carta; o evento é o mesmo nos dois casos.
            eventos.add(new DescarteEncerrado());
            return comRodada(estado, rodada.comDescarte(Descarte.encerrado(descarte.descartadas())));
        }
        // RG-DESC-4 e RG-DESC-7: a carta da vez sai da rodada, e o dono compra a do topo do baralho, na mesma posição.
        Carta cartaDaVez = cartaDaVez(configuracao, rodada).orElseThrow();
        JogadorId dono = configuracao.jogadoresNaOrdemDaMesa().stream()
                .filter(candidato -> rodada.maoDe(candidato).contains(cartaDaVez))
                .findFirst()
                .orElseThrow();
        Carta comprada = rodada.baralhoRestante().getFirst();
        List<Carta> mao = new ArrayList<>(rodada.maoDe(dono));
        mao.set(mao.indexOf(cartaDaVez), comprada);
        Map<JogadorId, List<Carta>> maos = new HashMap<>(rodada.maos());
        maos.put(dono, mao);
        List<Carta> descartadas = comAcrescimo(descarte.descartadas(), cartaDaVez);
        eventos.add(new CartaDescartada(dono, cartaDaVez)); // RG-DESC-8: público
        eventos.add(new CartaRecebidaPorDescarte(dono, comprada)); // RG-DESC-8: privado
        boolean fimDaSequencia =
                descartadas.size() >= sequenciaDeDescarte(configuracao, rodada).size();
        if (fimDaSequencia) {
            eventos.add(new DescarteEncerrado());
        }
        List<Carta> baralhoRestante =
                rodada.baralhoRestante().subList(1, rodada.baralhoRestante().size());
        return comRodada(
                estado,
                rodada.comMaos(maos, baralhoRestante)
                        .comDescarte(new Descarte(descartadas, fimDaSequencia, Set.of(), false)));
    }

    private static EstadoDaPartida jogarCarta(
            EstadoDaPartida estado, JogadorId jogador, int indiceNaMao, boolean encoberta, List<Evento> eventos) {
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        RegrasDeVaza regras = configuracao.variante().regrasDeVaza();
        Rodada rodada = estado.rodada();

        List<Carta> mao = new ArrayList<>(rodada.maoDe(jogador));
        Carta carta = mao.remove(indiceNaMao);
        Map<JogadorId, List<Carta>> maos = new HashMap<>(rodada.maos());
        maos.put(jogador, mao);
        // RG-ENC-3: da carta encoberta, os outros só sabem que foi jogada.
        eventos.add(
                encoberta
                        ? new CartaEncobertaJogada(jogador, rodada.numeroDaVazaAtual())
                        : new CartaJogada(jogador, carta, rodada.numeroDaVazaAtual()));

        List<Jogada> vazaAtual = comAcrescimo(rodada.vazaAtual(), new Jogada(jogador, carta, encoberta));
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
        return comRodada(estado, rodada.comMaos(maos, rodada.baralhoRestante()).comVazas(vazas, vazaAtual));
    }

    private static EstadoDaPartida pedirAumento(EstadoDaPartida estado, JogadorId jogador, List<Evento> eventos) {
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        Aposta aposta = estado.rodada().aposta();
        Optional<PedidoDeAumento> pendente = aposta.pedidoPendente();
        if (pendente.isPresent()) {
            // RG-AUM-3: pedir mais é aceitar o pedido pendente e propor o nível seguinte.
            aposta = aceita(aposta, configuracao.equipeDe(jogador));
            eventos.add(new AumentoAceito(jogador, aposta.valor()));
        }
        // Responde o adversário à direita; num "pedir mais", responde quem tinha pedido.
        JogadorId respondedor =
                pendente.map(PedidoDeAumento::pedinte).orElseGet(() -> configuracao.aDireitaDe(jogador));
        int nivel = configuracao
                .variante()
                .escadaDeApostas()
                .proximoNivel(aposta.valor())
                .orElseThrow();
        eventos.add(new AumentoPedido(jogador, nivel));
        PedidoDeAumento pedido = new PedidoDeAumento(jogador, respondedor, nivel);
        Aposta comPedido = new Aposta(aposta.valor(), aposta.ultimaEquipeQueAceitou(), Optional.of(pedido));
        return comRodada(estado, estado.rodada().comAposta(comPedido));
    }

    private static EstadoDaPartida aceitarAumento(EstadoDaPartida estado, JogadorId jogador, List<Evento> eventos) {
        Aposta aceita = aceita(estado.rodada().aposta(), estado.configuracao().equipeDe(jogador));
        eventos.add(new AumentoAceito(jogador, aceita.valor()));
        return comRodada(estado, estado.rodada().comAposta(aceita));
    }

    private static EstadoDaPartida correr(EstadoDaPartida estado, JogadorId jogador, List<Evento> eventos) {
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        EscadaDeApostas escada = configuracao.variante().escadaDeApostas();
        Aposta aposta = estado.rodada().aposta();
        eventos.add(new JogadorCorreu(jogador));
        Optional<PedidoDeAumento> pendente = aposta.pedidoPendente();
        if (pendente.isPresent()) {
            // RG-AUM-3 e RG-AUM-4: quem pediu ganha o valor que a rodada tinha antes do pedido.
            int pontos = escada.valorAoCorrer(pendente.get().nivelProposto());
            return darVitoria(estado, configuracao.equipeDe(pendente.get().pedinte()), pontos, eventos);
        }
        // RG-AUM-6: quem corre na própria vez entrega a rodada à equipe adversária.
        EquipeId propria = configuracao.equipeDe(jogador);
        EquipeId adversaria = configuracao.equipes().stream()
                .map(Equipe::id)
                .filter(equipe -> !equipe.equals(propria))
                .findFirst()
                .orElseThrow();
        return darVitoria(estado, adversaria, escada.valorAoDesistir(aposta.valor()), eventos);
    }

    /**
     * A aposta depois de aceito o pedido pendente: a rodada vale o nível pedido, e fica registrado quem aceitou, que é
     * quem a escada consulta para saber quem pode aumentar (RG-AUM-3, RG-AUM-5).
     */
    private static Aposta aceita(Aposta aposta, EquipeId quemAceitou) {
        PedidoDeAumento pedido = aposta.pedidoPendente().orElseThrow();
        return new Aposta(pedido.nivelProposto(), Optional.of(quemAceitou), Optional.empty());
    }

    private static EstadoDaPartida encerrarRodada(
            EstadoDaPartida estado, DesfechoDaRodada desfecho, List<Evento> eventos) {
        return switch (desfecho) {
            case DesfechoDaRodada.Vitoria(EquipeId equipe) ->
                darVitoria(estado, equipe, estado.rodada().aposta().valor(), eventos);
            case DesfechoDaRodada.Anulada anulada -> {
                eventos.add(new RodadaAnulada(estado.numeroDaRodada())); // RG-EMP-5: ninguém pontua.
                yield proximaRodada(estado, estado.placar(), eventos);
            }
        };
    }

    private static EstadoDaPartida darVitoria(
            EstadoDaPartida estado, EquipeId equipe, int pontos, List<Evento> eventos) {
        Placar placar = estado.placar().somando(equipe, pontos);
        eventos.add(new RodadaFinalizada(estado.numeroDaRodada(), equipe, pontos));
        eventos.add(new PlacarAtualizado(placar));
        return proximaRodada(estado, placar, eventos);
    }

    private static EstadoDaPartida proximaRodada(EstadoDaPartida estado, Placar placar, List<Evento> eventos) {
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
        Aposta aposta = Aposta.inicial(variante.escadaDeApostas().valorInicial()); // RG-PARTIDA-3
        // RG-DESC-2: o descarte, se a variante tiver, vem antes de tudo na rodada.
        Descarte descarte = variante.regrasDeDescarte().sequencia(vira).isEmpty()
                ? Descarte.encerrado(List.of())
                : Descarte.inicial();

        eventos.add(new RodadaIniciada(numeroDaRodada, carteador, aposta.valor(), vira));
        ordem.forEach(jogador -> eventos.add(new CartasDistribuidas(jogador, maos.get(jogador))));

        // A fase provisória é trocada pela de proximaFase em comRodada.
        Rodada rodada = new Rodada(
                aposta,
                vira,
                maos,
                baralho.subList(proxima, baralho.size()),
                descarte,
                List.of(),
                List.of(),
                new AguardandoJogada(carteador));
        return comRodada(new EstadoDaPartida(configuracao, placar, numeroDaRodada, carteador, rodada), rodada);
    }

    /** O estado com a rodada dada, na fase que {@link #proximaFase} decidir. */
    private static EstadoDaPartida comRodada(EstadoDaPartida estado, Rodada rodada) {
        FaseDaRodada fase = proximaFase(estado.configuracao(), estado.carteador(), rodada);
        return new EstadoDaPartida(
                estado.configuracao(),
                estado.placar(),
                estado.numeroDaRodada(),
                estado.carteador(),
                rodada.comFase(fase));
    }

    /**
     * Ponto único que decide a fase da rodada (CLAUDE.md, seção 7): primeiro o descarte, se ainda não acabou; depois um
     * pedido de aumento pendente; com uma vaza em andamento, joga quem está à direita do último; no começo das vazas,
     * quem está à direita do carteador; depois de uma vaza, quem a variante indicar.
     */
    private static FaseDaRodada proximaFase(ConfiguracaoDaPartida configuracao, JogadorId carteador, Rodada rodada) {
        Optional<Carta> cartaDaVez = cartaDaVez(configuracao, rodada);
        if (cartaDaVez.isPresent()) {
            return new AguardandoDescarte(cartaDaVez.get()); // RG-DESC-2
        }
        Optional<PedidoDeAumento> pendente = rodada.aposta().pedidoPendente();
        if (pendente.isPresent()) {
            return new AguardandoRespostaDeAumento(
                    pendente.get().respondedor(), pendente.get().nivelProposto()); // RG-AUM-3
        }
        if (!rodada.vazaAtual().isEmpty()) {
            return new AguardandoJogada(
                    configuracao.aDireitaDe(rodada.vazaAtual().getLast().jogador())); // RG-VAZA-1
        }
        if (rodada.vazas().isEmpty()) {
            return new AguardandoJogada(configuracao.aDireitaDe(carteador)); // RG-PARTIDA-4
        }
        // RG-VAZA-2 e RG-EMP-6
        return new AguardandoJogada(configuracao
                .variante()
                .regrasDeVaza()
                .abreAProxima(rodada.vazas().getLast()));
    }

    /** A carta da vez no descarte: a seguinte da sequência depois das já descartadas (RG-DESC-3, RG-DESC-4). */
    private static Optional<Carta> cartaDaVez(ConfiguracaoDaPartida configuracao, Rodada rodada) {
        if (rodada.descarte().encerrado()) {
            return Optional.empty();
        }
        List<Carta> sequencia = sequenciaDeDescarte(configuracao, rodada);
        int posicao = rodada.descarte().descartadas().size();
        return posicao < sequencia.size() ? Optional.of(sequencia.get(posicao)) : Optional.empty();
    }

    private static List<Carta> sequenciaDeDescarte(ConfiguracaoDaPartida configuracao, Rodada rodada) {
        return configuracao.variante().regrasDeDescarte().sequencia(rodada.vira());
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
