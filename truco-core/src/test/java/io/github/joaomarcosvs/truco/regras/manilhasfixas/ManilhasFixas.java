package io.github.joaomarcosvs.truco.regras.manilhasfixas;

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

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.partida.DesfechoDaRodada;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.Jogada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import io.github.joaomarcosvs.truco.partida.Vaza;
import io.github.joaomarcosvs.truco.regras.ComposicaoDoBaralho;
import io.github.joaomarcosvs.truco.regras.EscadaDeApostas;
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import io.github.joaomarcosvs.truco.regras.PontuacaoDaPartida;
import io.github.joaomarcosvs.truco.regras.RegrasDeDescarte;
import io.github.joaomarcosvs.truco.regras.RegrasDeDupla;
import io.github.joaomarcosvs.truco.regras.RegrasDeVaza;
import io.github.joaomarcosvs.truco.regras.RegrasEspeciais;
import io.github.joaomarcosvs.truco.regras.VarianteDeRegras;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Variante mínima do teste de prontidão (CLAUDE.md, seção 7, marco M10): manilhas fixas, como no Truco Mineiro, em vez
 * de manilhas que dependem da vira. Não é o Truco Mineiro: só troca o que as manilhas fixas exigem e reaproveita o
 * resto do Truco Paulista pelas interfaces.
 *
 * <ul>
 *   <li>Ordem de força própria: as manilhas são 4♣ (zap), 7♥, A♠ e 7♦, da mais forte para a mais fraca; não há vira.
 *   <li>Sem descarte, porque a sequência do paulista depende da vira.
 *   <li>Regras de vaza do paulista, com a comparação das cartas pela ordem de força desta variante.
 *   <li>Baralho, escada de apostas, rodadas especiais e pontos para vencer do paulista.
 * </ul>
 */
public record ManilhasFixas() implements VarianteDeRegras {

    private static final VarianteDeRegras PAULISTA = new TrucoPaulista();
    private static final OrdemDeForca ORDEM_DE_FORCA = new OrdemComManilhasFixas();
    private static final RegrasDeVaza REGRAS_DE_VAZA = new RegrasDeVazaComManilhasFixas();
    private static final RegrasDeDescarte SEM_DESCARTE = vira -> List.of();

    @Override
    public ComposicaoDoBaralho composicaoDoBaralho() {
        return PAULISTA.composicaoDoBaralho();
    }

    @Override
    public OrdemDeForca ordemDeForca() {
        return ORDEM_DE_FORCA;
    }

    @Override
    public RegrasDeDescarte regrasDeDescarte() {
        return SEM_DESCARTE;
    }

    @Override
    public RegrasDeVaza regrasDeVaza() {
        return REGRAS_DE_VAZA;
    }

    @Override
    public EscadaDeApostas escadaDeApostas() {
        return PAULISTA.escadaDeApostas();
    }

    @Override
    public RegrasEspeciais regrasEspeciais() {
        return PAULISTA.regrasEspeciais();
    }

    @Override
    public RegrasDeDupla regrasDeDupla() {
        return PAULISTA.regrasDeDupla();
    }

    @Override
    public PontuacaoDaPartida pontuacaoDaPartida() {
        return PAULISTA.pontuacaoDaPartida();
    }

    /** Manilhas fixas acima de tudo; as outras cartas pelo valor, sem olhar o naipe. */
    private static final class OrdemComManilhasFixas implements OrdemDeForca {

        /** Da mais forte para a mais fraca. */
        private static final List<Carta> MANILHAS = List.of(
                new Carta(QUATRO, PAUS), new Carta(SETE, COPAS), new Carta(AS, ESPADAS), new Carta(SETE, OUROS));

        /** As cartas comuns, da mais fraca para a mais forte. */
        private static final List<Valor> VALORES =
                List.of(QUATRO, CINCO, SEIS, SETE, DEZ, DAMA, VALETE, AS, DOIS, TRES);

        @Override
        public boolean usaVira() {
            return false;
        }

        @Override
        public int forca(Carta carta, Optional<Carta> vira) {
            if (vira.isPresent()) {
                throw new IllegalArgumentException("Com manilhas fixas não há vira: " + vira.get());
            }
            int manilha = MANILHAS.indexOf(carta);
            if (manilha >= 0) {
                return VALORES.size() + MANILHAS.size() - manilha;
            }
            int forca = VALORES.indexOf(carta.valor());
            if (forca < 0) {
                throw new IllegalArgumentException("Carta fora do baralho: " + carta);
            }
            return forca;
        }
    }

    /**
     * As regras de vaza do paulista (cartas por jogador, encoberta, quem abre a seguinte, desfecho da rodada), mas com a
     * comparação das cartas pela ordem de força desta variante.
     */
    private static final class RegrasDeVazaComManilhasFixas implements RegrasDeVaza {

        private final RegrasDeVaza paulista = PAULISTA.regrasDeVaza();

        @Override
        public int cartasPorJogador() {
            return paulista.cartasPorJogador();
        }

        @Override
        public boolean permiteEncoberta(int numeroDaVaza) {
            return paulista.permiteEncoberta(numeroDaVaza);
        }

        /** Vence a carta aberta mais forte; empatam as mais fortes de equipes diferentes e as vazas só de encobertas. */
        @Override
        public ResultadoDaVaza resultado(
                List<Jogada> jogadas, Optional<Carta> vira, Function<JogadorId, EquipeId> equipeDe) {
            List<Jogada> abertas =
                    jogadas.stream().filter(jogada -> !jogada.encoberta()).toList();
            int maisForte = abertas.stream()
                    .mapToInt(jogada -> ORDEM_DE_FORCA.forca(jogada.carta(), vira))
                    .max()
                    .orElse(-1);
            List<Jogada> maisFortes = abertas.stream()
                    .filter(jogada -> ORDEM_DE_FORCA.forca(jogada.carta(), vira) == maisForte)
                    .toList();
            if (maisFortes.isEmpty()
                    || maisFortes.stream()
                                    .map(jogada -> equipeDe.apply(jogada.jogador()))
                                    .distinct()
                                    .count()
                            > 1) {
                return new ResultadoDaVaza.Empatada();
            }
            JogadorId vencedor = maisFortes.getFirst().jogador();
            return new ResultadoDaVaza.Vencida(vencedor, equipeDe.apply(vencedor));
        }

        @Override
        public JogadorId abreAProxima(Vaza vaza) {
            return paulista.abreAProxima(vaza);
        }

        @Override
        public Optional<DesfechoDaRodada> desfecho(List<Vaza> vazas) {
            return paulista.desfecho(vazas);
        }
    }
}
