package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.Partidas.aplicarAceita;

import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.evento.Evento;
import java.util.ArrayList;
import java.util.List;

/** Aplica jogadas em sequência e acumula os eventos, para os testes de cenário. */
final class MesaDeTeste {

    private EstadoDaPartida estado;
    private final List<Evento> eventos = new ArrayList<>();

    MesaDeTeste(EstadoDaPartida estado) {
        this.estado = estado;
    }

    /** O jogador joga a carta indicada; ela precisa estar na mão dele, e o motor precisa aceitar. */
    MesaDeTeste jogar(JogadorId jogador, String notacao) {
        int indice = estado.rodada().maoDe(jogador).indexOf(carta(notacao));
        if (indice < 0) {
            throw new AssertionError(jogador + " não tem " + notacao + " na mão: "
                    + estado.rodada().maoDe(jogador));
        }
        Aplicada aplicada = aplicarAceita(estado, jogador, new JogarCarta(indice));
        estado = aplicada.novoEstado();
        eventos.addAll(aplicada.eventos());
        return this;
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
