# Frontend web de teste

Uma página para jogar no navegador, contra o `BotAleatorio` ou contra amigos, no 1x1 ou no 2x2, e ver o core funcionando. É uma ferramenta de desenvolvimento, como a CLI: **não é o servidor de verdade** (sem contas, sem fila para achar adversário, sem partidas guardadas em banco).

## Como funciona

- O módulo `truco-web` sobe um servidor HTTP só com o JDK (`com.sun.net.httpserver`) e serve a página, que fica em `truco-web/src/main/resources/web/`: `index.html`, `estilo.css`, `mesa.js` (o menu e a mesa), `cartas.js` (o baralho) e `baralho-espanhol.png` (a folha com as cartas).
- A página só recebe a **sua visão** e os **eventos que você pode ver**, no formato de `docs/protocolo.md`, como um cliente de verdade receberia. Cada evento e cada ação vêm junto com o texto da CLI. A página não tem regra nenhuma: só desenha e manda as jogadas.
- O menu pede o **seu nome** e tem três opções: **contra o bot** (1x1 ou 2x2), **criar sala** (1x1 ou 2x2) e **entrar numa sala**. O nome é obrigatório nas salas e opcional contra o bot.

### Contra o bot

- **1x1:** o servidor não guarda nada. A página guarda a **seed** e a **lista das suas jogadas** (também no `localStorage` do navegador) e manda as duas a cada pedido. O servidor refaz a partida inteira a partir delas, o que leva milissegundos, e responde. Isso só funciona porque o core é determinístico: a mesma seed com as mesmas ações dá a mesma partida, e o bot também tem seed. Por isso a partida sobrevive ao servidor gratuito dormir ou reiniciar.
- Como a página conhece a seed, dá para calcular as cartas do bot a partir dela. Contra um bot isso não tem importância.
- **2x2:** você e um bot contra dois bots. É uma sala só sua, começada na hora, com os outros três lugares ocupados por bots; funciona como as salas abaixo.

### Numa sala, com amigos

- Quem cria a sala recebe um **código** de 6 caracteres (sem 0, O, 1, I e L, que se confundem) e um link `/?sala=CODIGO` para mandar aos amigos. Quem abre o link escreve o nome e entra.
- **Nomes:** de 1 a 16 letras, números, espaços, pontos, hífens ou apóstrofos, sem repetir na sala (sem diferença de maiúsculas). "Você" e "Bot" com número são reservados. Nos textos, cada um aparece pelo nome; as duplas, como "sua dupla" e "dupla adversária".
- **1x1:** a partida começa quando o segundo jogador entra.
- **2x2:** os lugares 1 e 3 formam uma dupla, e os lugares 2 e 4, a outra; parceiros sentam frente a frente (RG-2X2-1). Quem entra pega o primeiro lugar vazio e, na sala de espera, pode trocar para outro vazio. Quem criou a sala clica em **Começar**, e os lugares vazios viram bots (`Bot 2`, `Bot 4`...).
- A sala fica **só na memória do servidor**: a seed, sorteada com `SecureRandom`, nunca sai dele, porque com ela daria para calcular as cartas de todos. Cada jogador recebe um **token** secreto, que a página guarda no `localStorage` e manda no cabeçalho `Authorization`. Com ele, dá para fechar a aba e voltar para a partida.
- A página pergunta ao servidor o que mudou **a cada segundo**. Cada jogador tem o próprio histórico de passos, e o **cursor** diz até onde ele já viu.
- **Descarte** (RG-DESC-1, RG-DESC-2 e RG-DESC-10): quem tem a carta da vez pode descartá-la **até a primeira ação de quem vem depois do descarte** (jogar uma carta, pedir aumento ou, na Rodada de Onze, decidir). Essa ação fecha o descarte: o servidor responde "não descartar" por todos e só então a aplica. Ninguém espera prazo, e a sala nunca guarda uma resposta pendente. Assim, para quem não tem a carta, "o dono não descartou" e "ninguém tinha a carta" são iguais. Como na mesa de verdade, se quem abre jogar na hora, quem tinha a carta perde a vez de descartar.
- **Ver as cartas** (2x2, RG-2X2-9 e RG-2X2-10): quando a sua dupla recebe um pedido de aumento, aparece o botão **Ver as cartas**. Se os dois quiserem, cada um vê a mão do outro, aberta no assento de cima, até a resposta.
- **Bots:** agem quando alguém pergunta pela sala, um de cada vez, 1,5 s depois da última mudança, para dar para ver cada jogada. Esperam 5 s quando a jogada deles fecharia o descarte (para dar tempo de descartar) ou responderia a um aumento com parceiro humano (para dar tempo de pedir para ver as cartas).
- Uma jogada leva o cursor que o jogador tinha visto. Se a partida andou nesse meio-tempo (alguém descartou, ou um bot jogou), ela é recusada, e a página mostra o que mudou antes de você jogar de novo.
- **No fim da partida**, cada um escolhe **Jogar de novo** ou **Sair para o menu**. A partida nova começa quando todas as pessoas pedem (os bots sempre topam), na mesma sala, com os mesmos lugares e outra seed; o aviso mostra quem já pediu. Se alguém sai, os outros veem o aviso e só podem voltar ao menu. Começar outra coisa pelo menu também conta como sair.
- **Limites:** até 100 salas abertas ao mesmo tempo. A sala some 2 horas depois da última jogada, ou 30 minutos depois do fim da partida, e some também se o servidor reiniciar. Não há prazo para jogar: se alguém sumir, a partida espera.
- O `localStorage` é do navegador inteiro, não da aba. Para testar sozinho, jogando de mais de um lugar, abra o link numa **janela anônima** ou em outro navegador; numa aba comum do mesmo navegador, a página volta para a sala como quem já estava nela.

