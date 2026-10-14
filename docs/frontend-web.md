# Frontend web de teste

Uma página para jogar contra o `BotAleatorio` no navegador e ver o core funcionando. É uma ferramenta de desenvolvimento, como a CLI: **não é o servidor de verdade** (sem contas, sem multiplayer, sem partidas guardadas no servidor).

## Como funciona

- O módulo `truco-web` sobe um servidor HTTP só com o JDK (`com.sun.net.httpserver`) e serve a página (`index.html`, `estilo.css`, `mesa.js`, em `truco-web/src/main/resources/web/`).
- O servidor não guarda nada. A página guarda a **seed** e a **lista das suas jogadas** (também no `localStorage` do navegador) e manda as duas a cada pedido. O servidor refaz a partida inteira a partir delas, o que leva milissegundos, e responde. Isso só funciona porque o core é determinístico: a mesma seed com as mesmas ações dá a mesma partida, e o bot também tem seed. Por isso a partida sobrevive ao servidor gratuito dormir ou reiniciar.
- A página só recebe a **sua visão** e os **eventos que você pode ver**, no formato de `docs/protocolo.md`, como um cliente de verdade receberia. Cada evento e cada ação vêm junto com o texto da CLI. A página não tem regra nenhuma: só desenha e manda as jogadas.
- Como a página conhece a seed, dá para calcular as cartas do bot a partir dela. Contra um bot isso não tem importância. Um servidor de verdade guardaria o estado do lado dele.

### API

`POST /api/partida` com `{"seed": 2026, "acoes": [ ...suas jogadas, no formato do protocolo... ]}`. Sem `seed`, o servidor sorteia uma. A resposta traz:

```json
{"seed": 2026,
 "passos": [{"eventos": [{"evento": {"tipo": "CartaJogada", "...": "..."}, "texto": "Bot jogou 5♥."}], "visao": {"...": "..."}}],
 "acoesLegais": [{"acao": {"tipo": "JogarCarta", "indiceNaMao": 0}, "texto": "Jogar 2♣"}]}
```

- `passos` vai da sua última jogada até agora, com a visão depois de cada passo. A página usa isso para mostrar as jogadas do bot uma a uma.
- `acoesLegais` traz o que você pode fazer agora; vem vazio quando a partida acaba.
- Uma jogada ilegal, ou um pedido malformado, recebe `400` com `{"erro": "..."}`.

`GET /saude` responde `ok`; é como o Render sabe que o servidor está de pé.

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
