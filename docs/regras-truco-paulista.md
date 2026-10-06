# Regras do Truco Paulista — especificação do core

> **Revisado pelo autor em 06/10/2026.** Este documento é a fonte da verdade do projeto: o código e os testes seguem o que está aqui. Os itens ainda em aberto estão marcados `[A CONFIRMAR]` e reunidos na seção 12.

**Legenda de status**

- **[DEFINIDA]** regra confirmada pelo autor.
- **[PROJETO]** decisão de escopo ou de implementação deste projeto.
- **[FUTURO]** regra definida, mas fora do escopo da v1 (não implementar agora).
- **[A CONFIRMAR]** ainda não decidida. O texto traz o *padrão proposto*, que o core implementa como opção configurável (`OpcoesTrucoPaulista`) até a confirmação.

Cada regra tem um ID (`RG-...`). Todo teste de regra cita o ID.

## 0. Glossário

- **Rodada:** tudo o que acontece entre distribuir as cartas e o resultado ficar decidido: vazas jogadas até acabarem as cartas da mão ou até alguém correr. É a rodada que vale pontos (1, 3, 6, 9 ou 12).
- **Vaza:** cada confronto em que cada jogador joga uma carta. Uma rodada tem até 3 vazas.
- **Carteador:** quem dá (distribui) as cartas na rodada.
- **Vira:** carta virada após a distribuição; define a manilha.
- **Manilha:** as cartas mais fortes da rodada, definidas pela vira.
- **Zap:** a manilha de paus, a mais forte.
- **Aumento:** pedido para subir o valor da rodada (truco, seis, nove, doze).
- **Rodada de Onze:** rodada em que só um lado tem 11 pontos.
- **Rodada Escurinho:** rodada em que os dois lados têm 11 pontos e as cartas são jogadas às cegas.

## 1. Escopo da v1

- **RG-ESC-1 [PROJETO]** A v1 é **1x1** (dois jogadores). O modelo já usa `Equipe` para suportar 2x2 (duas duplas) no futuro.
- **RG-ESC-2 [PROJETO]** Tudo que depende de parceiros (seção 11) fica fora da v1.

## 2. Baralho e cartas

- **RG-CARTAS-1 [DEFINIDA]** O baralho tem **40 cartas**: A, 2, 3, 4, 5, 6, 7, **10**, Q (dama), J (valete) em 4 naipes (ouros, espadas, copas, paus). Não há 8, 9, **K** nem coringas.
- **RG-CARTAS-2 [DEFINIDA]** Ordem de força das cartas comuns, da mais fraca para a mais forte:
  `4 < 5 < 6 < 7 < 10 < Q < J < A < 2 < 3`.
  Na disputa de uma vaza, o naipe não importa: cartas de mesmo valor empatam.
- **RG-CARTAS-3 [DEFINIDA]** **Vira:** depois de distribuir as cartas, uma carta do baralho é virada. Ela não pertence a ninguém e não é jogada.
- **RG-CARTAS-4 [DEFINIDA]** **Manilha:** é o valor imediatamente acima do valor da vira, na ordem circular de RG-CARTAS-2 (depois do 3 volta o 4).

  | Vira | Manilha |
  |---|---|
  | 4 | 5 |
  | 5 | 6 |
  | 6 | 7 |
  | 7 | 10 |
  | 10 | Q |
  | Q | J |
  | J | A |
  | A | 2 |
  | 2 | 3 |
  | 3 | 4 |

- **RG-CARTAS-5 [DEFINIDA]** As manilhas vencem todas as outras cartas. Entre manilhas, o naipe desempata, do mais fraco ao mais forte: **ouros < espadas < copas < paus** (paus = zap).
- **RG-CARTAS-6 [DEFINIDA]** Cartas com o mesmo valor da vira (que não sejam a manilha) são cartas comuns.

## 3. Estrutura da partida e da rodada

- **RG-PARTIDA-1 [DEFINIDA]** A partida é disputada em rodadas sucessivas. Vence quem primeiro atingir **12 pontos** ou mais.
- **RG-PARTIDA-2 [DEFINIDA]** Em cada rodada: o carteador embaralha e distribui **3 cartas** a cada jogador, e vira-se a vira.
- **RG-PARTIDA-3 [DEFINIDA]** Uma rodada começa valendo **1 ponto**.
- **RG-PARTIDA-4 [DEFINIDA]** **Carteador e ordem de jogo:**
  - o carteador da 1ª rodada é sorteado (pela seed);
  - a ordem de distribuição e de jogo vai sempre para o **jogador à direita** (sentido anti-horário, visto de cima);
  - abre a 1ª vaza o jogador à direita do carteador;
  - ao fim de cada rodada, o carteador passa para o jogador à direita, e o seguinte a ele passa a abrir a 1ª vaza. No 1x1, os dois se alternam.
