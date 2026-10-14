# Protocolo JSON

O formato das mensagens trocadas entre o servidor (Java, autoritativo) e os clientes (Unity, web, terminal). A implementação fica no módulo `truco-protocol` (`ProtocoloJson`).

- **Cliente → servidor:** ações (`Acao`).
- **Servidor → cliente:** eventos (`Evento`) e a visão do jogador (`VisaoDoJogador`).

O estado completo da partida nunca vira JSON, porque tem informação oculta. O servidor manda a cada jogador **só a visão dele** e **só os eventos que ele pode ver**. Por isso a visibilidade de um evento não vai no JSON: quem recebe um evento privado é o próprio dono dele.

> Os blocos marcados como `json acao`, `json evento` e `json visao` são lidos pelo teste `DocumentacaoDoProtocoloTest`: cada exemplo precisa ser válido, voltar igual e ter os mesmos campos do modelo. Os outros blocos são ilustrativos.

## Regras gerais

- **Formato:** JSON em UTF-8. O servidor escreve sem espaços e numa linha só; ao ler, espaços e quebras de linha são aceitos.
- **Nomes:** os campos têm os nomes do modelo (em português, sem acento) e saem na mesma ordem.
- **Tipos com variantes** (ação, evento, tipo de rodada, resultado da vaza) têm o campo `"tipo"`, com o nome da variante.
- **Campos sempre presentes:** cada tipo tem sempre os mesmos campos. Um valor ausente sai como `null`, como a vira antes da distribuição ou a carta encoberta de outro jogador.
- **Leitura estrita:** campo desconhecido, campo faltando, `"tipo"` desconhecido, número como texto, carta inexistente ou texto depois do JSON tornam a mensagem inválida. A leitura nunca lança exceção: devolve `Leitura.Invalida(motivo)`.
- **Mesmo valor, mesmo JSON:** as chaves de objetos como o placar saem em ordem alfabética.

## Valores básicos

- **Jogador e equipe:** o identificador, como texto: `"ana"`. Na v1, cada equipe tem um jogador só, e a equipe tem o mesmo identificador dele.
- **Carta:** `{"valor":"AS","naipe":"COPAS"}`, só em ASCII.
  - Valores usados no Truco Paulista: `AS`, `DOIS`, `TRES`, `QUATRO`, `CINCO`, `SEIS`, `SETE`, `DEZ`, `DAMA`, `VALETE`. O baralho de 40 cartas não tem `OITO`, `NOVE` nem `REI`.
  - Naipes: `COPAS`, `ESPADAS`, `OUROS`, `PAUS`.
- **Placar** e **cartas na mão:** objetos com o identificador como chave: `{"ana":3,"beto":1}`.
- **Tipo da rodada:** `{"tipo":"Normal"}`, `{"tipo":"DeOnze","equipeComOnze":"ana","decidiuJogar":false}` ou `{"tipo":"Escurinho"}`.
- **Resultado de uma vaza:** `{"tipo":"Vencida","jogador":"ana","equipe":"ana"}` ou `{"tipo":"Empatada"}`.

## Ações (cliente → servidor)

As cartas são indicadas pela **posição na mão** (`indiceNaMao`, a partir de 0), porque na Rodada Escurinho o jogador não vê as próprias cartas. Ao jogar uma carta, as seguintes sobem uma posição. Uma ação que não está entre as legais é rejeitada pelo motor.

Jogar uma carta aberta:

```json acao
{"tipo":"JogarCarta","indiceNaMao":0}
```

Jogar uma carta encoberta (da 2ª vaza em diante):

```json acao
{"tipo":"JogarEncoberta","indiceNaMao":1}
```

Descartar a carta da vez, no descarte:

```json acao
{"tipo":"Descartar","indiceNaMao":0}
```

Não descartar. No descarte todos respondem, inclusive quem não tem a carta da vez:

```json acao
{"tipo":"RecusarDescarte"}
```

Pedir aumento para o próximo nível da escada. Com um pedido pendente, significa "aceito e peço mais":

```json acao
{"tipo":"PedirAumento"}
```

Aceitar um aumento ou, na Rodada de Onze, decidir jogar:

```json acao
{"tipo":"Aceitar"}
```

Correr de um aumento ou, na Rodada de Onze, decidir não jogar. Na própria vez não se corre:

```json acao
{"tipo":"Correr"}
```

## Eventos (servidor → cliente)

Uma ação aplicada gera uma lista de eventos. Quando ela encerra uma rodada, a mesma lista já traz o começo da rodada seguinte. Os eventos marcados como **privados** só vão para o jogador indicado neles.

Uma rodada começou. `valor` é quanto ela vale ao começar:

```json evento
{"tipo":"RodadaIniciada","numeroDaRodada":3,"carteador":"beto","valor":1,"vira":{"valor":"SETE","naipe":"OUROS"}}
```

As cartas que o jogador recebeu (**privado**; não acontece na Rodada Escurinho):

```json evento
{"tipo":"CartasDistribuidas","jogador":"ana","cartas":[{"valor":"QUATRO","naipe":"PAUS"},{"valor":"AS","naipe":"ESPADAS"},{"valor":"QUATRO","naipe":"OUROS"}]}
```

Uma carta descartada, pública:

```json evento
{"tipo":"CartaDescartada","jogador":"ana","carta":{"valor":"QUATRO","naipe":"OUROS"}}
```

A carta que o jogador comprou no lugar da descartada (**privado**):

```json evento
{"tipo":"CartaRecebidaPorDescarte","jogador":"ana","carta":{"valor":"DEZ","naipe":"PAUS"}}
```

O descarte terminou. É o mesmo evento quando o dono da carta da vez recusa e quando ninguém a tem:

