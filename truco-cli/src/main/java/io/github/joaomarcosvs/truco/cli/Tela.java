package io.github.joaomarcosvs.truco.cli;

import static io.github.joaomarcosvs.truco.cli.Narrador.comMaiuscula;
import static io.github.joaomarcosvs.truco.cli.Narrador.pontos;
import static java.util.stream.Collectors.joining;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.JogarEncoberta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.acao.VerCartasDoParceiro;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.PedidoDeAumento;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;
import io.github.joaomarcosvs.truco.regras.EscadaDeApostas;
import io.github.joaomarcosvs.truco.regras.RegrasEspeciais;
import io.github.joaomarcosvs.truco.regras.VarianteDeRegras;
import io.github.joaomarcosvs.truco.visao.JogadaVisivel;
import io.github.joaomarcosvs.truco.visao.VazaVisivel;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Desenha a mesa como o jogador a vê e descreve as ações legais como opções de menu. */
final class Tela {

    private final Narrador narrador;
    private final EscadaDeApostas escada;
    private final RegrasEspeciais especiais;

    Tela(Narrador narrador, VarianteDeRegras variante) {
        this.narrador = narrador;
        this.escada = variante.escadaDeApostas();
        this.especiais = variante.regrasEspeciais();
    }

    /** A mesa, uma informação por linha: rodada e placar, rodada especial, aumento pendente, descarte, vazas e mãos. */
    List<String> mesa(VisaoDoJogador visao) {
        List<String> linhas = new ArrayList<>();
        linhas.add("Rodada %d | vale %d | %s%s"
                .formatted(
                        visao.numeroDaRodada(),
                        visao.aposta().valor(),
                        narrador.placar(visao.placar()),
                        visao.vira()
                                .map(vira -> " | vira " + narrador.carta(vira))
                                .orElse("")));
        switch (visao.tipoDaRodada()) {
            case TipoDeRodada.DeOnze onze ->
                linhas.add("Rodada de Onze: %s %s."
                        .formatted(
                                narrador.nome(onze.equipeComOnze()),
                                onze.decidiuJogar() ? "decidiu jogar" : "decide se joga ou corre"));
            case TipoDeRodada.Escurinho escurinho -> linhas.add("Rodada Escurinho: ninguém vê as próprias cartas.");
            case TipoDeRodada.Normal normal -> {}
        }
        visao.aposta().pedidoPendente().ifPresent(pedido -> {
            linhas.add(comMaiuscula(
                    "%s pediu aumento para %d.".formatted(narrador.nome(pedido.pedinte()), pedido.nivelProposto())));
            if (!pedido.queremVerAsCartas().isEmpty()) {
                linhas.add("Quer ver as cartas do parceiro: %s.".formatted(nomes(pedido.queremVerAsCartas())));
            }
        });
        if (!visao.descartadas().isEmpty()) {
            linhas.add("Descartadas: " + narrador.cartas(visao.descartadas()));
        }
        visao.cartaDaVezNoDescarte()
                .ifPresent(carta -> linhas.add("Descarte: a carta da vez é " + narrador.carta(carta) + "."));
        List<VazaVisivel> vazas = visao.vazas();
        for (int i = 0; i < vazas.size(); i++) {
            linhas.add("Vaza %d: %s (%s)"
                    .formatted(
                            i + 1,
                            jogadas(vazas.get(i).jogadas()),
                            narrador.resultado(vazas.get(i).resultado())));
        }
        if (!visao.vazaAtual().isEmpty()) {
            linhas.add("Vaza %d: %s".formatted(vazas.size() + 1, jogadas(visao.vazaAtual())));
        }
        // RG-2X2-10 e RG-2X2-11: a mão do parceiro, nos momentos em que a dupla se vê.
        visao.jogadoresNaOrdemDaMesa().stream()
                .filter(visao.maosDosParceiros()::containsKey)
                .forEach(parceiro -> linhas.add("Mão de %s: %s"
                        .formatted(
                                narrador.nome(parceiro),
                                narrador.cartas(visao.maosDosParceiros().get(parceiro)))));
        linhas.add("Sua mão: %s | %s".formatted(mao(visao), cartasDosOutros(visao)));
        return linhas;
    }

