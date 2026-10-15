package io.github.joaomarcosvs.truco.web;

import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.MOTOR;
import static io.github.joaomarcosvs.truco.web.PartidaReconstituida.VOCE;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.web.PartidaReconstituida.Reconstituida;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contra o bot, a resposta da web leva, além do protocolo, os textos da CLI. Nada nela (visões, eventos, textos e ações)
 * pode mostrar as cartas do bot, as do baralho ou as encobertas do bot, nem a sua própria mão na Rodada Escurinho.
 */
class InformacaoOcultaNaWebTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Property(tries = 60)
    @Label("RG-VIS-1 a RG-VIS-3: em partidas inteiras com escolhas aleatórias, a resposta da web nunca mostra uma carta"
            + " oculta para você")
    void nadaVaza(@ForAll long seed, @ForAll long sementeDasEscolhas) {
        Random escolhas = new Random(sementeDasEscolhas);
        List<Acao> jogadas = new ArrayList<>();
        while (true) {
            Reconstituida partida = (Reconstituida) PartidaReconstituida.reconstituir(seed, jogadas);
            JsonNode resposta = JSON.readTree(RespostaJson.partida(seed, partida));
            CartasOcultas.conferirPassos(VOCE, partida.passos(), resposta.get("passos"));
            CartasOcultas.conferirAcoes(VOCE, partida.estado(), resposta.get("acoesLegais"));
            if (partida.estado().rodada().fase() instanceof FaseDaRodada.PartidaFinalizada) {
                return;
            }
            List<Acao> legais = MOTOR.acoesLegais(partida.estado(), VOCE);
            jogadas.add(legais.get(escolhas.nextInt(legais.size())));
        }
    }
}
