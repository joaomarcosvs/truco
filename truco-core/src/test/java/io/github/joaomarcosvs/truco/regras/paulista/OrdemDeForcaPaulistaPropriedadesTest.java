package io.github.joaomarcosvs.truco.regras.paulista;

import static io.github.joaomarcosvs.truco.carta.Naipe.COPAS;
import static io.github.joaomarcosvs.truco.carta.Naipe.ESPADAS;
import static io.github.joaomarcosvs.truco.carta.Naipe.OUROS;
import static io.github.joaomarcosvs.truco.carta.Naipe.PAUS;
import static io.github.joaomarcosvs.truco.carta.Valor.AS;
import static io.github.joaomarcosvs.truco.carta.Valor.CINCO;
import static io.github.joaomarcosvs.truco.carta.Valor.DAMA;
import static io.github.joaomarcosvs.truco.carta.Valor.DEZ;
import static io.github.joaomarcosvs.truco.carta.Valor.DOIS;
import static io.github.joaomarcosvs.truco.carta.Valor.QUATRO;
import static io.github.joaomarcosvs.truco.carta.Valor.SEIS;
import static io.github.joaomarcosvs.truco.carta.Valor.SETE;
import static io.github.joaomarcosvs.truco.carta.Valor.TRES;
import static io.github.joaomarcosvs.truco.carta.Valor.VALETE;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Propriedades da ordem de força, conferidas com cada uma das 40 viras possíveis (geração exaustiva). */
class OrdemDeForcaPaulistaPropriedadesTest {

    /** RG-CARTAS-2, copiada do documento de regras. */
    private static final List<Valor> ORDEM_DAS_COMUNS =
            List.of(QUATRO, CINCO, SEIS, SETE, DEZ, DAMA, VALETE, AS, DOIS, TRES);

    /** RG-CARTAS-5, copiada do documento de regras. */
    private static final List<Naipe> ORDEM_DOS_NAIPES = List.of(OUROS, ESPADAS, COPAS, PAUS);

    /** Tabela de RG-CARTAS-4, copiada do documento de regras: valor da vira e valor da manilha. */
    private static final Map<Valor, Valor> MANILHA_POR_VIRA = Map.ofEntries(
            entry(QUATRO, CINCO),
            entry(CINCO, SEIS),
            entry(SEIS, SETE),
            entry(SETE, DEZ),
            entry(DEZ, DAMA),
            entry(DAMA, VALETE),
            entry(VALETE, AS),
            entry(AS, DOIS),
            entry(DOIS, TRES),
            entry(TRES, QUATRO));

    private static final List<Carta> BARALHO =
            new TrucoPaulista().composicaoDoBaralho().cartas();

    private final OrdemDeForca ordem = new TrucoPaulista().ordemDeForca();

    @Provide
    Arbitrary<Carta> viras() {
        return Arbitraries.of(BARALHO);
    }

    @Property(generation = GenerationMode.EXHAUSTIVE)
    @Label("RG-CARTAS-4 e RG-CARTAS-5: toda vira gera 4 manilhas, uma de cada naipe, que vencem todas as outras cartas")
    void quatroManilhasPorVira(@ForAll("viras") Carta vira) {
        // Manilha, aqui, é a carta que vence todas as cartas de outros valores.
        List<Carta> manilhas = BARALHO.stream()
                .filter(carta -> venceTodas(carta, cartasDeOutrosValores(carta), vira))
                .toList();

        assertThat(manilhas).extracting(Carta::naipe).containsExactlyInAnyOrder(Naipe.values());
        assertThat(manilhas).extracting(Carta::valor).containsOnly(MANILHA_POR_VIRA.get(vira.valor()));
    }

    @Property(generation = GenerationMode.EXHAUSTIVE)
    @Label("RG-CARTAS-5: em toda rodada, o zap (a manilha de paus) é a única carta que vence todas as outras")
    void zapEhAUnicaCartaImbativel(@ForAll("viras") Carta vira) {
        List<Carta> imbativeis = BARALHO.stream()
                .filter(carta -> venceTodas(carta, todasMenos(carta), vira))
                .toList();

        assertThat(imbativeis).containsExactly(new Carta(MANILHA_POR_VIRA.get(vira.valor()), PAUS));
    }

    @Property(generation = GenerationMode.EXHAUSTIVE)
    @Label("RG-CARTAS-2, 4, 5 e 6: com qualquer vira, as 40 cartas se ordenam exatamente como no documento")
    void forcaSegueODocumento(@ForAll("viras") Carta vira) {
        for (Carta a : BARALHO) {
            for (Carta b : BARALHO) {
                int esperado = Integer.compare(posicaoNoDocumento(a, vira), posicaoNoDocumento(b, vira));
                assertThat(Integer.signum(ordem.comparar(a, b, Optional.of(vira))))
                        .as("%s contra %s, com vira %s", a, b, vira)
                        .isEqualTo(esperado);
            }
        }
    }

    private boolean venceTodas(Carta carta, List<Carta> adversarias, Carta vira) {
        return adversarias.stream().allMatch(outra -> ordem.comparar(carta, outra, Optional.of(vira)) > 0);
    }

    private static List<Carta> cartasDeOutrosValores(Carta carta) {
        return BARALHO.stream().filter(outra -> outra.valor() != carta.valor()).toList();
    }

    private static List<Carta> todasMenos(Carta carta) {
        return BARALHO.stream().filter(outra -> !outra.equals(carta)).toList();
    }

    /** Posição na ordem do documento: as comuns pelo valor (RG-CARTAS-2), as manilhas acima delas pelo naipe (RG-CARTAS-5). */
    private static int posicaoNoDocumento(Carta carta, Carta vira) {
        if (carta.valor() == MANILHA_POR_VIRA.get(vira.valor())) {
            return ORDEM_DAS_COMUNS.size() + ORDEM_DOS_NAIPES.indexOf(carta.naipe());
        }
        return ORDEM_DAS_COMUNS.indexOf(carta.valor());
    }
}
