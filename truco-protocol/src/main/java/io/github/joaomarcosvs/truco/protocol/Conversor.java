package io.github.joaomarcosvs.truco.protocol;

import static java.util.stream.Collectors.toMap;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.Descartar;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.JogarEncoberta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.AumentoAceito;
import io.github.joaomarcosvs.truco.evento.AumentoPedido;
import io.github.joaomarcosvs.truco.evento.CartaDescartada;
import io.github.joaomarcosvs.truco.evento.CartaEncobertaJogada;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.CartaRecebidaPorDescarte;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.DescarteEncerrado;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.JogadorCorreu;
import io.github.joaomarcosvs.truco.evento.PartidaFinalizada;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.RodadaAnulada;
import io.github.joaomarcosvs.truco.evento.RodadaDeOnzeIniciada;
import io.github.joaomarcosvs.truco.evento.RodadaEscurinhoIniciada;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.Aposta;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.PedidoDeAumento;
import io.github.joaomarcosvs.truco.partida.Placar;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;
import io.github.joaomarcosvs.truco.visao.JogadaVisivel;
import io.github.joaomarcosvs.truco.visao.VazaVisivel;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Converte o modelo nos DTOs do JSON e de volta. Os {@code switch} são exaustivos: um tipo novo de ação ou de evento no
 * core só compila depois de ganhar o seu DTO. Na volta, dado inválido (um id nulo, um placar negativo) faz o próprio
 * modelo recusar, com {@link NullPointerException} ou {@link IllegalArgumentException}.
 */
final class Conversor {

    private Conversor() {}

    static AcaoDto dto(Acao acao) {
        return switch (acao) {
            case JogarCarta(int indiceNaMao) -> new AcaoDto.JogarCarta(indiceNaMao);
            case JogarEncoberta(int indiceNaMao) -> new AcaoDto.JogarEncoberta(indiceNaMao);
            case Descartar(int indiceNaMao) -> new AcaoDto.Descartar(indiceNaMao);
            case RecusarDescarte recusa -> new AcaoDto.RecusarDescarte();
            case PedirAumento pedido -> new AcaoDto.PedirAumento();
            case Aceitar aceite -> new AcaoDto.Aceitar();
            case Correr corrida -> new AcaoDto.Correr();
        };
    }

    static Acao acao(AcaoDto dto) {
        return switch (dto) {
            case AcaoDto.JogarCarta(int indiceNaMao) -> new JogarCarta(indiceNaMao);
            case AcaoDto.JogarEncoberta(int indiceNaMao) -> new JogarEncoberta(indiceNaMao);
            case AcaoDto.Descartar(int indiceNaMao) -> new Descartar(indiceNaMao);
            case AcaoDto.RecusarDescarte recusa -> new RecusarDescarte();
            case AcaoDto.PedirAumento pedido -> new PedirAumento();
            case AcaoDto.Aceitar aceite -> new Aceitar();
            case AcaoDto.Correr corrida -> new Correr();
        };
    }

    static EventoDto dto(Evento evento) {
        return switch (evento) {
            case RodadaIniciada e ->
                new EventoDto.RodadaIniciada(
                        e.numeroDaRodada(),
                        id(e.carteador()),
                        e.valor(),
                        e.vira().map(Conversor::dto));
            case CartasDistribuidas e -> new EventoDto.CartasDistribuidas(id(e.jogador()), dtos(e.cartas()));
            case CartaDescartada e -> new EventoDto.CartaDescartada(id(e.jogador()), dto(e.carta()));
            case CartaRecebidaPorDescarte e -> new EventoDto.CartaRecebidaPorDescarte(id(e.jogador()), dto(e.carta()));
            case DescarteEncerrado e -> new EventoDto.DescarteEncerrado();
            case CartaJogada e -> new EventoDto.CartaJogada(id(e.jogador()), dto(e.carta()), e.numeroDaVaza());
            case CartaEncobertaJogada e -> new EventoDto.CartaEncobertaJogada(id(e.jogador()), e.numeroDaVaza());
            case VazaFinalizada e -> new EventoDto.VazaFinalizada(e.numeroDaVaza(), dto(e.resultado()));
            case AumentoPedido e -> new EventoDto.AumentoPedido(id(e.pedinte()), e.nivelProposto());
            case AumentoAceito e -> new EventoDto.AumentoAceito(id(e.jogador()), e.valor());
            case JogadorCorreu e -> new EventoDto.JogadorCorreu(id(e.jogador()));
            case RodadaFinalizada e ->
                new EventoDto.RodadaFinalizada(e.numeroDaRodada(), id(e.vencedora()), e.pontos());
            case RodadaAnulada e -> new EventoDto.RodadaAnulada(e.numeroDaRodada());
            case PlacarAtualizado e -> new EventoDto.PlacarAtualizado(dto(e.placar()));
            case PartidaFinalizada e -> new EventoDto.PartidaFinalizada(id(e.vencedora()), dto(e.placar()));
            case RodadaDeOnzeIniciada e -> new EventoDto.RodadaDeOnzeIniciada(id(e.equipeComOnze()));
            case RodadaEscurinhoIniciada e -> new EventoDto.RodadaEscurinhoIniciada();
        };
    }

