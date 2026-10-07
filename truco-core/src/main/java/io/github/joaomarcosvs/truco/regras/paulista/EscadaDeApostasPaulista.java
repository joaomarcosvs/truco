package io.github.joaomarcosvs.truco.regras.paulista;

import io.github.joaomarcosvs.truco.partida.Aposta;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.regras.EscadaDeApostas;
import java.util.List;
import java.util.OptionalInt;

/** Escada de apostas do Truco Paulista: 1, truco (3), seis, nove e doze (RG-PARTIDA-3, RG-AUM-*). */
final class EscadaDeApostasPaulista implements EscadaDeApostas {

    /** RG-AUM-1: do valor inicial ao teto. */
    private static final List<Integer> NIVEIS = List.of(1, 3, 6, 9, 12);

    @Override
    public int valorInicial() {
        return NIVEIS.getFirst(); // RG-PARTIDA-3
    }

    @Override
    public OptionalInt proximoNivel(int valor) {
        int posicao = posicao(valor);
        return posicao + 1 < NIVEIS.size() ? OptionalInt.of(NIVEIS.get(posicao + 1)) : OptionalInt.empty();
    }

    @Override
    public int valorAoCorrer(int nivelRecusado) {
        // RG-AUM-4: quem pediu ganha o nível anterior ao recusado.
        int posicao = posicao(nivelRecusado);
        if (posicao == 0) {
            throw new IllegalArgumentException("O valor inicial não é um pedido de aumento: " + nivelRecusado);
        }
        return NIVEIS.get(posicao - 1);
    }

    @Override
    public int valorAoDesistir(int valorAtual) {
        posicao(valorAtual);
        return valorAtual; // RG-AUM-6: o adversário ganha o valor atual da rodada.
    }

    @Override
    public boolean podeAumentar(Aposta aposta, EquipeId equipe) {
        // RG-AUM-1: o doze é o teto. RG-AUM-5: depois de um aceite, só a equipe que aceitou pode pedir o próximo.
        return proximoNivel(aposta.valor()).isPresent()
                && aposta.ultimaEquipeQueAceitou().map(equipe::equals).orElse(true);
    }

    private static int posicao(int valor) {
        int posicao = NIVEIS.indexOf(valor);
        if (posicao < 0) {
            throw new IllegalArgumentException("Valor fora da escada do Truco Paulista: " + valor);
        }
        return posicao;
    }
}
