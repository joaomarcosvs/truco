package io.github.joaomarcosvs.truco.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.cli.Notacao;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.Rodada;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

/**
 * Confere que uma resposta da web não mostra a um jogador nenhuma carta que ele não pode ver: as do adversário, as do
 * baralho, as encobertas do adversário e, na Rodada Escurinho, a própria mão (RG-VIS-1 a RG-VIS-3). A resposta leva,
 * além do protocolo, os textos da CLI, então as cartas são procuradas nos dois formatos.
 */
final class CartasOcultas {

    /** Uma carta no JSON do protocolo. */
    private static final Pattern CARTA_NO_JSON = Pattern.compile("\\{\"valor\":\"([A-Z]+)\",\"naipe\":\"([A-Z]+)\"}");

    private static final List<Carta> BARALHO =
            new TrucoPaulista().composicaoDoBaralho().cartas();

    private CartasOcultas() {}

    /** Confere os passos ({@code "passos"} da resposta), cada um contra o estado em que foi gerado. */
    static void conferirPassos(JogadorId jogador, List<Passo> passos, JsonNode passosJson) {
        assertThat(passosJson).hasSize(passos.size());
        for (int i = 0; i < passos.size(); i++) {
            Passo passo = passos.get(i);
            JsonNode json = passosJson.get(i);
            Set<Carta> ocultas = para(jogador, passo.estado());
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
            conferirForcas(json, eventos);
        }
    }

    /**
     * A força só acompanha cartas abertas: a de uma encoberta diria algo sobre a carta (RG-ENC-3). As forças da visão
     * são uma lista por vaza, na ordem das jogadas.
     */
    private static void conferirForcas(JsonNode passo, List<JsonNode> eventos) {
        for (JsonNode evento : eventos) {
            boolean cartaAberta = evento.get("evento").get("tipo").asString().equals("CartaJogada");
            assertThat(evento.has("forca")).as("força no evento %s", evento).isEqualTo(cartaAberta);
        }
        JsonNode visao = passo.get("visao");
        List<JsonNode> vazas = new ArrayList<>();
        visao.get("vazas").forEach(vaza -> vazas.add(vaza.get("jogadas")));
        vazas.add(visao.get("vazaAtual"));
        JsonNode forcas = passo.get("forcas");
        assertThat(forcas).hasSize(vazas.size());
        for (int i = 0; i < vazas.size(); i++) {
            JsonNode jogadas = vazas.get(i);
            assertThat(forcas.get(i)).hasSize(jogadas.size());
            for (int j = 0; j < jogadas.size(); j++) {
                boolean encoberta = jogadas.get(j).get("encoberta").asBoolean();
                assertThat(forcas.get(i).get(j).isNull())
                        .as("força da jogada %d da vaza %d", j, i)
                        .isEqualTo(encoberta);
            }
        }
    }

    /** Confere as ações ({@code "acoesLegais"} da resposta) contra o estado atual. */
    static void conferirAcoes(JogadorId jogador, EstadoDaPartida estado, JsonNode acoesJson) {
        Set<Carta> ocultas = para(jogador, estado);
        for (JsonNode acao : acoesJson) {
            assertThat(mostradas(acao)).as("ação %s", acao).doesNotContainAnyElementsOf(ocultas);
        }
    }

    /**
     * O baralho restante, as mãos e as encobertas dos outros e, na Escurinho, a própria mão. A carta da vez no descarte
     * é pública; o que não pode vazar é quem a tem (RG-DESC-10).
     */
    static Set<Carta> para(JogadorId jogador, EstadoDaPartida estado) {
        Rodada rodada = estado.rodada();
        Set<Carta> ocultas = new HashSet<>(rodada.baralhoRestante());
        Set<JogadorId> parceirosVisiveis = parceirosVisiveis(jogador, estado);
        rodada.maos().forEach((dono, mao) -> {
            boolean visivel = dono.equals(jogador) || parceirosVisiveis.contains(dono);
            if (!visivel || rodada.tipo() instanceof TipoDeRodada.Escurinho) {
                ocultas.addAll(mao);
            }
        });
        Stream.concat(rodada.vazas().stream().flatMap(vaza -> vaza.jogadas().stream()), rodada.vazaAtual().stream())
                .filter(jogada -> jogada.encoberta() && !jogada.jogador().equals(jogador))
                .forEach(jogada -> ocultas.add(jogada.carta()));
        if (rodada.fase() instanceof FaseDaRodada.AguardandoDescarte(Carta cartaDaVez)) {
            ocultas.remove(cartaDaVez);
        }
        return ocultas;
    }

    /**
     * Os parceiros cuja mão o jogador pode ver agora, escrito de novo a partir das regras: quando a dupla dele responde
     * a um aumento e todos quiseram ver as cartas (RG-2X2-10), e na decisão da Rodada de Onze da dupla com 11
     * (RG-2X2-11). No 1x1, nenhum.
     */
    static Set<JogadorId> parceirosVisiveis(JogadorId jogador, EstadoDaPartida estado) {
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        List<JogadorId> parceiros = configuracao.parceirosDe(jogador);
        EquipeId equipe = configuracao.equipeDe(jogador);
        boolean seVeem = switch (estado.rodada().fase()) {
            case FaseDaRodada.AguardandoRespostaDeAumento resposta -> {
                List<JogadorId> querem =
                        estado.rodada().aposta().pedidoPendente().orElseThrow().queremVerAsCartas();
                yield configuracao.equipeDe(resposta.respondedor()).equals(equipe)
                        && querem.contains(jogador)
                        && querem.containsAll(parceiros);
            }
            case FaseDaRodada.DecisaoRodadaDeOnze decisao ->
                configuracao.equipeDe(decisao.jogador()).equals(equipe);
            default -> false;
        };
        return seVeem ? Set.copyOf(parceiros) : Set.of();
    }

    /** As cartas que aparecem no trecho, no formato do protocolo ou em texto. */
    static Set<Carta> mostradas(JsonNode trecho) {
        Set<Carta> cartas = new HashSet<>();
        String json = trecho.toString();
        Matcher noJson = CARTA_NO_JSON.matcher(json);
        while (noJson.find()) {
            cartas.add(new Carta(Valor.valueOf(noJson.group(1)), Naipe.valueOf(noJson.group(2))));
        }
        // Nos textos, pelo nome no baralho espanhol ("cavalo de ouros") ou pela notação com símbolo ("Q♦").
        for (Carta carta : BARALHO) {
            if (json.contains(Notacao.BARALHO_ESPANHOL.de(carta)) || json.contains(Notacao.SIMBOLOS.de(carta))) {
                cartas.add(carta);
            }
        }
        return cartas;
    }
}