    static Evento evento(EventoDto dto) {
        return switch (dto) {
            case EventoDto.RodadaIniciada e ->
                new RodadaIniciada(
                        e.numeroDaRodada(),
                        jogador(e.carteador()),
                        e.valor(),
                        e.vira().map(Conversor::carta));
            case EventoDto.CartasDistribuidas e -> new CartasDistribuidas(jogador(e.jogador()), cartas(e.cartas()));
            case EventoDto.CartaDescartada e -> new CartaDescartada(jogador(e.jogador()), carta(e.carta()));
            case EventoDto.CartaRecebidaPorDescarte e ->
                new CartaRecebidaPorDescarte(jogador(e.jogador()), carta(e.carta()));
            case EventoDto.DescarteEncerrado e -> new DescarteEncerrado();
            case EventoDto.CartaJogada e -> new CartaJogada(jogador(e.jogador()), carta(e.carta()), e.numeroDaVaza());
            case EventoDto.CartaEncobertaJogada e -> new CartaEncobertaJogada(jogador(e.jogador()), e.numeroDaVaza());
            case EventoDto.VazaFinalizada e -> new VazaFinalizada(e.numeroDaVaza(), resultado(e.resultado()));
            case EventoDto.AumentoPedido e -> new AumentoPedido(jogador(e.pedinte()), e.nivelProposto());
            case EventoDto.AumentoAceito e -> new AumentoAceito(jogador(e.jogador()), e.valor());
            case EventoDto.JogadorCorreu e -> new JogadorCorreu(jogador(e.jogador()));
            case EventoDto.RodadaFinalizada e ->
                new RodadaFinalizada(e.numeroDaRodada(), equipe(e.vencedora()), e.pontos());
            case EventoDto.RodadaAnulada e -> new RodadaAnulada(e.numeroDaRodada());
            case EventoDto.PlacarAtualizado e -> new PlacarAtualizado(placar(e.placar()));
            case EventoDto.PartidaFinalizada e -> new PartidaFinalizada(equipe(e.vencedora()), placar(e.placar()));
            case EventoDto.RodadaDeOnzeIniciada e -> new RodadaDeOnzeIniciada(equipe(e.equipeComOnze()));
            case EventoDto.RodadaEscurinhoIniciada e -> new RodadaEscurinhoIniciada();
        };
    }

    static VisaoDto dto(VisaoDoJogador visao) {
        return new VisaoDto(
                id(visao.jogador()),
                listar(visao.jogadoresNaOrdemDaMesa(), Conversor::id),
                listar(
                        visao.equipes(),
                        equipe -> new VisaoDto.Equipe(id(equipe.id()), listar(equipe.jogadores(), Conversor::id))),
                dto(visao.placar()),
                visao.numeroDaRodada(),
                id(visao.carteador()),
                dto(visao.tipoDaRodada()),
                dto(visao.aposta()),
                visao.vira().map(Conversor::dto),
                dtos(visao.descartadas()),
                visao.cartaDaVezNoDescarte().map(Conversor::dto),
                dtos(visao.mao()),
                ordenado(visao.cartasNaMao(), Conversor::id),
                listar(visao.vazas(), vaza -> new VisaoDto.Vaza(dtosDeJogadas(vaza.jogadas()), dto(vaza.resultado()))),
                dtosDeJogadas(visao.vazaAtual()),
                visao.vezDe().map(Conversor::id),
                visao.vencedoraDaPartida().map(Conversor::id));
    }

    static VisaoDoJogador visao(VisaoDto dto) {
        return new VisaoDoJogador(
                jogador(dto.jogador()),
                listar(dto.jogadoresNaOrdemDaMesa(), Conversor::jogador),
                listar(
                        dto.equipes(),
                        equipe -> new Equipe(equipe(equipe.id()), listar(equipe.jogadores(), Conversor::jogador))),
                placar(dto.placar()),
                dto.numeroDaRodada(),
                jogador(dto.carteador()),
                tipo(dto.tipoDaRodada()),
                aposta(dto.aposta()),
                dto.vira().map(Conversor::carta),
                cartas(dto.descartadas()),
                dto.cartaDaVezNoDescarte().map(Conversor::carta),
                cartas(dto.mao()),
                dto.cartasNaMao().entrySet().stream()
                        .collect(toMap(entrada -> jogador(entrada.getKey()), Map.Entry::getValue)),
                listar(dto.vazas(), vaza -> new VazaVisivel(jogadas(vaza.jogadas()), resultado(vaza.resultado()))),
                jogadas(dto.vazaAtual()),
                dto.vezDe().map(Conversor::jogador),
                dto.vencedoraDaPartida().map(Conversor::equipe));
    }