### A mesa

- Você fica embaixo. No 1x1, o outro jogador fica em cima. No 2x2, o parceiro fica em cima, o adversário que joga depois de você à direita e o outro à esquerda; cada vaza mostra as quatro cartas em cruz, cada uma do lado de quem a jogou.
- Quem tem a vez ganha um anel dourado. A sua dupla aparece em dourado, e a outra em azul, no placar, na mesa e no histórico.
- No celular (até 720 px de largura), as cartas encolhem; no 2x2, a vira fica entre os adversários, e as vazas vão para a linha de baixo.

### API

`POST /api/partida` com `{"seed": 2026, "acoes": [ ...suas jogadas, no formato do protocolo... ]}`, contra o bot no 1x1. Sem `seed`, o servidor sorteia uma. A resposta traz:

```json
{"seed": 2026,
 "passos": [{"eventos": [{"evento": {"tipo": "CartaJogada", "...": "..."}, "texto": "Bot jogou 5 de copas."}], "visao": {"...": "..."}}],
 "acoesLegais": [{"acao": {"tipo": "JogarCarta", "indiceNaMao": 0}, "texto": "Jogar 2 de bastos"}]}
```

- `passos` vai da sua última jogada até agora, com a visão depois de cada passo. A página usa isso para mostrar as jogadas do bot uma a uma.
- `acoesLegais` traz o que você pode fazer agora; vem vazio quando a partida acaba.
- Uma jogada ilegal, ou um pedido malformado, recebe `400` com `{"erro": "..."}`.

As salas:

| Pedido | Resposta |
|---|---|
| `POST /api/salas` com `{"formato": "2x2", "nome": "Ana"}` | `{"codigo": "K7QF2M", "token": "...", "voce": "lugar1"}`; `400` com nome ou formato inválido, `503` com salas demais |
| `POST /api/salas/K7QF2M/entrar` com `{"nome": "Beto"}` | `{"codigo": "K7QF2M", "token": "...", "voce": "lugar2"}`; `404` sem sala, `409` se cheia, já começada ou com o nome repetido |
| `POST /api/salas/K7QF2M/lugar` com `{"lugar": "lugar3"}` | a vista da sala; `409` se a partida começou ou o lugar está ocupado |
| `POST /api/salas/K7QF2M/comecar` | a vista da sala; `409` se não foi você quem criou a sala |
| `GET /api/salas/K7QF2M?desde=12` | a vista da sala, abaixo |
| `POST /api/salas/K7QF2M/jogadas` com `{"acao": {...}, "cursor": 12}` | a vista da sala; `400` se a jogada não vale, `409` se a partida andou depois do cursor |
| `POST /api/salas/K7QF2M/jogar-de-novo` | a vista da sala, já com a partida nova se todos pediram; `409` antes do fim ou se alguém saiu |
| `POST /api/salas/K7QF2M/sair` | a vista da sala; `409` antes do fim |

O código pode vir em minúsculas, com espaço ou hífen. Todas as rotas de uma sala, menos `entrar`, pedem `Authorization: Bearer <token>` (sem ele, ou de outra sala, `401`). A vista da sala não tem a seed:

```json
{"situacao": "jogando", "formato": "2x2", "voce": "lugar3", "anfitriao": "lugar1",
 "lugares": [{"id": "lugar1", "equipe": "dupla1", "nome": "Ana", "bot": false},
             {"id": "lugar2", "equipe": "dupla2", "nome": "Bot 2", "bot": true}, "..."],
 "partida": 1, "queremJogarDeNovo": [], "sairam": [], "cursor": 15,
 "passos": [{"eventos": [{"evento": {"tipo": "CartaJogada", "...": "..."}, "texto": "Bot 2 jogou 5 de copas."}], "visao": {"...": "..."}}],
 "acoesLegais": [{"acao": {"tipo": "JogarCarta", "indiceNaMao": 0}, "texto": "Jogar 2 de bastos"}]}
```

