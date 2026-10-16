# CLAUDE.md — Truco (core)

> Leia este arquivo inteiro antes de qualquer tarefa. As regras do jogo estão em `docs/regras-truco-paulista.md`; o formato JSON entre servidor e clientes, em `docs/protocolo.md`.

## 1. O que é este projeto

Um jogo de truco pensado para virar um jogo de verdade (estilo Steam). Arquitetura-alvo:

- **Servidor Java autoritativo**: contém as regras e decide tudo.
- **Cliente Unity (C#)**: só exibe o estado e envia as ações do jogador, por rede.

**Este repositório contém o core: as regras do jogo em Java puro.**
A variante da v1 é o **Truco Paulista**. Truco Mineiro e Gaudério virão muito mais adiante, mas o core já precisa estar preparado para eles (seção 7).

## 2. Fase atual: construir o core

Tudo o que for feito agora serve para entregar um core **completo, correto, testado e jogável por linha de comando**.

**Dentro do escopo agora**
- Regras do truco paulista, 1x1 e 2x2, no módulo `truco-core`
- Bot aleatório e CLI de desenvolvimento para jogar e testar
- Testes (unitários, de cenário e de propriedade)
- Módulo de protocolo (DTOs + JSON) no penúltimo marco
- Na branch `feat/frontend-web`: um frontend web **de teste** (`truco-web`) para jogar no navegador, contra o bot ou contra um amigo numa sala com código (só na memória do servidor), e publicar de graça no Render (`docs/frontend-web.md`). É uma ferramenta de desenvolvimento, como a CLI, e não o servidor de verdade: o que ele faz com salas não substitui o `truco-server` futuro.

**Fora do escopo agora: não implemente nem crie esqueleto**
- Servidor, rede, WebSocket
- Cliente Unity e qualquer UI gráfica
- Contas, matchmaking, persistência
- Truco Mineiro e Gaudério (apenas respeite a seção 7)
- Bots avançados

Se uma tarefa parecer exigir algo fora do escopo, pare e pergunte.

## 3. Stack e build

- Java 21+ (records, sealed interfaces, `switch` com pattern matching)
- Maven multi-módulo, sempre pelo Maven Wrapper (`./mvnw`, Maven 3.9.16). Requer `JAVA_HOME` apontando para um JDK 21+.
- JUnit 5, AssertJ, jqwik (testes de propriedade)
- Comandos: `./mvnw -q test` · `./mvnw -q -pl truco-core test` · `./mvnw -q verify` · `./mvnw spotless:apply` (formata o código)
- Jogar contra o bot: `./mvnw -q package -DskipTests` e depois `java -jar truco-cli/target/truco-cli.jar [--seed N] [--letras] [--2x2]` (com `--2x2`, você e um bot de parceiro contra dois bots)
- No navegador: `java -jar truco-web/target/truco-web.jar` e abrir http://localhost:8080
- groupId: `io.github.joaomarcosvs` · pacote base: `io.github.joaomarcosvs.truco` (bots em `...truco.bots`, CLI em `...truco.cli`)
- CI: GitHub Actions (`.github/workflows/ci.yml`) roda `./mvnw verify` a cada push na `main` e em pull requests

```
truco/
├─ truco-core/       regras do jogo (zero dependências de runtime)
├─ truco-bots/       estratégias de jogo (começa com BotAleatorio)
├─ truco-cli/        cliente de console para desenvolvimento
├─ truco-protocol/   DTOs + JSON (criado só no marco M9)
├─ truco-web/        frontend web de teste (branch feat/frontend-web), com Dockerfile e render.yaml na raiz
├─ docs/
└─ CLAUDE.md
```

Dependências entre módulos: `cli → bots → core`, `protocol → core` e `web → cli, protocol`. **O core não depende de nada além do JDK.**
Futuros, fora deste repo ou deste escopo: `truco-server` (Java) e o cliente Unity.

## 4. Princípios inegociáveis

1. **Core puro.** Nada de Spring, Jackson, bibliotecas de log ou qualquer dependência de runtime em `truco-core`. Serialização fica em `truco-protocol`.
2. **Estado imutável e motor puro.** `aplicar(estado, jogador, acao)` devolve novo estado + eventos. Sem efeitos colaterais, sem I/O, sem relógio, sem `Math.random()`.
3. **Determinismo.** Toda aleatoriedade vem de uma seed guardada no estado; o embaralhamento de cada rodada (e a reposição do descarte) deriva de `(seed, numeroDaRodada)`. Mesma seed + mesmas ações = mesma partida.
4. **Informação oculta.** `VisaoDoJogador` e os eventos entregues a um jogador nunca vazam: cartas alheias, ordem do baralho, identidade de carta encoberta do adversário, nem **quem tem a carta da vez no descarte** (RG-DESC-10). A única exceção é a mão do parceiro, no 2x2, nos momentos em que a dupla se vê (RG-2X2-10 e RG-2X2-11).
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
- `JogadorId`, `EquipeId`, `Equipe` (um jogador no 1x1, uma dupla no 2x2)
- `ConfiguracaoDaPartida` (jogadores/equipes, variante, seed)
- `EstadoDaPartida`: placar, número da rodada, carteador, seed, rodada atual
- `Rodada`, `Vaza`, `Jogada`
- `TipoDeRodada` (sealed): `Normal`, `DeOnze(equipeComOnze, decidiuJogar)`, `Escurinho`. Decidido pelo placar no começo da rodada.
- `FaseDaRodada` (sealed): `AguardandoDescarte(cartaDaVez)`, `AguardandoJogada(jogador)`, `AguardandoRespostaDeAumento(respondedor, nivelProposto)`, `DecisaoRodadaDeOnze(jogador)`, `PartidaFinalizada(vencedora)`. O fim de uma rodada não é fase: é o evento `RodadaFinalizada`, e o mesmo `aplicar` já distribui a rodada seguinte.

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
                                     PedirAumento, Aceitar, Correr, VerCartasDoParceiro {}
```

**Ações** (todas referenciam a carta pela posição na mão, `indiceNaMao`, porque na Rodada Escurinho o jogador joga sem ver)
- `JogarCarta(indiceNaMao)`, `JogarEncoberta(indiceNaMao)`
- `Descartar(indiceNaMao)`, `RecusarDescarte()`: só na fase de descarte. Todos respondem a cada carta da vez: só o dono dela pode `Descartar`, e para os outros `RecusarDescarte` é passar. O passo só se resolve quando todos responderam (RG-DESC-10).
- `PedirAumento()`: o nível é sempre o próximo da escada. Se houver pedido pendente, significa "aceito e aumento".
- `Aceitar()`: aceita um aumento ou, na Rodada de Onze, decide jogar.
- `Correr()`: recusa um aumento ou, na Rodada de Onze, decide não jogar. Na própria vez não se corre (RG-AUM-6).
- `VerCartasDoParceiro()`: no 2x2, com um pedido de aumento pendente, quem o recebeu e o parceiro querem se ver; quando todos querem, eles se veem até a resposta (RG-2X2-9, RG-2X2-10). É opcional: a partida não espera por ela.

**Eventos** (sealed `Evento`, cada um com `Visibilidade`: `PUBLICO` ou `PRIVADO(jogador)`)
`RodadaIniciada`, `CartasDistribuidas` (privado), `CartaDescartada` (público, com a carta), `CartaRecebidaPorDescarte` (privado), `DescarteEncerrado` (público), `CartaJogada`, `CartaEncobertaJogada` (público, sem a identidade), `VazaFinalizada`, `AumentoPedido`, `AumentoAceito`, `JogadorCorreu`, `RodadaFinalizada`, `RodadaAnulada`, `PlacarAtualizado`, `PartidaFinalizada`, `RodadaDeOnzeIniciada`, `RodadaEscurinhoIniciada` e, no 2x2, `QuisVerAsCartas`, `DuplaViuAsCartas` (públicos) e `CartasDoParceiroVistas` (privado).

**Bots:** `Estrategia { Acao decidir(VisaoDoJogador visao, List<Acao> legais); }`, e `decidirOpcional`, que pode não fazer nada, para as ações opcionais.

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
| `RegrasDeDupla` | formatos aceitos (1x1, 2x2) e o que os parceiros veem um do outro |

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
| M11 | 2x2: duplas na mesa, empate entre parceiros, ver as cartas do parceiro, Rodada de Onze em duplas, CLI `--2x2` | `RG-2X2-*` testadas |

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
- [x] M3 Rodada sem aumentos (vencer com 12 pontos, de RG-PARTIDA-1, será testado no M7; correr, de RG-PARTIDA-5, no M4/M5)
- [x] M4 Aumentos (a RG-AUM-6, sobre correr, entra no M5; as exceções de RG-AUM-7 e "rodada de 12 vence a partida", de RG-AUM-1, no M7)
- [x] M5 Carta encoberta e correr (a proibição de encoberta na Rodada Escurinho entra no M7)
- [x] M6 Descarte (RG-DESC-9, sem descarte na Rodada Escurinho, e o descarte na Rodada de Onze entram no M7)
- [x] M7 Partida completa
- [x] M8 Jogável (bots + CLI)
- [x] M9 Protocolo
- [x] M10 Prontidão de variante
- [x] M11 2x2

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
- **M3** `FaseDaRodada` não tem `RodadaFinalizada` (aprovado): como nenhuma ação leva à rodada seguinte, o `aplicar` que encerra uma rodada já distribui a próxima e devolve os eventos das duas. Os tipos sealed crescem a cada marco: hoje `Acao` só tem `JogarCarta`, `FaseDaRodada` só `AguardandoJogada`, e `Evento` tem os 7 eventos da rodada sem aumentos.
- **M3** Embaralhamento em `partida.Sorteio`: Fisher–Yates com números de SHA-256 sobre (finalidade, seed, rodada, contador); o carteador inicial sai do mesmo gerador. Dá as mesmas cartas em qualquer JVM, e ver algumas cartas não ajuda a prever as outras. O resultado da seed 2026 está fixado em teste.
- **M3** Distribuição: uma carta por vez, a começar pelo jogador à direita do carteador; a vira é a carta seguinte. Não foi preciso um tipo `Baralho`: o monte é `Rodada.baralhoRestante`, já embaralhado.
- **M3** `indiceNaMao` é a posição na mão atual: ao jogar uma carta, as seguintes sobem uma posição. `aplicar` rejeita tudo o que não está em `acoesLegais` (`JOGADOR_DESCONHECIDO`, `NAO_E_A_VEZ_DO_JOGADOR` ou `ACAO_INVALIDA`); configuração inválida e `visaoDe` de quem não joga são erros de programação (`IllegalArgumentException`).
- **M3** Todas as mudanças de fase passam por `MotorGenerico.proximaFase`, o ponto que o descarte (M6) e o envido do gaudério vão estender. `novaPartida` só aceita 1x1 (RG-ESC-1), devolve o estado com a 1ª rodada já distribuída (sem eventos) e o motor se obtém com `MotorDeTruco.novo()`.
- **M3** Os pacotes `partida`, `regras`, `evento` e `visao` se referenciam (as regras usam `Jogada` e `Vaza`; a partida usa a variante). É intencional, por ser um módulo só.
- **M4** `Aposta` (valor, equipe que aceitou o último aumento e pedido pendente) substitui o `valor` da `Rodada` e vai inteira na `VisaoDoJogador`, porque é pública. A fase `AguardandoRespostaDeAumento(respondedor, nivelProposto)` sai do pedido pendente, em `proximaFase`. `Acao` ganhou `PedirAumento`, `Aceitar` e `Correr`; `Evento` ganhou `AumentoPedido`, `AumentoAceito` e `JogadorCorreu`.
- **M4** `EscadaDeApostas` tem `proximoNivel`, `valorAoCorrer` e `podeAumentar` (teto e direito de aumentar). O motor só registra quem aceitou; a regra de quem pode pedir fica na escada.
- **M4** Responde a um pedido o adversário à direita de quem pediu; num "pedir mais", responde quem tinha pedido, e o motor emite `AumentoAceito` e `AumentoPedido`. Aceito o aumento, a vez volta para quem ia jogar, que pode pedir o nível seguinte na hora se tiver o direito. `Correr` por enquanto só responde a um pedido.
- **M5** `Jogada` ganhou `encoberta` (fábricas `Jogada.aberta` e `Jogada.encoberta`), e o estado do servidor guarda a carta encoberta. `Acao` ganhou `JogarEncoberta`; `Evento` ganhou `CartaEncobertaJogada`, sem a carta. `RegrasDeVaza.permiteEncoberta(numeroDaVaza)` libera a encoberta da 2ª vaza em diante, e o resultado da vaza só compara as cartas abertas (todas encobertas: empate).
- **M5** A `VisaoDoJogador` não expõe mais `Vaza` e `Jogada` do modelo: usa `VazaVisivel` e `JogadaVisivel(jogador, Optional<Carta>, encoberta)`, e a carta encoberta só aparece para quem a jogou, inclusive depois da rodada.
- **M5** ~~`Correr` também vale na própria vez~~: revogada depois do M8, ver a correção da RG-AUM-6 abaixo.
- **M6** Descarte pela opção A (aprovada): a cada carta da vez, todos respondem, e o passo só se resolve quando todos responderam. Assim, para quem não tem a carta, "o dono recusou" e "ninguém tinha" geram as mesmas visões, ações e eventos (há teste disso). Esconder o tempo de resposta fica com o servidor (por exemplo, uma janela igual para todos).
- **M6** `FaseDaRodada.AguardandoDescarte(cartaDaVez)`; `Acao` ganhou `Descartar` e `RecusarDescarte`; `Evento` ganhou `CartaDescartada` (pública, com quem descartou), `CartaRecebidaPorDescarte` (privada) e `DescarteEncerrado`. A `Rodada` guarda um `Descarte` (descartadas, se acabou, quem já respondeu e se o dono descarta), e a visão mostra as descartadas e a carta da vez, com `vezDe` vazio no descarte.
- **M6** `RegrasDeDescarte.sequencia(vira)` dá a sequência (vazia = sem descarte); a carta da vez é a seguinte depois das descartadas. A carta comprada sai do topo do baralho restante e fica na posição da descartada. Sem baralho restante, ninguém pode descartar (caso-limite que as regras não cobrem).
- **M6** O descarte entra em `proximaFase` antes de tudo (RG-DESC-2), e toda mudança de estado da rodada passa por `MotorGenerico.comRodada`, que recalcula a fase.
- **M7** A `VarianteDeRegras` ganhou duas peças. `RegrasEspeciais` diz o tipo da rodada pelo placar, o que cada tipo permite (aumento, encoberta, descarte, mão visível) e os valores da Rodada de Onze (3 ao jogar, 1 ao correr). `PontuacaoDaPartida` dá os pontos para vencer (12). O `TipoDeRodada` fica na `Rodada` e na `VisaoDoJogador`. `Evento` ganhou `RodadaDeOnzeIniciada`, `RodadaEscurinhoIniciada` e `PartidaFinalizada(vencedora, placar)`.
- **M7** Ordem em `proximaFase`: fim da partida, descarte, `DecisaoRodadaDeOnze`, pedido pendente e, por último, as vazas. Assim, na Rodada de Onze, o descarte vem antes da decisão (RG-ONZE-3). Decide quem tem 11, mesmo que não seja quem abre a 1ª vaza. `Aceitar` (jogar) emite `AumentoAceito(jogador, 3)`, e `Correr` dá 1 ponto ao adversário.
- **M7** ~~Suposição de que se corre na própria vez depois de decidir jogar a Onze e na Escurinho~~: revogada com a correção da RG-AUM-6 abaixo.
- **M7** Fim de partida: quando um lado chega aos pontos para vencer, o `aplicar` emite `PartidaFinalizada` depois de `PlacarAtualizado` e não distribui outra rodada. O estado guarda a rodada final, com a última vaza, na fase `PartidaFinalizada(vencedora)`. Depois disso, `acoesLegais` volta vazia, `aplicar` devolve `Rejeitada(PARTIDA_FINALIZADA)` (motivo novo) e a visão mostra `vencedoraDaPartida`.
- **M7** Na Escurinho não há `CartasDistribuidas`, e a visão traz a `mao` vazia (só `cartasNaMao`). O jogador escolhe a carta pela posição, e a carta jogada é revelada a todos.
- **M7** Testes de partida inteira: propriedades (a partida sempre termina, as restrições da Onze e da Escurinho valem, nada vaza, nem a própria mão na Escurinho) e um golden de partida completa (seed 2026) que passa pela Rodada de Onze e pela Escurinho.
- **M8** `BotAleatorio(seed)` escolhe entre as ações legais, todas com a mesma chance, usando um `java.util.Random`. O algoritmo do `Random` é fixo na especificação do Java, então a mesma seed dá as mesmas escolhas em qualquer JVM. Como adversário ele é fraco, porque joga ao acaso e aceita ou corre de aumentos sem critério. É de propósito: bots melhores estão fora do escopo, e o uniforme passa por todas as ações no fuzz.
- **M8** O `Arbitro` (em `truco-bots`) conduz uma partida local. A cada passo, o primeiro jogador da mesa com ações legais recebe a própria visão e escolhe; uma ação fora das legais é bug da estratégia (`IllegalStateException`). A cada ação aplicada, ele avisa um `ObservadorDaPartida`. A CLI e o fuzz usam o árbitro; o servidor terá o próprio laço.
- **M8** O fuzz global (`FuzzGlobalTest`, em `truco-bots`) usa só a API pública e roda dois lotes de partidas de `BotAleatorio` contra `BotAleatorio`:
  - 2.000 partidas conferem que toda partida termina, que as ações da escada valem, que o placar nunca diminui, que as 40 cartas se conservam e, a cada passo, que uma ação sorteada é aceita se e só se for legal;
  - 300 partidas conferem a visão e os eventos de cada bot.
  Os vazamentos que atravessam rodadas continuam cobertos pelas propriedades do core.
- **M8** A `truco-cli` tem quatro peças:
  - `TrucoCli` lê `--seed N`, `--letras` e `--ajuda`. Sem `--seed`, sorteia uma seed e a mostra; o bot usa a mesma seed. O código de saída é 0 quando a partida termina, 1 quando é interrompida ("sair" ou fim da entrada) e 2 com opções inválidas.
  - `JogadorHumano` é uma `Estrategia` de console: mostra um menu numerado só com as ações legais e passa sozinho no descarte quando a única opção é passar.
  - `Narrador` descreve só os eventos `visivelPara` você, com `switch` exaustivo.
  - `Tela` desenha a mesa a partir da `VisaoDoJogador`.

  As cartas aparecem com os símbolos de naipe se o charset do terminal os codifica; senão, com letras (c, e, o, p).
- **M8** O jar executável sai do `maven-shade-plugin` 3.6.2, em `truco-cli/target/truco-cli.jar`. Ele é separado do jar do módulo: se o substituísse, um build sem `clean` reprocessaria o próprio resultado. Só o manifesto da CLI entra no jar.
- **Correção da RG-AUM-6 (depois do M8)** O documento de regras dizia que dava para correr na própria vez, e o motor fazia isso. Não dá: só se corre respondendo a um pedido de aumento ou, na Rodada de Onze, ao decidir não jogar. Quem acha as cartas fracas tem que jogá-las. Por isso:
  - `Correr` saiu das ações legais da vez de jogar;
  - `EscadaDeApostas.valorAoDesistir` foi removida;
  - depois de decidir jogar a Onze, e na Escurinho, ninguém corre, porque nelas não há aumento.

  Uma propriedade confere a regra em partidas inteiras.
- **M9** O `truco-protocol` (pacote `...truco.protocol`) depende do core e do Jackson 3.2.3 (`tools.jackson.core:jackson-databind`).
  - A API pública é `ProtocoloJson` (escrever e ler ação, evento e visão) e `Leitura` (`Lida` ou `Invalida(motivo)`). A leitura nunca lança exceção, porque o servidor vai ler o que os clientes mandam.
  - Os DTOs são internos ao módulo e espelham o modelo. A conversão de ida e volta usa `switch` exaustivo, então um tipo novo no core não compila sem o seu DTO.
  - O contrato é o formato JSON, documentado em `docs/protocolo.md`. O estado completo da partida nunca vira JSON, e a visibilidade dos eventos não vai no JSON: o servidor filtra antes de enviar.
- **M9** As regras do formato:
  - os campos têm os nomes e a ordem dos records do modelo;
  - ações, eventos, tipo de rodada e resultado da vaza levam `"tipo"`, com o nome da variante;
  - a carta é `{"valor":"AS","naipe":"COPAS"}`, só em ASCII;
  - valores ausentes saem como `null`, e todo tipo tem sempre os mesmos campos;
  - placar e cartas na mão são objetos com as chaves em ordem alfabética, para o mesmo valor dar sempre o mesmo JSON;
  - a leitura é estrita: campo desconhecido ou faltando, número como texto ou fração e texto depois do JSON tornam a mensagem inválida.
- **M9** Os exemplos de `docs/protocolo.md` são os goldens. O `DocumentacaoDoProtocoloTest` lê o documento e confere que cada exemplo marcado é válido, volta igual e tem os campos do record do modelo, na mesma ordem, e que os exemplos cobrem todas as ações e todos os eventos. Além disso:
  - uma propriedade faz ida e volta com toda ação legal, todo evento e toda visão de 200 partidas aleatórias inteiras;
  - outro teste cobre as entradas inválidas.
- **M9** Ficam para o servidor o envelope das mensagens de rede (tipo da mensagem, id da partida, versão do protocolo) e a resposta a uma ação rejeitada (`MotivoDeRejeicao`).
- **M10** O motor passou no teste de prontidão sem nenhuma mudança: a variante de teste `ManilhasFixas` é só uma nova `VarianteDeRegras`. Ela fica em `truco-core/src/test`, no pacote `regras.manilhasfixas`, e tem:
  - manilhas fixas (4♣ > 7♥ > A♠ > 7♦, como no Mineiro);
  - nenhuma vira (`usaVira` falso) e nenhum descarte (sequência vazia);
  - o resto do paulista, reaproveitado pelas interfaces: baralho, escada, rodadas especiais e pontos.

  O `ProntidaoDeVarianteTest` joga com ela em cenários exatos e em 200 partidas aleatórias inteiras: elas terminam, sem vira nem descarte, com as 40 cartas e só com ações legais. Testes de mutação confirmam que um motor que presumisse vira ou descarte falharia. O `Cenario` dos testes ganhou `semVira(variante)`.
- **M10** Observação para quando o Mineiro vier: as regras de vaza do paulista recebem a ordem de força, mas a classe é package-private. Para reaproveitá-las com outra ordem, a variante de teste delega ao paulista quantas cartas, encoberta, quem abre e desfecho, e reescreve só a comparação das cartas (`RegrasDeVaza.resultado`). Vale tornar essa comparação reutilizável, por exemplo com uma fábrica pública de regras de vaza que receba a `OrdemDeForca`, em vez de copiá-la.
- **Frontend web (branch `feat/frontend-web`)** O `truco-web` é uma página para jogar no navegador, contra o bot ou contra um amigo; o guia está em `docs/frontend-web.md`.
  - O servidor é HTTP, só com o JDK (`com.sun.net.httpserver`), e **não guarda nada**. A página manda a seed e as suas jogadas a cada pedido, e `PartidaReconstituida` refaz a partida, o que funciona porque o core e o bot são determinísticos. Assim a partida sobrevive ao servidor gratuito dormir ou reiniciar.
  - A resposta traz só a sua visão e os eventos que você pode ver, no formato do protocolo, com o texto da CLI em cada evento e ação. Por isso `Narrador`, `Tela` e `Notacao` da CLI ficaram públicos.
  - A página (HTML, CSS e JS, sem build) só desenha e manda jogadas, sem nenhuma regra. No descarte, quando a única opção é passar, o servidor passa sozinho, como a CLI.
  - A hospedagem gratuita é no Render, com o `Dockerfile` e o `render.yaml` da raiz.
  - Os testes cobrem a reconstituição, o servidor por HTTP (página, API, erros, partida inteira) e uma propriedade de que a resposta, inclusive os textos, nunca mostra uma carta oculta.
  - A página mostra o baralho espanhol, só na aparência: paus vira bastos, e 10, Q e J viram sota, cavalo e rei, o que mantém a ordem de força. Os textos usam a `Notacao.BARALHO_ESPANHOL` ("cavalo de ouros").
  - As cartas são a folha "Baraja española completa" do Wikimedia Commons, sob CC BY-SA 3.0, sem alterações e usada como sprite CSS. O crédito fica no rodapé da página, no `cartas.js` e em `docs/frontend-web.md`. A imagem vai com cache longo; a página e os scripts, com `no-store`.
- **Frontend web: contra um amigo** A página abre num menu: contra o bot, criar sala ou entrar numa sala. O core não mudou.
  - `Sala` guarda na memória uma partida entre os lugares da mesa (ver o 2x2 abaixo), com a seed sorteada por `SecureRandom`, que nunca sai do servidor, porque com ela daria para calcular as cartas de todos. `Salas` cria as salas (código de 6 caracteres sem 0, O, 1, I e L), dá a cada jogador um token de 128 bits, aceita até 100 salas e fecha as que passam 2 horas sem jogadas ou 30 minutos depois do fim. O relógio é injetado nos testes.
  - A página pergunta a cada segundo o que mudou (`GET /api/salas/{codigo}?desde=N`, com o token em `Authorization: Bearer`). Cada jogador tem o próprio histórico de passos, e o cursor é dele. A jogada leva o cursor visto: se a partida andou depois dele, volta `409`, em vez de cair num lugar que o jogador não viu.
  - Descarte, como o usuário definiu: quem tem a carta da vez pode descartá-la até a primeira ação de quem vem depois do descarte (jogar, pedir aumento ou decidir a Onze). Essa ação fecha o descarte, e o servidor responde "não descartar" por todos antes de aplicá-la (RG-DESC-1 e RG-DESC-2). Não há prazo, e a sala não guarda resposta pendente, então quem não tem a carta não distingue "o dono não descartou" de "ninguém tinha" (RG-DESC-10, com teste). As opções de quem vem depois do descarte são as ações legais do estado em que todos recusaram; o motor puro permite calcular isso sem aplicar nada.
  - Contra o bot continua igual, com o descarte como fase em que você responde, porque o bot agiria antes de você poder descartar.
  - `Passo` saiu de `PartidaReconstituida` para servir aos dois modos. Os testes de informação oculta usam `CartasOcultas`, que vale para qualquer jogador; uma propriedade joga partidas inteiras numa sala, com escolhas aleatórias dos dois lados, e confere que nada vaza e que a seed não aparece.
- **Frontend web: 2x2, nomes e jogar de novo** (aprovado; o core não mudou). O guia (`docs/frontend-web.md`) tem a API.
  - O menu pede o nome e oferece 1x1 e 2x2 contra o bot e ao criar sala. O nome é obrigatório nas salas (1 a 16 caracteres, sem repetir, sem "você" nem "Bot N") e opcional contra o bot. Os textos usam os nomes, também no 1x1; as duplas são "sua dupla" e "dupla adversária", como na CLI.
  - Os lugares são `lugar1` a `lugar4`; no 2x2, 1 e 3 formam `dupla1`, e 2 e 4, `dupla2`. Na sala de espera, cada um escolhe um lugar vazio; quem criou a sala começa, e os lugares vazios viram `BotAleatorio` (`Bot N`, com a seed da partida mais N - 1). O 2x2 contra bots é uma sala só sua, começada na hora; o 1x1 contra o bot continua sem sala.
  - Os bots agem quando alguém pergunta pela sala, 1,5 s depois da última mudança. Esperam 5 s quando a jogada deles fecharia o descarte ou responderia a um aumento com parceiro humano: a proposta dizia 1 s, mas assim dá para ver cada jogada, descartar e pedir para ver as cartas. As ações opcionais (descartar, ver as cartas) são oferecidas a cada bot uma vez por momento da rodada.
  - A mesa põe você embaixo, o parceiro em cima e os adversários dos lados (o que joga depois de você à direita). A mão do parceiro aparece aberta enquanto a dupla se vê. No celular, a vira fica entre os adversários.
  - Jogar de novo (pedido do usuário depois da proposta): no fim, cada pessoa pede outra partida ou sai. Com todas pedindo (os bots sempre topam), a `Sala` recomeça com outra seed (`SplittableRandom` sobre a anterior) e os mesmos lugares e nomes; quem sai impede a revanche. O cursor de cada jogador continua contando entre as partidas (`passosAnteriores`), então uma jogada com o cursor da partida anterior volta `409`, e a vista com ele traz a partida nova desde o começo. A vista diz o número da `partida`, quem quer jogar de novo e quem saiu.
- **Frontend web: a vaza como pilha** (pedido do usuário; o core não mudou). Cada vaza é uma pilha com a carta mais forte por cima, e clicar nela mostra as cartas na ordem em que foram jogadas, com quem jogou cada uma.
  - A página não sabe a ordem de força, que depende da vira. A resposta leva os números: `forca` em cada `CartaJogada` e `forcas` por vaza da visão, com `null` nas encobertas (que não contam, e cuja força revelaria algo da carta). Por isso o `Passo` guarda `viraAntes`: a carta que encerra a rodada chega junto com a rodada seguinte, de outra vira.
  - Na pilha, as cartas sem força (as encobertas) ficam por baixo, e numa força igual a jogada depois fica por cima. As de baixo aparecem pela borda, com a sigla de quem jogou.
  - Testes: o `ForcaDasCartasTest` confere as forças de uma partida 2x2 inteira contra a ordem do paulista, inclusive na jogada que encerra a rodada (uma mutação que usa a vira nova falha), e o `CartasOcultas` confere que nenhuma encoberta tem força.
- **M11** 2x2 (aprovado). `FaseDaRodada` e `MotorDeTruco` não mudaram. O que mudou:
  - a `VarianteDeRegras` ganhou a peça `RegrasDeDupla`: quantos jogadores por equipe a variante aceita (1 ou 2 no paulista), se a dupla que recebe um aumento pode se ver e se a dupla com 11 se vê na decisão da Onze;
  - `novaPartida` aceita duas equipes do tamanho que a variante aceita, e `ConfiguracaoDaPartida.parceirosDe` dá os parceiros de um jogador (nenhum no 1x1);
  - a `RegrasDeVazaPaulista` empata a vaza quando as mais fortes empatam, mesmo entre parceiros (RG-2X2-4), e a Rodada de Onze é decidida pelo jogador da dupla com 11 que joga primeiro na 1ª vaza (RG-2X2-12). No 1x1 as duas coisas dão o mesmo de antes;
  - `Acao` ganhou `VerCartasDoParceiro`; `Evento` ganhou `QuisVerAsCartas` e `DuplaViuAsCartas` (públicos) e `CartasDoParceiroVistas` (privado); `PedidoDeAumento` ganhou `queremVerAsCartas` (com um construtor de 3 argumentos para o pedido novo); `VisaoDoJogador` ganhou `maosDosParceiros`.
- **M11** Ver as cartas acontece dentro de `AguardandoRespostaDeAumento`, sem fase nova:
  - quem recebeu o pedido e o parceiro têm `VerCartasDoParceiro`, uma vez cada; quando todos da dupla querem, saem `DuplaViuAsCartas` e um `CartasDoParceiroVistas` para cada um, e a visão mostra a mão do parceiro até a resposta;
  - quem recebeu pode responder sem esperar, e a resposta encerra a chance;
  - na Rodada de Onze, o `aplicar` que leva à decisão emite os `CartasDoParceiroVistas` da dupla com 11, e a visão mostra as mãos até a decisão.
- **M11** Testes: `DuplasTest` com um teste por regra (RG-2X2-3 a 15), casos de 2x2 nas regras de vaza e na distribuição, e um golden 2x2 (seed 2026). As propriedades do core, o fuzz dos bots e a ida e volta do protocolo jogam 1x1 e 2x2; a mão do parceiro é conferida contra uma regra escrita de novo nos testes, e um teste de mutação (mostrar sempre a mão do parceiro) confirmou que as propriedades pegam o vazamento.
- **M11** Bots e CLI:
  - `Estrategia` ganhou o método `default` `decidirOpcional`, que pode devolver vazio. O `Arbitro` consulta, antes de quem tem a vez, quem só tem ações opcionais (`VerCartasDoParceiro`), uma vez a cada mudança do pedido; assim o parceiro pode não querer ver, e é consultado de novo quando o outro passa a querer. O `BotAleatorio` diz sim ou não ao acaso. No 1x1 nada disso acontece, e os resultados fixados não mudaram;
  - a CLI ganhou `--2x2`: você e Carla contra Beto e Davi, bots com as seeds `seed`, `seed + 1` e `seed + 2`. O `JogadorHumano` oferece "Não ver as cartas" quando é consultado fora da vez, o `Narrador` chama as equipes de "sua dupla" e "dupla adversária", e a `Tela` mostra a mão do parceiro quando a dupla se vê.
- **M11** Protocolo: DTOs da ação, dos eventos e dos campos novos (`maosDosParceiros` sai com os jogadores em ordem alfabética). O `docs/protocolo.md` ganhou os exemplos novos e um exemplo de visão no 2x2, e o teste da documentação passou a aceitar mais de um exemplo de visão.