- **RG-PARTIDA-5 [DEFINIDA]** A rodada termina quando o resultado fica decidido (seção 5), quando alguém corre ou quando acabam as cartas da mão.

## 4. Descarte

- **RG-DESC-1 [DEFINIDA]** O descarte só pode ocorrer **antes de a primeira carta da rodada ser jogada**.
- **RG-DESC-2 [PROJETO]** A fase de descarte acontece logo depois da distribuição e da vira, e termina antes de qualquer outra ação da rodada, inclusive pedidos de aumento.
- **RG-DESC-3 [DEFINIDA]** **Sequência de descarte:** todas as cartas, **exceto as manilhas**, da mais fraca para a mais forte (ordem de RG-CARTAS-2). Dentro do mesmo valor, a ordem é por naipe: **ouros, espadas, copas, paus**.
- **RG-DESC-4 [DEFINIDA]** Quem tiver a **carta da vez** pode descartá-la e comprar outra do baralho. A sequência avança para a próxima carta, e quem a tiver pode descartá-la também, e assim por diante.
- **RG-DESC-5 [DEFINIDA]** O descarte é **opcional**. Se o dono da carta da vez recusar, a sequência termina.
- **RG-DESC-6 [DEFINIDA]** A sequência também termina quando a carta da vez **não está na mão de nenhum jogador** (isso inclui a vira e as cartas que ficaram no baralho). Se ninguém tem a primeira carta da sequência, não há descarte.
- **RG-DESC-7 [DEFINIDA]** A carta descartada sai da rodada. A reposição vem do baralho restante (sem a vira, sem as cartas nas mãos e sem as já descartadas). A carta reposta pode ser, ela mesma, a próxima da sequência, e então pode ser descartada.
- **RG-DESC-8 [DEFINIDA]** O descarte é **público** (todos veem qual carta foi descartada). A carta recebida na reposição é **privada**.
- **RG-DESC-9 [DEFINIDA]** Não há descarte na Rodada Escurinho, porque ninguém vê a própria mão.
- **RG-DESC-10 [PROJETO]** O core não expõe quem tem a carta da vez. O fim do descarte emite o mesmo evento público (`DescarteEncerrado`), seja porque o dono recusou, seja porque ninguém tinha a carta da vez.

## 5. Vazas e vencedor da rodada

- **RG-VAZA-1 [DEFINIDA]** Uma rodada tem **até 3 vazas**. Em cada vaza, cada jogador joga uma carta, um de cada vez.
- **RG-VAZA-2 [DEFINIDA]** Vence a vaza quem jogou a carta aberta mais forte (seção 2). **Quem vence a vaza abre a seguinte.**
- **RG-VAZA-3 [DEFINIDA]** Vence a rodada quem vencer **2 vazas**. A rodada termina assim que o resultado estiver decidido, sem jogar as vazas restantes.

**Empates**

- **RG-EMP-1 [DEFINIDA]** Empate na 1ª vaza: quem vencer a 2ª vence a rodada.
- **RG-EMP-2 [DEFINIDA]** Empate na 2ª vaza (a 1ª teve vencedor): vence a rodada quem venceu a 1ª.
- **RG-EMP-3 [DEFINIDA]** Empate na 1ª e na 2ª: a 3ª vaza decide.
- **RG-EMP-4 [DEFINIDA]** Empate na 3ª vaza (as vazas 1 e 2 tiveram vencedores diferentes): vence a rodada quem venceu a 1ª.
- **RG-EMP-5 [DEFINIDA]** As 3 vazas empatam: a rodada é **anulada**, ninguém pontua e começa uma nova rodada, com o próximo carteador.
- **RG-EMP-6 [DEFINIDA]** Depois de uma vaza empatada, abre a seguinte quem abriu a vaza empatada.

## 6. Carta encoberta (esconder uma carta)

- **RG-ENC-1 [DEFINIDA]** O jogador pode jogar uma carta **encoberta** (virada para baixo) apenas **a partir da 2ª vaza**.
- **RG-ENC-2 [DEFINIDA]** A carta encoberta **não conta na comparação** da vaza: perde para qualquer carta aberta. Vence a vaza a carta aberta mais forte. Se só um jogador jogou carta aberta e os demais encobriram, esse jogador (e o parceiro, no 2x2) vence a vaza.
- **RG-ENC-3 [DEFINIDA]** A identidade da carta encoberta nunca é revelada ao adversário, nem no fim da rodada. O adversário só sabe que foi jogada uma encoberta.
- **RG-ENC-4 [A CONFIRMAR]** Se **todas** as cartas da vaza forem encobertas, a vaza empata.