```json evento
{"tipo":"DescarteEncerrado"}
```

Uma carta jogada aberta:

```json evento
{"tipo":"CartaJogada","jogador":"beto","carta":{"valor":"TRES","naipe":"ESPADAS"},"numeroDaVaza":1}
```

Uma carta jogada encoberta. A carta não vai no evento, nem para quem a jogou:

```json evento
{"tipo":"CartaEncobertaJogada","jogador":"beto","numeroDaVaza":2}
```

Uma vaza terminou, com vencedor ou empatada:

```json evento
{"tipo":"VazaFinalizada","numeroDaVaza":1,"resultado":{"tipo":"Vencida","jogador":"beto","equipe":"beto"}}
```

```json evento
{"tipo":"VazaFinalizada","numeroDaVaza":2,"resultado":{"tipo":"Empatada"}}
```

Um pedido de aumento:

```json evento
{"tipo":"AumentoPedido","pedinte":"ana","nivelProposto":6}
```

Um aumento aceito; `valor` é quanto a rodada passa a valer. Também marca a decisão de jogar a Rodada de Onze, com valor 3:

```json evento
{"tipo":"AumentoAceito","jogador":"beto","valor":6}
```

Um jogador correu:

```json evento
{"tipo":"JogadorCorreu","jogador":"beto"}
```

Uma rodada terminou e a equipe marcou os pontos:

```json evento
{"tipo":"RodadaFinalizada","numeroDaRodada":3,"vencedora":"ana","pontos":3}
```

As três vazas empataram, e ninguém marca ponto:

```json evento
{"tipo":"RodadaAnulada","numeroDaRodada":4}
```

O placar depois de uma rodada:

```json evento
{"tipo":"PlacarAtualizado","placar":{"ana":7,"beto":6}}
```

Fim da partida. Depois dela, nenhuma ação é aceita:

```json evento
{"tipo":"PartidaFinalizada","vencedora":"ana","placar":{"ana":12,"beto":9}}
```

A rodada que começa é de Onze, e quem tem 11 decide se joga ou corre:

```json evento
{"tipo":"RodadaDeOnzeIniciada","equipeComOnze":"ana"}
```

A rodada que começa é Escurinho (11 a 11). Ninguém vê as próprias cartas:

```json evento
{"tipo":"RodadaEscurinhoIniciada"}
```

## Visão do jogador (servidor → cliente)

O que um jogador pode ver da partida. O cliente pode desenhar a mesa só com ela.

| Campo | O que é |
|---|---|
| `jogador` | de quem é a visão |
| `jogadoresNaOrdemDaMesa`, `equipes` | a mesa; o próximo a jogar é o que está à direita (o seguinte na lista) |
| `placar`, `numeroDaRodada`, `carteador` | |
| `tipoDaRodada` | normal, de Onze ou Escurinho |
| `aposta` | quanto a rodada vale (`valor`), a equipe que aceitou o último aumento (a única que pode pedir o próximo) e o pedido pendente, se houver |
| `vira`, `descartadas` | cartas públicas |
| `cartaDaVezNoDescarte` | durante o descarte, a carta da vez; nunca diz quem a tem |
| `mao` | as suas cartas, na ordem das posições; vazia na Rodada Escurinho |
| `cartasNaMao` | quantas cartas cada jogador tem |
| `vazas`, `vazaAtual` | as jogadas; a carta encoberta de outro jogador vem com `"carta":null` |
| `vezDe` | de quem se espera a ação; `null` no descarte (todos respondem) e no fim da partida |
| `vencedoraDaPartida` | a equipe que venceu, quando a partida acabou |

Exemplo: a visão de Ana na 3ª rodada. Beto venceu a 1ª vaza e abriu a 2ª com uma carta encoberta. Ana, que tinha aceitado o truco, pediu seis, e a resposta é do Beto:

```json visao
{
  "jogador": "ana",
  "jogadoresNaOrdemDaMesa": ["ana", "beto"],
  "equipes": [
    {"id": "ana", "jogadores": ["ana"]},
    {"id": "beto", "jogadores": ["beto"]}
  ],
  "placar": {"ana": 4, "beto": 6},
  "numeroDaRodada": 3,
  "carteador": "beto",
  "tipoDaRodada": {"tipo": "Normal"},
  "aposta": {
    "valor": 3,
    "ultimaEquipeQueAceitou": "ana",
    "pedidoPendente": {"pedinte": "ana", "respondedor": "beto", "nivelProposto": 6}
  },
  "vira": {"valor": "SETE", "naipe": "OUROS"},
  "descartadas": [{"valor": "QUATRO", "naipe": "OUROS"}],
  "cartaDaVezNoDescarte": null,
  "mao": [{"valor": "AS", "naipe": "ESPADAS"}, {"valor": "DEZ", "naipe": "PAUS"}],
  "cartasNaMao": {"ana": 2, "beto": 1},
  "vazas": [
    {
      "jogadas": [
        {"jogador": "ana", "carta": {"valor": "QUATRO", "naipe": "PAUS"}, "encoberta": false},
        {"jogador": "beto", "carta": {"valor": "TRES", "naipe": "ESPADAS"}, "encoberta": false}
      ],
      "resultado": {"tipo": "Vencida", "jogador": "beto", "equipe": "beto"}
    }
  ],
  "vazaAtual": [{"jogador": "beto", "carta": null, "encoberta": true}],
  "vezDe": "beto",
  "vencedoraDaPartida": null
}
```

## Fora deste documento

O envelope das mensagens de rede fica com o servidor, quando ele existir: o tipo da mensagem, o identificador da partida, a versão do protocolo e a resposta a uma ação rejeitada (`MotivoDeRejeicao`).