    /** A ação como opção de menu, com o efeito que ela tem agora. */
    String opcao(Acao acao, VisaoDoJogador visao) {
        return switch (acao) {
            case JogarCarta(int indiceNaMao) -> "Jogar " + cartaNaPosicao(visao, indiceNaMao);
            case JogarEncoberta(int indiceNaMao) -> "Jogar " + cartaNaPosicao(visao, indiceNaMao) + " encoberta";
            case Descartar(int indiceNaMao) ->
                "Descartar " + cartaNaPosicao(visao, indiceNaMao) + " e comprar outra do baralho";
            case RecusarDescarte recusa -> "Não descartar";
            case PedirAumento pedido ->
                visao.aposta()
                        .pedidoPendente()
                        .map(pendente -> "Aceitar e pedir aumento para " + proximoNivel(pendente.nivelProposto()))
                        .orElseGet(() -> "Pedir aumento para "
                                + proximoNivel(visao.aposta().valor()));
            case Aceitar aceite ->
                decidindoARodadaDeOnze(visao)
                        ? "Jogar a Rodada de Onze (vale %d)".formatted(especiais.valorAoJogarARodadaDeOnze())
                        : "Aceitar (a rodada passa a valer %d)"
                                .formatted(pendente(visao).nivelProposto());
            case Correr corrida ->
                "Correr (%s ganha %s)".formatted(narrador.nomeDoAdversario(), pontos(pontosAoCorrer(visao)));
            case VerCartasDoParceiro ver ->
                "Ver as cartas de %s (só se os dois quiserem; até a resposta)".formatted(nomes(parceiros(visao)));
        };
    }

    /** Os outros jogadores da equipe de quem vê (RG-2X2-9). */
    private static List<JogadorId> parceiros(VisaoDoJogador visao) {
        return visao.equipes().stream()
                .filter(equipe -> equipe.jogadores().contains(visao.jogador()))
                .flatMap(equipe -> equipe.jogadores().stream())
                .filter(jogador -> !jogador.equals(visao.jogador()))
                .toList();
    }

    private String nomes(List<JogadorId> jogadores) {
        return jogadores.stream().map(narrador::nome).collect(joining(" e "));
    }

    private int pontosAoCorrer(VisaoDoJogador visao) {
        if (decidindoARodadaDeOnze(visao)) {
            return especiais.valorAoCorrerDaRodadaDeOnze(); // RG-ONZE-1
        }
        // RG-AUM-4; fora da Onze, só se corre de um pedido de aumento (RG-AUM-6).
        return escada.valorAoCorrer(pendente(visao).nivelProposto());
    }

    /** Na Rodada de Onze, antes da decisão e depois do descarte, quem tem 11 decide se joga ou corre (RG-ONZE-1). */
    private static boolean decidindoARodadaDeOnze(VisaoDoJogador visao) {
        return visao.tipoDaRodada() instanceof TipoDeRodada.DeOnze onze
                && !onze.decidiuJogar()
                && visao.cartaDaVezNoDescarte().isEmpty();
    }

    private static PedidoDeAumento pendente(VisaoDoJogador visao) {
        return visao.aposta().pedidoPendente().orElseThrow();
    }

    private int proximoNivel(int valor) {
        return escada.proximoNivel(valor).orElseThrow();
    }

    /** A carta na posição da mão; na Escurinho, em que o jogador não vê a mão (RG-VIS-3), só a posição. */
    private String cartaNaPosicao(VisaoDoJogador visao, int indiceNaMao) {
        return visao.mao().isEmpty()
                ? "a carta [%d] (às cegas)".formatted(indiceNaMao + 1)
                : narrador.carta(visao.mao().get(indiceNaMao));
    }

    private String mao(VisaoDoJogador visao) {
        if (!visao.mao().isEmpty()) {
            return IntStream.range(0, visao.mao().size())
                    .mapToObj(i -> "[%d] %s"
                            .formatted(i + 1, narrador.carta(visao.mao().get(i))))
                    .collect(joining(" "));
        }
        int cartas = visao.cartasNaMao().getOrDefault(visao.jogador(), 0);
        return cartas == 0
                ? "vazia"
                : IntStream.rangeClosed(1, cartas).mapToObj("[%d] ?"::formatted).collect(joining(" "));
    }

    private String cartasDosOutros(VisaoDoJogador visao) {
        return visao.jogadoresNaOrdemDaMesa().stream()
                .filter(outro -> !outro.equals(visao.jogador()))
                .map(outro -> "%s: %s"
                        .formatted(
                                narrador.nome(outro), cartas(visao.cartasNaMao().getOrDefault(outro, 0))))
                .collect(joining(" | "));
    }

    private static String cartas(int quantas) {
        return quantas == 1 ? "1 carta" : quantas + " cartas";
    }

    private String jogadas(List<JogadaVisivel> jogadas) {
        return jogadas.stream().map(this::jogada).collect(joining(", "));
    }

    /** A carta encoberta dos outros aparece só como "encoberta" (RG-ENC-3); a sua, com a carta. */
    private String jogada(JogadaVisivel jogada) {
        String nome = narrador.nome(jogada.jogador());
        String carta = jogada.carta().map(narrador::carta).orElse("");
        if (!jogada.encoberta()) {
            return nome + " " + carta;
        }
        return carta.isEmpty() ? nome + " encoberta" : nome + " " + carta + " encoberta";
    }
}