## 7. Aumentos: truco, seis, nove e doze

- **RG-AUM-1 [DEFINIDA]** Escada de valores da rodada: **1 → 3 (truco) → 6 (seis) → 9 (nove) → 12 (doze)**. Doze é o teto. Quem vencer uma rodada valendo 12 chega a 12 pontos e vence a partida, tenha sido ou não quem pediu o doze.
- **RG-AUM-2 [PROJETO]** Só o jogador da vez pode pedir aumento, antes de jogar sua carta, e apenas quando não houver pedido pendente.
- **RG-AUM-3 [DEFINIDA]** Quem recebe o pedido tem três opções:
  - **aceitar** o aumento: a rodada passa a valer o novo valor e o jogo continua;
  - **correr**: desiste, e quem pediu ganha o valor que a rodada tinha antes do pedido;
  - **pedir mais**: aceita o aumento e já propõe o próximo nível da escada.
- **RG-AUM-4 [DEFINIDA]** Valor ganho por quem pediu quando o adversário corre:

  | Pedido recusado | Quem pediu ganha |
  |---|---|
  | truco | 1 ponto |
  | seis | 3 pontos |
  | nove | 6 pontos |
  | doze | 9 pontos |

- **RG-AUM-5 [DEFINIDA]** **Direito de aumentar:** depois de um aumento aceito, só quem aceitou pode pedir o próximo nível. Quem pediu espera. Os direitos se alternam a cada aumento.
- **RG-AUM-6 [DEFINIDA]** Quem corre na própria vez, sem pedido pendente, entrega a rodada ao adversário pelo valor atual da rodada.
- **RG-AUM-7 [DEFINIDA]** Aumentos podem ser pedidos em qualquer vaza (1ª, 2ª ou 3ª), exceto na Rodada de Onze e na Rodada Escurinho.

## 8. Rodada de Onze e Rodada Escurinho

- **RG-ONZE-1 [DEFINIDA]** Quando **só um** lado tem 11 pontos, é **Rodada de Onze**: esse jogador, vendo suas cartas, decide **jogar** (a rodada vale 3 pontos) ou **correr** (o adversário ganha 1 ponto).
- **RG-ONZE-2 [DEFINIDA]** Na Rodada de Onze não há pedido de aumento: o valor é fixo em 3.
- **RG-ONZE-3 [A CONFIRMAR]** Na Rodada de Onze há descarte (RG-DESC-9 só exclui a Rodada Escurinho), e ele acontece **antes** da decisão de jogar ou correr.
- **RG-ESCURINHO-1 [DEFINIDA]** Quando **os dois** lados têm 11 pontos, é **Rodada Escurinho**: a rodada vale 1 ponto, as cartas são jogadas **às cegas** (o jogador não vê a própria mão e escolhe pela posição), não há aumentos, carta encoberta nem descarte, e quem vencer a rodada vence a partida.

## 9. Fim da partida

- **RG-FIM-1 [DEFINIDA]** A partida termina imediatamente quando um lado atinge 12 pontos ou mais. Esse lado vence.

## 10. Informação oculta (requisitos do core)

- **RG-VIS-1 [PROJETO]** Cada jogador só vê: suas próprias cartas, a vira, as cartas abertas já jogadas, as cartas descartadas, o fato de uma carta encoberta ter sido jogada (sem saber qual), o placar e o estado da rodada.
- **RG-VIS-2 [DEFINIDA]** A ordem do baralho e as cartas dos outros jogadores nunca saem do core em nenhuma visão ou evento destinado a um jogador.
- **RG-VIS-3 [DEFINIDA]** Na Rodada Escurinho, o jogador não recebe a identidade das próprias cartas.
- **RG-VIS-4 [PROJETO]** O descarte segue RG-DESC-8 e RG-DESC-10: a carta reposta é privada e o core não revela quem tinha a carta da vez.

## 11. Regras do 2x2 (futuro)

- **RG-2X2-1 [FUTURO]** Ao receber um pedido de truco ou de qualquer aumento (seis, nove, doze), o time que recebeu pode, opcionalmente, **ver as cartas do parceiro** antes de responder. Quem fez o pedido **não** pode ver as cartas do parceiro.
- **RG-2X2-2 [FUTURO]** Depois de ver, o time escolhe **aceitar** (a rodada passa a valer o novo valor) ou **correr** (cede pontos ao adversário conforme RG-AUM-4).
- **RG-2X2-3 [FUTURO]** Valem a ordem à direita (RG-PARTIDA-4) e a regra de vaza com uma única carta aberta (RG-ENC-2).

