package io.github.joaomarcosvs.truco.regras.paulista;

import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.Placar;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada.DeOnze;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada.Escurinho;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada.Normal;
import io.github.joaomarcosvs.truco.regras.RegrasEspeciais;
import java.util.List;
import java.util.Map;

/** Rodada de Onze e Rodada Escurinho do Truco Paulista (RG-ONZE-*, RG-ESCURINHO-1). */
final class RegrasEspeciaisPaulista implements RegrasEspeciais {

    /** Os pontos que tornam a rodada especial. */
    private static final int ONZE = 11;

    @Override
    public TipoDeRodada tipoDaRodada(Placar placar) {
        List<EquipeId> comOnze = placar.pontos().entrySet().stream()
                .filter(entrada -> entrada.getValue() == ONZE)
                .map(Map.Entry::getKey)
                .toList();
        if (comOnze.size() == 1) {
            return new DeOnze(comOnze.getFirst(), false); // RG-ONZE-1: só um lado tem 11
        }
        if (comOnze.size() > 1) {
            return new Escurinho(); // RG-ESCURINHO-1: os dois lados têm 11
        }
        return new Normal();
    }

    @Override
    public boolean permiteAumento(TipoDeRodada tipo) {
        return tipo instanceof Normal; // RG-AUM-7, RG-ONZE-2 e RG-ESCURINHO-1
    }

    @Override
    public boolean permiteEncoberta(TipoDeRodada tipo) {
        return !(tipo instanceof Escurinho); // RG-ESCURINHO-1
    }

    @Override
    public boolean permiteDescarte(TipoDeRodada tipo) {
        return !(tipo instanceof Escurinho); // RG-DESC-9; na Rodada de Onze há descarte (RG-ONZE-3)
    }

    @Override
    public boolean maoVisivel(TipoDeRodada tipo) {
        return !(tipo instanceof Escurinho); // RG-ESCURINHO-1 e RG-VIS-3
    }

    @Override
    public int valorAoJogarARodadaDeOnze() {
        return 3; // RG-ONZE-1 e RG-ONZE-2
    }

    @Override
    public int valorAoCorrerDaRodadaDeOnze() {
        return 1; // RG-ONZE-1
    }
}
