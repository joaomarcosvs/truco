# Frontend web de teste

Uma página para jogar contra o `BotAleatorio` no navegador e ver o core funcionando. É uma ferramenta de desenvolvimento, como a CLI: **não é o servidor de verdade** (sem contas, sem multiplayer, sem partidas guardadas no servidor).

## Como funciona

- O módulo `truco-web` sobe um servidor HTTP só com o JDK (`com.sun.net.httpserver`) e serve a página, que fica em `truco-web/src/main/resources/web/`: `index.html`, `estilo.css`, `mesa.js` (a mesa), `cartas.js` (o baralho) e `baralho-espanhol.png` (a folha com as cartas).
- O servidor não guarda nada. A página guarda a **seed** e a **lista das suas jogadas** (também no `localStorage` do navegador) e manda as duas a cada pedido. O servidor refaz a partida inteira a partir delas, o que leva milissegundos, e responde. Isso só funciona porque o core é determinístico: a mesma seed com as mesmas ações dá a mesma partida, e o bot também tem seed. Por isso a partida sobrevive ao servidor gratuito dormir ou reiniciar.
- A página só recebe a **sua visão** e os **eventos que você pode ver**, no formato de `docs/protocolo.md`, como um cliente de verdade receberia. Cada evento e cada ação vêm junto com o texto da CLI. A página não tem regra nenhuma: só desenha e manda as jogadas.
- Como a página conhece a seed, dá para calcular as cartas do bot a partir dela. Contra um bot isso não tem importância. Um servidor de verdade guardaria o estado do lado dele.

### API

`POST /api/partida` com `{"seed": 2026, "acoes": [ ...suas jogadas, no formato do protocolo... ]}`. Sem `seed`, o servidor sorteia uma. A resposta traz:

```json
{"seed": 2026,
 "passos": [{"eventos": [{"evento": {"tipo": "CartaJogada", "...": "..."}, "texto": "Bot jogou 5 de copas."}], "visao": {"...": "..."}}],
 "acoesLegais": [{"acao": {"tipo": "JogarCarta", "indiceNaMao": 0}, "texto": "Jogar 2 de bastos"}]}
```

- `passos` vai da sua última jogada até agora, com a visão depois de cada passo. A página usa isso para mostrar as jogadas do bot uma a uma.
- `acoesLegais` traz o que você pode fazer agora; vem vazio quando a partida acaba.
- Uma jogada ilegal, ou um pedido malformado, recebe `400` com `{"erro": "..."}`.

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

- `http://localhost:8080/?seed=2026` começa a partida com essa seed.
- O rodapé da página mostra a seed e um link para jogar a mesma partida de novo.

## Publicar de graça no Render

O Render roda a imagem do `Dockerfile` da raiz no plano gratuito, como descrito no `render.yaml`. O plano gratuito tem três limitações:

- **Dorme sem uso:** depois de 15 minutos sem acesso, o servidor dorme, e o primeiro acesso depois disso leva cerca de 1 minuto. A página avisa enquanto espera.
- **Horas por mês:** são 750 horas de servidor ligado por mês.
- **Pode reiniciar:** o servidor pode reiniciar a qualquer momento. Isso não afeta as partidas, porque ele não guarda nada.

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
