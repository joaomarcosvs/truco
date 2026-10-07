# CLAUDE.md — Truco (core)

> Leia este arquivo inteiro antes de qualquer tarefa. As regras do jogo estão em `docs/regras-truco-paulista.md`.

## 1. O que é este projeto

Um jogo de truco pensado para virar um jogo de verdade (estilo Steam). Arquitetura-alvo:

- **Servidor Java autoritativo**: contém as regras e decide tudo.
- **Cliente Unity (C#)**: só exibe o estado e envia as ações do jogador, por rede.

**Este repositório contém o core: as regras do jogo em Java puro.**
A variante da v1 é o **Truco Paulista**. Truco Mineiro e Gaudério virão muito mais adiante, mas o core já precisa estar preparado para eles (seção 7).

## 2. Fase atual: construir o core

Tudo o que for feito agora serve para entregar um core **completo, correto, testado e jogável por linha de comando**.

**Dentro do escopo agora**
- Regras do truco paulista, 1x1, no módulo `truco-core`
- Bot aleatório e CLI de desenvolvimento para jogar e testar
- Testes (unitários, de cenário e de propriedade)
- Módulo de protocolo (DTOs + JSON) no penúltimo marco

**Fora do escopo agora: não implemente nem crie esqueleto**
- Servidor, rede, WebSocket
- Cliente Unity e qualquer UI gráfica
- Contas, matchmaking, persistência
- 2x2 e as regras da seção 11 do documento de regras (apenas deixe o modelo preparado)
- Truco Mineiro e Gaudério (apenas respeite a seção 7)
- Bots avançados

Se uma tarefa parecer exigir algo fora do escopo, pare e pergunte.

## 3. Stack e build

- Java 21+ (records, sealed interfaces, `switch` com pattern matching)
- Maven multi-módulo, sempre pelo Maven Wrapper (`./mvnw`, Maven 3.9.16). Requer `JAVA_HOME` apontando para um JDK 21+.
- JUnit 5, AssertJ, jqwik (testes de propriedade)
- Comandos: `./mvnw -q test` · `./mvnw -q -pl truco-core test` · `./mvnw -q verify` · `./mvnw spotless:apply` (formata o código)
- groupId: `io.github.joaomarcosvs` · pacote base: `io.github.joaomarcosvs.truco` (bots em `...truco.bots`, CLI em `...truco.cli`)
- CI: GitHub Actions (`.github/workflows/ci.yml`) roda `./mvnw verify` a cada push na `main` e em pull requests

```
truco/
├─ truco-core/       regras do jogo (zero dependências de runtime)
├─ truco-bots/       estratégias de jogo (começa com BotAleatorio)
├─ truco-cli/        cliente de console para desenvolvimento
├─ truco-protocol/   DTOs + JSON (criado só no marco M9)
├─ docs/
└─ CLAUDE.md
```

Dependências entre módulos: `cli → bots → core` e `protocol → core`. **O core não depende de nada além do JDK.**
Futuros, fora deste repo ou deste escopo: `truco-server` (Java) e o cliente Unity.

## 4. Princípios inegociáveis

1. **Core puro.** Nada de Spring, Jackson, bibliotecas de log ou qualquer dependência de runtime em `truco-core`. Serialização fica em `truco-protocol`.
2. **Estado imutável e motor puro.** `aplicar(estado, jogador, acao)` devolve novo estado + eventos. Sem efeitos colaterais, sem I/O, sem relógio, sem `Math.random()`.
3. **Determinismo.** Toda aleatoriedade vem de uma seed guardada no estado; o embaralhamento de cada rodada (e a reposição do descarte) deriva de `(seed, numeroDaRodada)`. Mesma seed + mesmas ações = mesma partida.
4. **Informação oculta.** `VisaoDoJogador` e os eventos entregues a um jogador nunca vazam: cartas alheias, ordem do baralho, identidade de carta encoberta do adversário, nem **quem tem a carta da vez no descarte** (RG-DESC-10).
5. **Entrada inválida não lança exceção.** Ação ilegal retorna `Rejeitada(motivo)`. Exceções são só para bugs.
6. **Regras vivem na variante.** Nenhuma constante de regra (12 pontos, 40 cartas, 3 vazas, escada 1/3/6/9/12, ordem de força, manilha, sequência de descarte) fora de `...regras`. O motor pergunta à `VarianteDeRegras`.
7. **Nomes genéricos no motor.** Use `PedirAumento`, `EscadaDeApostas`, `Aposta`, não `PedirTruco` em código que servirá a outras variantes.

## 5. Modelo do domínio

**Vocabulário (use exatamente estes termos nos identificadores):**
- **Rodada** = distribuição completa, vale pontos, até 3 vazas. **Nunca use "mão" para isso.**
- **Vaza** = cada confronto em que cada jogador joga uma carta.
- **Carteador** = quem dá as cartas.
- **Rodada de Onze** e **Rodada Escurinho** (antiga "mão de ferro").

Pacotes sob o pacote base: `carta`, `regras`, `partida`, `acao`, `evento`, `visao`.

**Valores e entidades**
- `Naipe`, `Valor`, `Carta` (record), `Baralho` (composição definida pela variante)
- `JogadorId`, `EquipeId`, `Equipe` (v1: uma equipe por jogador, mas o modelo já suporta duplas)
- `ConfiguracaoDaPartida` (jogadores/equipes, variante, seed)
- `EstadoDaPartida`: placar, número da rodada, carteador, seed, rodada atual
- `Rodada`, `Vaza`, `Jogada`
- `FaseDaRodada` (sealed): `AguardandoDescarte(jogador)`, `AguardandoJogada(jogador)`, `AguardandoRespostaDeAumento(respondedor, nivelProposto)`, `DecisaoRodadaDeOnze(jogador)`, `RodadaFinalizada`, `PartidaFinalizada`

**API do motor**

```java
public interface MotorDeTruco {
    EstadoDaPartida novaPartida(ConfiguracaoDaPartida configuracao);
    List<Acao> acoesLegais(EstadoDaPartida estado, JogadorId jogador);
    Resultado aplicar(EstadoDaPartida estado, JogadorId jogador, Acao acao);
    VisaoDoJogador visaoDe(EstadoDaPartida estado, JogadorId jogador);
}

public sealed interface Resultado permits Aplicada, Rejeitada {}
public record Aplicada(EstadoDaPartida novoEstado, List<Evento> eventos) implements Resultado {}
public record Rejeitada(MotivoDeRejeicao motivo) implements Resultado {}

public sealed interface Acao permits JogarCarta, JogarEncoberta, Descartar, RecusarDescarte,
                                     PedirAumento, Aceitar, Correr {}
```

**Ações** (todas referenciam a carta pela posição na mão, `indiceNaMao`, porque na Rodada Escurinho o jogador joga sem ver)
- `JogarCarta(indiceNaMao)`, `JogarEncoberta(indiceNaMao)`
- `Descartar(indiceNaMao)`, `RecusarDescarte()`: só na fase de descarte, e só para quem tem a carta da vez.
- `PedirAumento()`: o nível é sempre o próximo da escada. Se houver pedido pendente, significa "aceito e aumento".
- `Aceitar()`: aceita um aumento ou, na Rodada de Onze, decide jogar.
- `Correr()`: recusa um aumento, desiste da rodada na própria vez ou, na Rodada de Onze, decide correr.

**Eventos** (sealed `Evento`, cada um com `Visibilidade`: `PUBLICO` ou `PRIVADO(jogador)`)
`RodadaIniciada`, `CartasDistribuidas` (privado), `CartaDescartada` (público, com a carta), `CartaRecebidaPorDescarte` (privado), `DescarteEncerrado` (público), `CartaJogada`, `CartaEncobertaJogada` (público, sem a identidade), `VazaFinalizada`, `AumentoPedido`, `AumentoAceito`, `JogadorCorreu`, `RodadaFinalizada`, `RodadaAnulada`, `PlacarAtualizado`, `PartidaFinalizada`, `RodadaDeOnzeIniciada`, `RodadaEscurinhoIniciada`.

**Bots:** `Estrategia { Acao decidir(VisaoDoJogador visao, List<Acao> legais); }`

## 6. Testes

- Cada regra de `docs/regras-truco-paulista.md` tem um ID (`RG-...`). **Todo teste de regra cita o ID** no `@DisplayName` (em propriedades jqwik, no `@Label`, porque o jqwik não lê `@DisplayName`).
- **Unitários** por regra, usando as tabelas e exemplos do documento de regras.
- **Cenário (golden):** seed fixa + sequência de ações → eventos e placar esperados.
- **Propriedade/fuzz (jqwik):** bots aleatórios jogam milhares de partidas e verifica-se que:
  - a partida sempre termina;
  - ações de `acoesLegais` são sempre aceitas e ações fora dela são sempre `Rejeitada`;
  - o valor da rodada está sempre na escada de apostas;
  - o placar nunca diminui;
  - **conservação de cartas:** mãos + baralho restante + vira + descartadas + jogadas sempre somam as 40 cartas, sem duplicatas;
  - `visaoDe(A)` e os eventos de A nunca contêm cartas de B.
- Bug corrigido = teste de regressão novo.

## 7. Preparação para Truco Mineiro e Gaudério (não implementar agora)

Princípio: **o motor é genérico; a variante é dado + estratégia.**

`VarianteDeRegras` é composta por interfaces pequenas:

| Peça | Responsabilidade |
|---|---|
| `ComposicaoDoBaralho` | quais cartas existem |
| `OrdemDeForca` | comparar duas cartas dado o contexto (vira ou nenhum) |
| `EscadaDeApostas` | níveis, valores, quem pode aumentar, valor ao correr |
| `RegrasDeVaza` | empates, carta encoberta, quem abre a seguinte |
| `RegrasDeDescarte` | sequência de descarte e quando é permitido |
| `RegrasEspeciais` | Rodada de Onze, Rodada Escurinho e afins |
| `PontuacaoDaPartida` | pontos para vencer |

`TrucoPaulista` é a primeira implementação, com `OpcoesTrucoPaulista` para as regras `[A CONFIRMAR]`.

Diferenças esperadas (a detalhar quando chegar a hora, **não implementar**):
- **Mineiro:** manilhas fixas (em vez de depender da vira), escada de apostas e pontuação próprias.
- **Gaudério:** ordem de força fixa e própria, canto de envido e flor antes das vazas, escada de apostas própria, partida com mais pontos.

O que isso exige do desenho de hoje:
- `Acao`, `Evento` e `FaseDaRodada` são sealed. Adicionar tipos específicos de variante no futuro (ex.: envido) é aceitável, mas **o fluxo genérico (vazas, placar, visibilidade) não deve precisar mudar**.
- Nada de `if (variante == ...)` no motor.
- O motor não assume que a rodada começa direto na primeira vaza: a fase de descarte já é uma fase anterior às vazas. Faça a transição de fases passar por **um ponto único** que a variante possa estender (o envido do gaudério entrará aí).

**Teste de prontidão (marco M10):** implementar uma variante mínima de teste (manilhas fixas) criando só uma nova `VarianteDeRegras`. Se for preciso alterar o motor, o desenho falhou: abra uma tarefa para corrigir.

## 8. Convenções

- Domínio em português, sem acento em identificadores: `Rodada`, `Vaza`, `Manilha`, `VarianteDeRegras`. Termos técnicos consagrados podem ficar em inglês.
- Documentação, Javadoc e commits em português. Commits no padrão Conventional Commits.
- Records para valores; classes `final`; sem getters/setters; sem `null` em APIs públicas; cópias defensivas (`List.copyOf`).
- Javadoc curto nos tipos públicos, citando o ID da regra quando existir.
- Formatação automática configurada no M1 e verificada no `mvn verify`.

## 9. Marcos (um de cada vez)

| # | Marco | Pronto quando |
|---|---|---|
| M1 | Fundação: Maven multi-módulo, Java 21, JUnit/AssertJ/jqwik, CI (`mvn verify`), formatador | build verde com 1 teste |
| M2 | Cartas e força: `Naipe`, `Valor`, `Carta`, baralho de 40, vira e manilha, comparação paulista | `RG-CARTAS-*` testadas |
| M3 | Rodada sem aumentos: carteador e ordem à direita, distribuir, jogar, vazas, empates, rodada anulada, `Resultado`, `acoesLegais`, `VisaoDoJogador` | `RG-PARTIDA-*`, `RG-VAZA-*`, `RG-EMP-*` testadas |
| M4 | Aumentos: truco/seis/nove/doze, aceitar, correr, pedir mais, direito de aumentar | `RG-AUM-*` testadas |
| M5 | Carta encoberta e correr na própria vez | `RG-ENC-*` testadas |
| M6 | Descarte: fase antes da 1ª vaza, sequência, reposição, visibilidade | `RG-DESC-*` e `RG-VIS-4` testadas |
| M7 | Partida completa: placar, Rodada de Onze, Rodada Escurinho, fim de partida | `RG-ONZE-*`, `RG-ESCURINHO-*`, `RG-FIM-*` testadas |
| M8 | Jogável: `truco-bots` (`BotAleatorio`) + `truco-cli` humano x bot + fuzz global | uma partida completa jogável no terminal |
| M9 | `truco-protocol`: DTOs e JSON de `Acao`, `Evento`, `VisaoDoJogador` | testes de ida e volta (round-trip) |
| M10 | Teste de prontidão de variante (seção 7) | variante mínima funciona sem mexer no motor |

## 10. Como trabalhar neste repo

1. Antes de implementar qualquer regra, leia o trecho correspondente em `docs/regras-truco-paulista.md`.
2. Regra marcada `[A CONFIRMAR]`: implemente o **padrão proposto** como opção configurável (`OpcoesTrucoPaulista`), documente e liste essas opções no resumo final. Não invente regras novas. Se a ambiguidade for central e não houver padrão proposto, pergunte.
3. Regra marcada `[FUTURO]`: não implemente.
4. Um marco por vez. Escreva os testes junto (ou antes) do código. Rode `mvn verify` antes de dizer que terminou.
5. Mudança na API pública do motor ou nos tipos sealed: **proponha antes de fazer**.
6. Ao concluir um marco, marque-o em "Status" abaixo e registre as decisões tomadas.

## 11. Status

- [x] M1 Fundação
- [x] M2 Cartas e força (RG-CARTAS-3 será testada no M3, junto com a distribuição)
- [ ] M3 Rodada sem aumentos
- [ ] M4 Aumentos
- [ ] M5 Carta encoberta e correr
- [ ] M6 Descarte
- [ ] M7 Partida completa
- [ ] M8 Jogável (bots + CLI)
- [ ] M9 Protocolo
- [ ] M10 Prontidão de variante

**Decisões registradas**
- **M1** Build: Maven Wrapper com Maven 3.9.16, `maven.compiler.release` 21, `-Xlint:all` com warnings tratados como erro e versões de plugins fixadas no POM pai.
- **M1** Testes: JUnit 5.14 (não o 6), porque o jqwik 1.10 roda sobre a JUnit Platform 1.14. JUnit, AssertJ e jqwik vêm do POM pai, em escopo `test`, para todos os módulos. Classes de teste terminam em `Test`, inclusive as de propriedade (padrão do Surefire).
- **M1** jqwik reporta só as propriedades que falharam e guarda as seeds de falhas em `target/` (`junit-platform.properties`).
- **M1** O enforcer barra no `truco-core` qualquer dependência fora do escopo `test` (princípio 1).
- **M1** Formatação: Spotless com palantir-java-format (até 120 colunas), checada no `verify`. Finais de linha LF (`.gitattributes` e `.editorconfig`), exceto `*.cmd`.
- **M1** RG-ENC-4 e RG-ONZE-3 confirmadas como `[DEFINIDA]` e serão regras fixas. Como não restou regra `[A CONFIRMAR]`, a `OpcoesTrucoPaulista` só será criada quando surgir uma.
- **M2** `Valor` tem os 13 valores do baralho francês e `Naipe` os 4 naipes. A ordem de declaração não é ordem de força (os naipes estão em ordem alfabética para que um `compareTo` acidental quebre os testes), e `Carta` não é `Comparable`. Quais cartas existem e a força de cada uma ficam em `regras`.
- **M2** `OrdemDeForca` expõe `forca(carta, vira)` (inteiro: maior vence, igual empata) e `comparar`, derivado dele. A vira é `Optional<Carta>`, vazia em variantes de manilhas fixas; no Truco Paulista, avaliar sem vira é erro de programação (`IllegalArgumentException`).
- **M2** A variante paulista fica em `regras.paulista`. `TrucoPaulista` é um record sem componentes (todas as instâncias são iguais), e as peças são package-private, acessadas pelas interfaces. `VarianteDeRegras` só tem as peças já usadas; as outras entram nos marcos que precisarem delas.
- **M2** A ordem de referência do baralho (A♦ A♠ A♥ A♣ 2♦ … J♣) é fixa e testada, porque o embaralhamento do M3 vai partir dela.
- **M2** O tipo `Baralho` (monte embaralhado, com compra) e o teste de RG-CARTAS-3 (a vira é virada depois da distribuição e não é jogada) ficam para o M3, que implementa a distribuição.
