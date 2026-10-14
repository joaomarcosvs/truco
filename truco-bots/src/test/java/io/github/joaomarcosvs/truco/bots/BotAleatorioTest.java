package io.github.joaomarcosvs.truco.bots;

import static io.github.joaomarcosvs.truco.bots.Partidas.ANA;
import static io.github.joaomarcosvs.truco.bots.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.bots.Partidas.configuracao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BotAleatorioTest {

    private static final VisaoDoJogador VISAO = MOTOR.visaoDe(MOTOR.novaPartida(configuracao(2026)), ANA);

    private static final List<Acao> LEGAIS =
            List.of(new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new PedirAumento(), new Correr());

    private static List<Acao> escolhas(BotAleatorio bot, int quantas) {
        List<Acao> escolhas = new ArrayList<>();
        for (int i = 0; i < quantas; i++) {
            escolhas.add(bot.decidir(VISAO, LEGAIS));
        }
        return escolhas;
    }

    @Test
    @DisplayName("Escolhe sempre uma das ações legais e, com o tempo, todas elas")
    void escolheEntreAsLegais() {
        List<Acao> escolhas = escolhas(new BotAleatorio(7), 1_000);

        assertThat(LEGAIS).containsAll(escolhas);
        assertThat(new HashSet<>(escolhas)).isEqualTo(Set.copyOf(LEGAIS));
    }

    @Test
    @DisplayName("A mesma seed dá as mesmas escolhas, e seeds diferentes dão escolhas diferentes")
    void deterministico() {
        assertThat(escolhas(new BotAleatorio(42), 100)).isEqualTo(escolhas(new BotAleatorio(42), 100));
        assertThat(escolhas(new BotAleatorio(42), 100)).isNotEqualTo(escolhas(new BotAleatorio(43), 100));
    }

    @Test
    @DisplayName("As escolhas da seed 2026 são fixas: o algoritmo do Random é o mesmo em qualquer JVM")
    void escolhasFixas() {
        List<Integer> posicoes = escolhas(new BotAleatorio(2026), 12).stream()
                .map(LEGAIS::indexOf)
                .toList();

        assertThat(posicoes).containsExactly(4, 0, 2, 0, 4, 3, 3, 1, 4, 3, 4, 4);
    }

    @Test
    @DisplayName("Com uma única ação legal, escolhe essa ação")
    void umaSoAcao() {
        assertThat(new BotAleatorio(1).decidir(VISAO, List.of(new Correr()))).isEqualTo(new Correr());
    }

    @Test
    @DisplayName("Sem ações legais, é erro de programação")
    void semAcoes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BotAleatorio(1).decidir(VISAO, List.of()));
    }
}