- `situacao` é `esperando-jogadores`, `jogando` ou `finalizada`; `formato`, `1x1` ou `2x2`.
- `lugares` vem na ordem da mesa, com `nome` nulo nos lugares vazios da sala de espera.
- `partida` é 1 na primeira partida da sala e cresce a cada vez que jogam de novo. `queremJogarDeNovo` e `sairam` dizem quem, depois do fim, pediu outra partida e quem saiu.
- `passos` vai do cursor `desde` até agora, e `cursor` é o próximo a pedir. O cursor continua contando de uma partida para a outra: com o cursor da partida anterior, `passos` traz a partida nova desde o começo, e uma jogada volta `409`.

`GET /saude` responde `ok`; é como o Render sabe que o servidor está de pé.

## O baralho espanhol

A página mostra as cartas como o baralho espanhol de 40 cartas. É só aparência: as regras e o protocolo continuam com as cartas do Truco Paulista.

| No core e no protocolo | Na página |
|---|---|
| copas, espadas, ouros | copas, espadas, ouros |
| paus | bastos |
| A, 2 a 7 | ás (1), 2 a 7 |
| 10 | sota (10) |
| Q | cavalo (11) |
| J | rei (12) |

As figuras seguem a ordem de força do paulista (10 < Q < J), que é a mesma do espanhol (sota < cavalo < rei). Os textos usam os mesmos nomes ("4 de bastos", "cavalo de ouros") pela `Notacao.BARALHO_ESPANHOL` da CLI.

### Crédito das cartas

As imagens são da folha [“Baraja española completa”](https://commons.wikimedia.org/wiki/File:Baraja_espa%C3%B1ola_completa.png), de Basquetteur, feita a partir do trabalho de Germarquezm, publicada no Wikimedia Commons sob a licença [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/deed.pt-br).

- A folha está no repositório sem nenhuma alteração, em `truco-web/src/main/resources/web/baralho-espanhol.png`. O CSS só mostra o pedaço de cada carta.
- A grade tem 12 colunas (valores 1 a 12) e 5 fileiras de 208 × 319 px: ouros, copas, espadas, bastos e, na última, uma carta em branco e o verso.
- A licença exige o crédito, que aparece no rodapé da página. Se um dia a imagem for editada, a versão editada também precisa sair sob CC BY-SA.

## Rodar no seu computador

No PowerShell, na pasta do projeto, com o JDK 21:

```powershell
$env:JAVA_HOME = "C:\Users\joaom\.jdks\corretto-21.0.12"; ./mvnw -q package -DskipTests
```

```powershell
& "$env:JAVA_HOME\bin\java" -jar truco-web/target/truco-web.jar
```

Depois abra http://localhost:8080. Para usar outra porta, defina a variável `PORT` antes de rodar.

- `http://localhost:8080/?seed=2026` começa uma partida contra o bot com essa seed, e `http://localhost:8080/?sala=K7QF2M` entra na sala com esse código.
- Contra o bot, o histórico mostra a seed e um link para jogar a mesma partida de novo; numa sala, mostra o código.

## Publicar de graça no Render

O Render roda a imagem do `Dockerfile` da raiz no plano gratuito, como descrito no `render.yaml`. O plano gratuito tem três limitações:

- **Dorme sem uso:** depois de 15 minutos sem acesso, o servidor dorme, e o primeiro acesso depois disso leva cerca de 1 minuto. A página avisa enquanto espera. Numa sala, as perguntas a cada segundo mantêm o servidor acordado enquanto vocês jogam.
- **Horas por mês:** são 750 horas de servidor ligado por mês.
- **Pode reiniciar:** o servidor pode reiniciar a qualquer momento. Isso não afeta as partidas contra o bot, porque ele não guarda nada delas, mas apaga as salas, que ficam só na memória.

Passo a passo:

1. Garanta que a branch `feat/frontend-web` está no GitHub (`git push -u origin feat/frontend-web`).
2. Crie uma conta em https://render.com com **Sign in with GitHub**.
3. No painel, clique em **New** → **Blueprint**.
4. Autorize o Render a ler o repositório `joaomarcosvs/truco`; pode ser só esse repositório.
5. Escolha o repositório e a branch `feat/frontend-web`. O Render lê o `render.yaml` e mostra o serviço `truco` no plano **Free**. Confirme com **Apply**.
6. Espere o primeiro build: ele baixa o Maven, compila e monta a imagem, o que leva alguns minutos. Os logs aparecem no painel.
7. Quando o serviço ficar **Live**, o endereço aparece no topo, como `https://truco-xxxx.onrender.com`.

A cada push na branch `feat/frontend-web`, o Render publica a versão nova sozinho.

Se o Blueprint não aceitar o plano gratuito, crie o serviço à mão:

1. **New** → **Web Service** → escolha o repositório.
2. Em **Branch**, escolha `feat/frontend-web`; em **Language**, Docker; em **Instance Type**, Free.
3. Em **Health Check Path**, use `/saude`.

O endereço é público: qualquer pessoa com o link pode jogar. Para tirar do ar, apague o serviço no painel do Render.
