package io.github.joaomarcosvs.truco.web;

import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.MOTOR;
import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.VOCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.Rodada;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Passo;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Reconstituida;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A resposta da web leva, além do protocolo, os textos da CLI. Nada nela (visões, eventos, textos e ações) pode mostrar
 * as cartas do bot, as do baralho ou as encobertas do bot, nem a sua própria mão na Rodada Escurinho.
 */
class InformacaoOcultaNaWebTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Uma carta no JSON do protocolo. */
    private static final Pattern CARTA_NO_JSON = Pattern.compile("\\{\"valor\":\"([A-Z]+)\",\"naipe\":\"([A-Z]+)\"}");

    /** Uma carta num texto, como "10♠". */
    private static final Pattern CARTA_NO_TEXTO = Pattern.compile("(10|[2-9AJQK])([♥♠♦♣])");

    @Property(tries = 60)
    @Label("RG-VIS-1 a RG-VIS-3: em partidas inteiras com escolhas aleatórias, a resposta da web nunca mostra uma carta"
            + " oculta para você")
    void nadaVaza(@ForAll long seed, @ForAll long sementeDasEscolhas) {
        Random escolhas = new Random(sementeDasEscolhas);
        List<Acao> jogadas = new ArrayList<>();
        while (true) {
            Reconstituida partida = (Reconstituida) PartidaReconstituida.reconstituir(seed, jogadas);
            JsonNode resposta = JSON.readTree(RespostaJson.partida(seed, partida));
            conferir(partida, resposta);
            if (partida.estado().rodada().fase() instanceof FaseDaRodada.PartidaFinalizada) {
                return;
            }
            List<Acao> legais = MOTOR.acoesLegais(partida.estado(), VOCE);
            jogadas.add(legais.get(escolhas.nextInt(legais.size())));
        }
    }

    private static void conferir(Reconstituida partida, JsonNode resposta) {
        for (int i = 0; i < partida.passos().size(); i++) {
            Passo passo = partida.passos().get(i);
            JsonNode json = resposta.get("passos").get(i);
            Set<Carta> ocultas = ocultasParaVoce(passo.estado());
            // A carta da vez no descarte é pública; o que não pode vazar é quem a tem (RG-DESC-10).
            if (passo.estado().rodada().fase() instanceof FaseDaRodada.AguardandoDescarte(Carta cartaDaVez)) {
                ocultas.remove(cartaDaVez);
            }
            assertThat(mostradas(json.get("visao"))).as("visão do passo %d", i).doesNotContainAnyElementsOf(ocultas);
            // Os eventos antes de RodadaIniciada falam da rodada que acabou, cujas cartas voltaram ao baralho.
            List<JsonNode> eventos = new ArrayList<>();
            json.get("eventos").forEach(eventos::add);
            int inicioDaRodadaNova = 0;
            for (int j = 0; j < eventos.size(); j++) {
                if (eventos.get(j).get("evento").get("tipo").asString().equals("RodadaIniciada")) {
                    inicioDaRodadaNova = j;
                }
            }
            for (JsonNode evento : eventos.subList(inicioDaRodadaNova, eventos.size())) {
                assertThat(mostradas(evento)).as("evento %s", evento).doesNotContainAnyElementsOf(ocultas);
            }
        }
        Set<Carta> ocultasAgora = ocultasParaVoce(partida.estado());
        for (JsonNode acao : resposta.get("acoesLegais")) {
            assertThat(mostradas(acao)).as("ação %s", acao).doesNotContainAnyElementsOf(ocultasAgora);
        }
    }

    /** O baralho restante, a mão e as encobertas do bot e, na Escurinho, a sua mão. */
    private static Set<Carta> ocultasParaVoce(EstadoDaPartida estado) {
        Rodada rodada = estado.rodada();
        Set<Carta> ocultas = new HashSet<>(rodada.baralhoRestante());
        rodada.maos().forEach((dono, mao) -> {
            if (!dono.equals(VOCE) || rodada.tipo() instanceof TipoDeRodada.Escurinho) {
                ocultas.addAll(mao);
            }
        });
        Stream.concat(rodada.vazas().stream().flatMap(vaza -> vaza.jogadas().stream()), rodada.vazaAtual().stream())
                .filter(jogada -> jogada.encoberta() && !jogada.jogador().equals(VOCE))
                .forEach(jogada -> ocultas.add(jogada.carta()));
        return ocultas;
    }

    /** As cartas que aparecem no trecho, no formato do protocolo ou em texto. */
    private static Set<Carta> mostradas(JsonNode trecho) {
        Set<Carta> cartas = new HashSet<>();
        String json = trecho.toString();
        Matcher noJson = CARTA_NO_JSON.matcher(json);
        while (noJson.find()) {
            cartas.add(new Carta(Valor.valueOf(noJson.group(1)), Naipe.valueOf(noJson.group(2))));
        }
        Matcher noTexto = CARTA_NO_TEXTO.matcher(json);
        while (noTexto.find()) {
            for (Valor valor : Valor.values()) {
                for (Naipe naipe : Naipe.values()) {
                    if (valor.simbolo().equals(noTexto.group(1))
                            && naipe.simbolo().equals(noTexto.group(2))) {
                        cartas.add(new Carta(valor, naipe));
                    }
                }
            }
        }
        return cartas;
    }
}
