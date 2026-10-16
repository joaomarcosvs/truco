package io.github.joaomarcosvs.truco.cli;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.bots.Estrategia;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * O jogador humano, no terminal: mostra a mesa e um menu numerado com as ações legais e lê a escolha. Como só oferece
 * ações legais, o motor nunca recebe uma ação rejeitada.
 */
final class JogadorHumano implements Estrategia {

    private static final List<Acao> SO_PASSAR = List.of(new RecusarDescarte());

    private final Tela tela;
    private final Narrador narrador;
    private final BufferedReader entrada;
    private final PrintStream saida;

    JogadorHumano(Tela tela, Narrador narrador, BufferedReader entrada, PrintStream saida) {
        this.tela = tela;
        this.narrador = narrador;
        this.entrada = entrada;
        this.saida = saida;
    }

    /**
     * @throws PartidaInterrompida se a entrada acabar ou o jogador digitar "sair"
     */
    @Override
    public Acao decidir(VisaoDoJogador visao, List<Acao> legais) {
        if (legais.equals(SO_PASSAR)) {
            // No descarte todos respondem (RG-DESC-10); sem a carta da vez, não há o que decidir.
            saida.println("Descarte: a carta da vez é %s; você não pode descartar e passa."
                    .formatted(visao.cartaDaVezNoDescarte().map(narrador::carta).orElse("?")));
            return legais.getFirst();
        }
        mostrarMesa(visao, "O que você faz?");
        mostrarOpcoes(legais, visao);
        return legais.get(lerEscolha(legais.size()) - 1);
    }

    /**
     * Fora da sua vez, no 2x2: o seu parceiro recebeu um pedido de aumento, e você decide se quer ver as cartas dele
     * (RG-2X2-9). Não querer é não fazer nada.
     *
     * @throws PartidaInterrompida se a entrada acabar ou o jogador digitar "sair"
     */
    @Override
    public Optional<Acao> decidirOpcional(VisaoDoJogador visao, List<Acao> legais) {
        mostrarMesa(visao, "Seu parceiro vai responder ao pedido. O que você faz?");
        mostrarOpcoes(legais, visao);
        saida.println("  %d) Não ver as cartas".formatted(legais.size() + 1));
        int escolha = lerEscolha(legais.size() + 1);
        return escolha <= legais.size() ? Optional.of(legais.get(escolha - 1)) : Optional.empty();
    }

    private void mostrarMesa(VisaoDoJogador visao, String pergunta) {
        saida.println();
        tela.mesa(visao).forEach(saida::println);
        saida.println(pergunta);
    }

    private void mostrarOpcoes(List<Acao> legais, VisaoDoJogador visao) {
        for (int i = 0; i < legais.size(); i++) {
            saida.println("  %d) %s".formatted(i + 1, tela.opcao(legais.get(i), visao)));
        }
    }

    /** Lê um número de 1 a {@code opcoes}, perguntando de novo até vir um válido. */
    private int lerEscolha(int opcoes) {
        while (true) {
            saida.print("> ");
            saida.flush();
            String linha = lerLinha();
            if (linha == null || linha.strip().equalsIgnoreCase("sair")) {
                throw new PartidaInterrompida();
            }
            try {
                int escolha = Integer.parseInt(linha.strip());
                if (escolha >= 1 && escolha <= opcoes) {
                    return escolha;
                }
            } catch (NumberFormatException naoENumero) {
                // Cai na mensagem abaixo e pergunta de novo.
            }
            saida.println("Opção inválida: digite um número de 1 a %d, ou \"sair\".".formatted(opcoes));
        }
    }

    private String lerLinha() {
        try {
            return entrada.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