    private static TipoDeRodadaDto dto(TipoDeRodada tipo) {
        return switch (tipo) {
            case TipoDeRodada.Normal normal -> new TipoDeRodadaDto.Normal();
            case TipoDeRodada.DeOnze onze -> new TipoDeRodadaDto.DeOnze(id(onze.equipeComOnze()), onze.decidiuJogar());
            case TipoDeRodada.Escurinho escurinho -> new TipoDeRodadaDto.Escurinho();
        };
    }

    private static TipoDeRodada tipo(TipoDeRodadaDto dto) {
        return switch (dto) {
            case TipoDeRodadaDto.Normal normal -> new TipoDeRodada.Normal();
            case TipoDeRodadaDto.DeOnze onze ->
                new TipoDeRodada.DeOnze(equipe(onze.equipeComOnze()), onze.decidiuJogar());
            case TipoDeRodadaDto.Escurinho escurinho -> new TipoDeRodada.Escurinho();
        };
    }

    private static ResultadoDaVazaDto dto(ResultadoDaVaza resultado) {
        return switch (resultado) {
            case ResultadoDaVaza.Vencida vencida ->
                new ResultadoDaVazaDto.Vencida(id(vencida.jogador()), id(vencida.equipe()));
            case ResultadoDaVaza.Empatada empatada -> new ResultadoDaVazaDto.Empatada();
        };
    }

    private static ResultadoDaVaza resultado(ResultadoDaVazaDto dto) {
        return switch (dto) {
            case ResultadoDaVazaDto.Vencida vencida ->
                new ResultadoDaVaza.Vencida(jogador(vencida.jogador()), equipe(vencida.equipe()));
            case ResultadoDaVazaDto.Empatada empatada -> new ResultadoDaVaza.Empatada();
        };
    }

    private static VisaoDto.Aposta dto(Aposta aposta) {
        return new VisaoDto.Aposta(
                aposta.valor(),
                aposta.ultimaEquipeQueAceitou().map(Conversor::id),
                aposta.pedidoPendente()
                        .map(pedido -> new VisaoDto.PedidoDeAumento(
                                id(pedido.pedinte()), id(pedido.respondedor()), pedido.nivelProposto())));
    }

    private static Aposta aposta(VisaoDto.Aposta dto) {
        return new Aposta(
                dto.valor(),
                dto.ultimaEquipeQueAceitou().map(Conversor::equipe),
                dto.pedidoPendente()
                        .map(pedido -> new PedidoDeAumento(
                                jogador(pedido.pedinte()), jogador(pedido.respondedor()), pedido.nivelProposto())));
    }

    private static List<VisaoDto.Jogada> dtosDeJogadas(List<JogadaVisivel> jogadas) {
        return listar(
                jogadas,
                jogada -> new VisaoDto.Jogada(
                        id(jogada.jogador()), jogada.carta().map(Conversor::dto), jogada.encoberta()));
    }

    private static List<JogadaVisivel> jogadas(List<VisaoDto.Jogada> dtos) {
        return listar(
                dtos,
                jogada -> new JogadaVisivel(
                        jogador(jogada.jogador()), jogada.carta().map(Conversor::carta), jogada.encoberta()));
    }

    /** O placar como objeto JSON, com as equipes em ordem alfabética, para o JSON sair sempre igual. */
    private static Map<String, Integer> dto(Placar placar) {
        return ordenado(placar.pontos(), Conversor::id);
    }

    private static Placar placar(Map<String, Integer> dto) {
        return new Placar(
                dto.entrySet().stream().collect(toMap(entrada -> equipe(entrada.getKey()), Map.Entry::getValue)));
    }

    private static <K> Map<String, Integer> ordenado(Map<K, Integer> mapa, Function<K, String> chave) {
        Map<String, Integer> ordenado = new TreeMap<>();
        mapa.forEach((k, valor) -> ordenado.put(chave.apply(k), valor));
        return ordenado;
    }

    private static CartaDto dto(Carta carta) {
        return new CartaDto(carta.valor(), carta.naipe());
    }

    private static Carta carta(CartaDto dto) {
        return new Carta(dto.valor(), dto.naipe());
    }

    private static List<CartaDto> dtos(List<Carta> cartas) {
        return listar(cartas, Conversor::dto);
    }

    private static List<Carta> cartas(List<CartaDto> dtos) {
        return listar(dtos, Conversor::carta);
    }

    private static String id(JogadorId jogador) {
        return jogador.valor();
    }

    private static String id(EquipeId equipe) {
        return equipe.valor();
    }

    private static JogadorId jogador(String id) {
        return new JogadorId(id);
    }

    private static EquipeId equipe(String id) {
        return new EquipeId(id);
    }

    private static <A, B> List<B> listar(List<A> lista, Function<A, B> conversao) {
        return lista.stream().map(conversao).toList();
    }
}
