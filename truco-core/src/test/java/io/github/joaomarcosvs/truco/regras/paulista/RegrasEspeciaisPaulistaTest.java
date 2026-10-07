package io.github.joaomarcosvs.truco.regras.paulista;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.Placar;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada.DeOnze;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada.Escurinho;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada.Normal;
import io.github.joaomarcosvs.truco.regras.RegrasEspeciais;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegrasEspeciaisPaulistaTest {

    private static final EquipeId ANA = new EquipeId("ana");
    private static final EquipeId BETO = new EquipeId("beto");

    private final RegrasEspeciais regras = new TrucoPaulista().regrasEspeciais();

    private TipoDeRodada tipo(int pontosDaAna, int pontosDoBeto) {
        return regras.tipoDaRodada(new Placar(Map.of(ANA, pontosDaAna, BETO, pontosDoBeto)));
    }

    @ParameterizedTest(name = "{0} a {1}")
    @CsvSource({"0, 0", "10, 10", "10, 0", "0, 10", "9, 10"})
    @DisplayName("RG-ONZE-1 e RG-ESCURINHO-1: sem ninguém com 11, a rodada é normal")
    void rodadaNormal(int pontosDaAna, int pontosDoBeto) {
        assertThat(tipo(pontosDaAna, pontosDoBeto)).isEqualTo(new Normal());
    }

    @Test
    @DisplayName("RG-ONZE-1: quando só um lado tem 11, é Rodada de Onze para ele, ainda sem decisão")
    void rodadaDeOnze() {
        assertThat(tipo(11, 8)).isEqualTo(new DeOnze(ANA, false));
        assertThat(tipo(8, 11)).isEqualTo(new DeOnze(BETO, false));
        assertThat(tipo(11, 0)).isEqualTo(new DeOnze(ANA, false));
        assertThat(tipo(10, 11)).isEqualTo(new DeOnze(BETO, false));
    }

    @Test
    @DisplayName("RG-ESCURINHO-1: quando os dois lados têm 11, é Rodada Escurinho")
    void rodadaEscurinho() {
        assertThat(tipo(11, 11)).isEqualTo(new Escurinho());
    }

    @ParameterizedTest(name = "{0}: aumento {1}, encoberta {2}, descarte {3}, mão visível {4}")
    @CsvSource({
        "normal,    true,  true,  true,  true",
        "de onze,   false, true,  true,  true",
        "escurinho, false, false, false, false"
    })
    @DisplayName(
            "RG-AUM-7, RG-ONZE-2, RG-ONZE-3, RG-ESCURINHO-1, RG-DESC-9 e RG-VIS-3: o que cada tipo de rodada permite")
    void permissoes(String nome, boolean aumento, boolean encoberta, boolean descarte, boolean maoVisivel) {
        TipoDeRodada tipo = switch (nome) {
            case "normal" -> new Normal();
            case "de onze" -> new DeOnze(ANA, true);
            case "escurinho" -> new Escurinho();
            default -> throw new IllegalArgumentException(nome);
        };

        assertThat(regras.permiteAumento(tipo)).isEqualTo(aumento);
        assertThat(regras.permiteEncoberta(tipo)).isEqualTo(encoberta);
        assertThat(regras.permiteDescarte(tipo)).isEqualTo(descarte);
        assertThat(regras.maoVisivel(tipo)).isEqualTo(maoVisivel);
    }

    @Test
    @DisplayName("RG-ONZE-2: na Rodada de Onze não há aumento, antes ou depois da decisão")
    void semAumentoNaRodadaDeOnze() {
        assertThat(regras.permiteAumento(new DeOnze(ANA, false))).isFalse();
        assertThat(regras.permiteAumento(new DeOnze(ANA, true))).isFalse();
    }

    @Test
    @DisplayName("RG-ONZE-1: jogando a Rodada de Onze ela vale 3; correndo, o adversário ganha 1")
    void valoresDaRodadaDeOnze() {
        assertThat(regras.valorAoJogarARodadaDeOnze()).isEqualTo(3);
        assertThat(regras.valorAoCorrerDaRodadaDeOnze()).isEqualTo(1);
    }
}
