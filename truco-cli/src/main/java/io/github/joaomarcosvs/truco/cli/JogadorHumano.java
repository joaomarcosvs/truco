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
        saida.println();
        tela.mesa(visao).forEach(saida::println);
        saida.println("O que você faz?");
        for (int i = 0; i < legais.size(); i++) {
            saida.println("  %d) %s".formatted(i + 1, tela.opcao(legais.get(i), visao)));
        }
        while (true) {
            saida.print("> ");
            saida.flush();
            String linha = lerLinha();
            if (linha == null || linha.strip().equalsIgnoreCase("sair")) {
                throw new PartidaInterrompida();
            }
            try {
                int escolha = Integer.parseInt(linha.strip());
                if (escolha >= 1 && escolha <= legais.size()) {
                    return legais.get(escolha - 1);
                }
            } catch (NumberFormatException naoENumero) {
                // Cai na mensagem abaixo e pergunta de novo.
            }
            saida.println("Opção inválida: digite um número de 1 a %d, ou \"sair\".".formatted(legais.size()));
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
