package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.Partidas.aplicarAceita;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.JogarEncoberta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.evento.Evento;
import java.util.ArrayList;
import java.util.List;

/** Aplica ações em sequência e acumula os eventos, para os testes de cenário. Toda ação precisa ser aceita. */
final class MesaDeTeste {

    private EstadoDaPartida estado;
    private final List<Evento> eventos = new ArrayList<>();

    MesaDeTeste(EstadoDaPartida estado) {
        this.estado = estado;
    }

    /** O jogador joga aberta a carta indicada, que precisa estar na mão dele. */
    MesaDeTeste jogar(JogadorId jogador, String notacao) {
        return agir(jogador, new JogarCarta(posicaoNaMao(jogador, notacao)));
    }

    /** O jogador joga encoberta a carta indicada, que precisa estar na mão dele. */
    MesaDeTeste jogarEncoberta(JogadorId jogador, String notacao) {
        return agir(jogador, new JogarEncoberta(posicaoNaMao(jogador, notacao)));
    }

    MesaDeTeste pedirAumento(JogadorId jogador) {
        return agir(jogador, new PedirAumento());
    }

    MesaDeTeste aceitar(JogadorId jogador) {
        return agir(jogador, new Aceitar());
    }

    MesaDeTeste correr(JogadorId jogador) {
        return agir(jogador, new Correr());
    }

    MesaDeTeste agir(JogadorId jogador, Acao acao) {
        Aplicada aplicada = aplicarAceita(estado, jogador, acao);
        estado = aplicada.novoEstado();
        eventos.addAll(aplicada.eventos());
        return this;
    }

    private int posicaoNaMao(JogadorId jogador, String notacao) {
        int indice = estado.rodada().maoDe(jogador).indexOf(carta(notacao));
        if (indice < 0) {
            throw new AssertionError(jogador + " não tem " + notacao + " na mão: "
                    + estado.rodada().maoDe(jogador));
        }
        return indice;
    }

    EstadoDaPartida estado() {
        return estado;
    }

    /** Todos os eventos até agora, na ordem. */
    List<Evento> eventos() {
        return List.copyOf(eventos);
    }

    /** Os eventos de um tipo, na ordem. */
    <T extends Evento> List<T> eventos(Class<T> tipo) {
        return eventos.stream().filter(tipo::isInstance).map(tipo::cast).toList();
    }
}