## 12. Decisões em aberto

Preencha a coluna "Decisão" e o Claude Code atualiza `OpcoesTrucoPaulista` e os testes.

| ID | Pergunta | Padrão proposto | Decisão |
|---|---|---|---|
| RG-ENC-4 | O que acontece se todas as cartas da vaza forem encobertas? | a vaza empata | Sim, empata |
| RG-ONZE-3 | Há descarte na Rodada de Onze e quando? | sim, antes da decisão de jogar ou correr | Sim |
| RG-2X2-1/2 | No 2x2, quem do time responde ao aumento e o parceiro sabe que suas cartas foram vistas? | definir ao implementar o 2x2 | responde quem recebe o truco ou o aumento, ele pode ver minhas cartas ou não, decisão da dupla os dois precisam querem ver as cartas um do outro|
| RG-ONZE (2x2) | Na Rodada de Onze do 2x2, os parceiros veem as cartas um do outro? | definir ao implementar o 2x2 | Sim |

## 13. Exemplos para testes

**Manilha**
- Vira 7♦ → manilha é o 10. Ordem entre elas: 10♦ < 10♠ < 10♥ < 10♣ (zap). Qualquer 10 vence um 3.
- Vira 3♠ → manilha é o 4. O 4♣ é a carta mais forte da rodada.
- Vira J♥ → manilha é o A. Vira A♥ → manilha é o 2.

**Vazas e empates**
- Vaza 1 empata (A♥ contra A♠, nenhuma é manilha); na vaza 2, X vence → X vence a rodada (RG-EMP-1).
- Vaza 1: X vence; vaza 2: Y vence; vaza 3 empata → X vence a rodada (RG-EMP-4).
- Vaza 1: X vence; vaza 2 empata → X vence a rodada (RG-EMP-2).
- Três vazas empatadas → rodada anulada, ninguém pontua, o carteador passa para o próximo (RG-EMP-5).
- Vaza empatada aberta por X → X abre a vaza seguinte (RG-EMP-6).

**Carta encoberta**
- Na 2ª vaza, X joga 6♣ aberta e Y joga encoberta → X vence a vaza (RG-ENC-2).
- Na 1ª vaza, nenhuma carta pode ser encoberta (RG-ENC-1).
- (2x2, futuro) só um jogador joga carta aberta e os outros três encobrem → a dupla dele vence a vaza.

**Aumentos**
- X pede truco, Y corre → X ganha 1 ponto (RG-AUM-4).
- X pede truco, Y aceita (rodada vale 3), Y pede seis, X corre → Y ganha 3 pontos.
- X pede truco, Y aceita (vale 3), Y pede seis, X aceita (vale 6) → agora só X pode pedir nove (RG-AUM-5).
- X pede truco, Y responde com seis (aceita e aumenta); X pode aceitar, correr ou pedir nove.
- Rodada valendo 12: ninguém pode pedir aumento, e quem vencer a rodada vence a partida, tenha pedido o doze ou não (RG-AUM-1).

**Descarte**
- Vira 7 (manilha 10): a sequência é 4♦, 4♠, 4♥, 4♣, 5♦, ... X tem 4♦ e 4♠, Y tem 4♥. X descarta 4♦ e 4♠, e Y descarta 4♥. Se o 4♣ não está em nenhuma mão, o descarte termina.
- Vira 3 (manilha 4): a sequência começa em 5♦. Quem tem 4♦ não pode descartá-lo.
- Ninguém tem o 4♦ (está no baralho ou é a vira), mas Y tem o 4♠ → não há descarte (RG-DESC-6).
- X tem 4♦ e recusa descartar; Y tem 4♠ → a sequência termina e Y não pode descartar (RG-DESC-5).
- X descarta 4♦ e compra o 4♠ → pode descartá-lo também (RG-DESC-7).
- Rodada Escurinho: nenhum descarte (RG-DESC-9).

**Placar**
- X com 9 pontos vence uma rodada valendo 3 → X chega a 12 e vence a partida (RG-FIM-1).
- X com 11 e Y com 8: Rodada de Onze para X. Se X corre, Y ganha 1 ponto (RG-ONZE-1).
- X com 11 e Y com 11: Rodada Escurinho; quem vencer a rodada vence a partida (RG-ESCURINHO-1).
