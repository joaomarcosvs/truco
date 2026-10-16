'use strict';

// A página só conhece a sua visão e os eventos que você pode ver, no formato de docs/protocolo.md, como o cliente Unity.
// Contra o bot no 1x1, o servidor não guarda nada: a página guarda a seed e as suas jogadas, e manda tudo a cada pedido.
// Numa sala (com amigos, ou no 2x2 contra bots), a partida fica na memória do servidor: a página guarda o código e o
// seu token, pergunta o que mudou a cada segundo e manda as suas jogadas.

const CHAVE_MODO = 'truco-modo';
const CHAVE_BOT = 'truco-partida';
const CHAVE_SALA = 'truco-sala';
const CHAVE_NOME = 'truco-nome';
const PAUSA = 550;
const PAUSA_ULTIMA_CARTA = 1600;
const PAUSA_FIM_DE_RODADA = 1900;
const INTERVALO_DA_SALA = 1000;
// Só para os rótulos dos botões de aumento; as regras ficam no servidor.
const NIVEIS = { 3: 'Truco', 6: 'Seis', 9: 'Nove', 12: 'Doze' };
/** Os lugares da partida 1x1 contra o bot, que não passa por sala. */
const LUGARES_CONTRA_O_BOT = [
  { id: 'voce', equipe: 'voce', nome: 'você', bot: false },
  { id: 'bot', equipe: 'bot', nome: 'bot', bot: true },
];

const estado = {
  modo: null, // 'bot' ou 'sala'
  eu: 'voce',
  lugares: [], // [{ id, equipe, nome, bot }], na ordem da mesa
  nomes: {}, // id de jogador ou de equipe → como aparece na tela
  geracao: 0, // muda a cada partida nova, para ignorar respostas da anterior
  // Contra o bot
  seed: null,
  acoes: [],
  // Na sala
  sala: null, // { codigo, token, voce }
  anfitriao: null,
  formato: '1x1',
  cursor: 0,
  situacao: null,
  partidaDaSala: null, // 1 na primeira partida da sala, 2 depois de jogarem de novo...
  queremJogarDeNovo: [],
  sairam: [],
  esperaNaTela: '', // o que a sala de espera mostra, para não redesenhá-la à toa
  fimNaTela: '', // idem, para o aviso de fim de partida
  erroNaTela: false,
  // Mesa
  ocupado: false,
  ultima: null, // { acoes, visao }: o que está desenhado agora
  ultimaJogada: null, // a sua última ação, para mostrar a sua encoberta no fim da rodada
  rodadaNoConsole: null,
  placar: null,
};

const $ = (id) => document.getElementById(id);

function elemento(tag, classe, texto) {
  const el = document.createElement(tag);
  if (classe) el.className = classe;
  if (texto !== undefined) el.textContent = texto;
  return el;
}

const esperar = (ms) => new Promise((pronto) => setTimeout(pronto, ms));
const nome = (id) => estado.nomes[id] ?? id;
const comMaiuscula = (texto) => texto.charAt(0).toUpperCase() + texto.slice(1);
const plural = (n, palavra) => `${n} ${palavra}${n === 1 ? '' : 's'}`;
const lista = (nomes) => (nomes.length <= 1 ? nomes.join('') : `${nomes.slice(0, -1).join(', ')} e ${nomes.at(-1)}`);

// ---------- Lugares, equipes e nomes ----------

const duplas = () => estado.lugares.length === 4;
/** A equipe do jogador; um id de equipe dá a própria equipe. */
const equipeDe = (id) => estado.lugares.find((lugar) => lugar.id === id)?.equipe ?? id;
const minhaEquipe = () => equipeDe(estado.eu);
const outraEquipe = () => estado.lugares.map((lugar) => lugar.equipe).find((equipe) => equipe !== minhaEquipe());
const parceiros = () => estado.lugares.filter((lugar) => lugar.id !== estado.eu && lugar.equipe === minhaEquipe());
const adversarios = () => estado.lugares.filter((lugar) => lugar.equipe !== minhaEquipe());

/** Os lugares e quem é você; os nomes das equipes seguem o narrador do servidor ("sua dupla", "dupla adversária"). */
function definirLugares(lugares, eu) {
  estado.lugares = lugares;
  estado.eu = eu;
  const nomes = {};
  for (const lugar of lugares) nomes[lugar.id] = lugar.id === eu ? 'você' : lugar.nome ?? 'lugar vazio';
  for (const lugar of lugares) {
    if (!(lugar.equipe in nomes)) {
      nomes[lugar.equipe] = lugar.equipe === equipeDe(eu) ? 'sua dupla' : 'dupla adversária';
    }
  }
  estado.nomes = nomes;
  $('nome-voce-placar').textContent = ['Você', ...parceiros().filter((lugar) => lugar.nome).map((lugar) => nome(lugar.id))].join(' e ');
  // Na sala de espera, só quem já sentou; com os lugares do outro lado vazios, um traço.
  const sentados = adversarios().filter((lugar) => lugar.nome);
  $('nome-no-placar').textContent = sentados.length > 0 ? lista(sentados.map((lugar) => comMaiuscula(nome(lugar.id)))) : '—';
}

/** A cor de um jogador ou de uma equipe: a sua dupla em dourado, a outra em azul. */
function lado(id) {
  if (id === undefined) return 'geral';
  const equipe = equipeDe(id);
  if (equipe === minhaEquipe()) return 'voce';
  return estado.lugares.some((lugar) => lugar.equipe === equipe) ? 'adversario' : 'geral';
}

/**
 * Onde o jogador senta na tela: você embaixo; no 1x1, o outro em cima; no 2x2, o parceiro em cima, o adversário que
 * joga depois de você (à sua direita) à direita e o outro à esquerda.
 */
function posicao(id) {
  const n = estado.lugares.length;
  const indice = (jogador) => estado.lugares.findIndex((lugar) => lugar.id === jogador);
  const distancia = (indice(id) - indice(estado.eu) + n) % n;
  if (n === 2) return distancia === 0 ? 'baixo' : 'topo';
  return ['baixo', 'direita', 'topo', 'esquerda'][distancia];
}

// ---------- Guardar no navegador (para sobreviver a um recarregar) ----------

function guardar(chave, valor) {
  try {
    localStorage.setItem(chave, JSON.stringify(valor));
  } catch (e) {
    // Sem armazenamento (aba anônima, por exemplo): a partida só não sobrevive a um recarregar.
  }
}

function ler(chave) {
  try {
    return JSON.parse(localStorage.getItem(chave));
  } catch (e) {
    return null; // Ignora o que estiver salvo de forma inválida.
  }
}

function esquecer(chave) {
  try {
    localStorage.removeItem(chave);
  } catch (e) {
    // Nada a fazer.
  }
}

function botSalvo() {
  const salvo = ler(CHAVE_BOT);
  return salvo && Number.isSafeInteger(salvo.seed) && Array.isArray(salvo.acoes) ? salvo : null;
}

function salaSalva() {
  const salva = ler(CHAVE_SALA);
  return salva && typeof salva.codigo === 'string' && typeof salva.token === 'string' && typeof salva.voce === 'string'
    ? salva
    : null;
}

// ---------- Conversa com o servidor ----------

class ErroDoServidor extends Error {
  constructor(mensagem, status) {
    super(mensagem);
    this.status = status;
  }
}

async function pedir(url, { metodo = 'GET', corpo, token } = {}) {
  const demorando = setTimeout(() => {
    avisar([elemento('p', '', 'Acordando o servidor… No plano gratuito ele dorme quando ninguém joga, e o primeiro acesso pode levar até um minuto.')]);
    estado.erroNaTela = true;
  }, 2000);
  const cabecalhos = {};
  if (corpo !== undefined) cabecalhos['Content-Type'] = 'application/json';
  if (token) cabecalhos.Authorization = `Bearer ${token}`;
  try {
    const resposta = await fetch(url, {
      method: metodo,
      headers: cabecalhos,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
    const json = await resposta.json().catch(() => ({}));
    if (!resposta.ok) throw new ErroDoServidor(json.erro || `O servidor respondeu ${resposta.status}`, resposta.status);
    return json;
  } catch (erro) {
    if (erro instanceof TypeError) throw new ErroDoServidor('Não foi possível falar com o servidor. Tente de novo.', 0);
    throw erro;
  } finally {
    clearTimeout(demorando);
  }
}

/** Mostra os passos um a um (eventos no histórico, mesa depois de cada passo) e desenha as ações. */
async function mostrar(passos, acoesLegais, animar) {
  let visao = estado.ultima?.visao ?? null;
  for (const [i, passo] of passos.entries()) {
    if (animar && i > 0) await esperar(PAUSA);
    passo.eventos.forEach(registrar);
    if (animar && terminaRodada(passo)) {
      // A última carta da rodada chega junto com a rodada seguinte: antes do resumo, a mesa mostra como a rodada acabou.
      const fim = mesaNoFimDaRodada(visao, passo.eventos);
      if (fim) {
        desenharMesa(fim);
        await esperar(PAUSA_ULTIMA_CARTA);
      }
      mostrarResumo(passo);
      await esperar(PAUSA_FIM_DE_RODADA);
      esconderAviso();
    }
    visao = passo.visao;
    desenharMesa(visao);
  }
  estado.ultima = { acoes: acoesLegais, visao };
  desenharAcoes(acoesLegais, visao);
}

async function ocupado(tarefa) {
  estado.ocupado = true;
  try {
    return await tarefa();
  } finally {
    estado.ocupado = false;
  }
}

async function ateFicarLivre() {
  while (estado.ocupado) await esperar(50);
}

/**
 * A mesa depois da última carta da rodada, montada com a visão de antes e os eventos dessa carta: a jogada entra na
 * vaza, e a vaza ganha o resultado. Vazio se a rodada acabou sem carta (alguém correu).
 */
function mesaNoFimDaRodada(anterior, eventos) {
  const evento = (tipo) => eventos.map((e) => e.evento).find((e) => tipo.includes(e.tipo));
  const jogou = evento(['CartaJogada', 'CartaEncobertaJogada']);
  if (!anterior || !jogou) return null;
  const encoberta = jogou.tipo === 'CartaEncobertaJogada';
  let carta = encoberta ? null : jogou.carta;
  const mao = [...anterior.mao];
  if (jogou.jogador === estado.eu) {
    // A sua encoberta você vê: é a carta da posição que você escolheu.
    const indice = encoberta
      ? estado.ultimaJogada?.indiceNaMao ?? -1
      : mao.findIndex((c) => c.valor === carta.valor && c.naipe === carta.naipe);
    if (indice >= 0 && indice < mao.length) {
      if (encoberta) carta = mao[indice];
      mao.splice(indice, 1);
    }
  }
  const jogadas = [...anterior.vazaAtual, { jogador: jogou.jogador, carta, encoberta }];
  const fimDaVaza = evento(['VazaFinalizada']);
  return {
    ...anterior,
    mao,
    maosDosParceiros: {},
    cartasNaMao: { ...anterior.cartasNaMao, [jogou.jogador]: Math.max(0, (anterior.cartasNaMao[jogou.jogador] ?? 1) - 1) },
    vazas: fimDaVaza ? [...anterior.vazas, { jogadas, resultado: fimDaVaza.resultado }] : anterior.vazas,
    vazaAtual: fimDaVaza ? [] : jogadas,
    aposta: { ...anterior.aposta, pedidoPendente: null },
    cartaDaVezNoDescarte: null,
    vezDe: null,
  };
}

function jogar(acao) {
  estado.ultimaJogada = acao;
  if (estado.modo === 'bot') jogarContraOBot(acao);
  else if (estado.modo === 'sala') jogarNaSala(acao);
}

// ---------- Começar uma partida ----------

function prepararPartida(modo, eu, lugares) {
  estado.geracao += 1;
  estado.modo = modo;
  estado.erroNaTela = false;
  estado.partidaDaSala = null;
  estado.queremJogarDeNovo = [];
  estado.sairam = [];
  guardar(CHAVE_MODO, modo);
  definirLugares(lugares, eu);
  limparMesa();
  mostrarJogo();
}

/** A mesa, o placar e o histórico vazios, para uma partida nova. */
function limparMesa() {
  estado.ultima = null;
  estado.ultimaJogada = null;
  estado.placar = null;
  estado.rodadaNoConsole = null;
  $('pontos-voce').textContent = '0';
  $('pontos-adversario').textContent = '0';
  $('historico').replaceChildren();
  ['assento-topo', 'assento-esquerda', 'assento-direita', 'mao-voce', 'vira', 'vazas', 'info', 'acoes'].forEach((id) => $(id).replaceChildren());
  $('dica').textContent = '';
  $('mesa').classList.remove('na-espera');
  esconderAviso();
}

function emAndamento() {
  const acabou = Boolean(estado.ultima?.visao?.vencedoraDaPartida);
  if (estado.modo === 'bot') return estado.acoes.length > 0 && !acabou;
  if (estado.modo === 'sala') return estado.situacao === 'jogando' && !acabou;
  return false;
}

/**
 * Antes de outra partida: confirma se a atual está em andamento. Se a atual é uma sala que acabou, avisa que você saiu
 * dela, para os outros não esperarem você jogar de novo (a não ser que você esteja voltando para a mesma sala).
 */
function podeTrocarDePartida(codigoDaProxima) {
  if (emAndamento() && !confirm('Abandonar a partida em andamento?')) return false;
  if (estado.modo === 'sala' && estado.situacao === 'finalizada' && estado.sala?.codigo !== codigoDaProxima) {
    avisarQueSaiu(estado.sala);
  }
  return true;
}

// ---------- Contra o bot (1x1, sem sala) ----------

async function novaPartidaContraOBot(seed) {
  prepararPartida('bot', 'voce', LUGARES_CONTRA_O_BOT);
  estado.acoes = [];
  await executarContraOBot(seed === null ? { acoes: [] } : { seed, acoes: [] }, true);
}

async function retomarContraOBot(salvo) {
  prepararPartida('bot', 'voce', LUGARES_CONTRA_O_BOT);
  estado.acoes = salvo.acoes;
  nota('Partida retomada de onde você parou.');
  const deuCerto = await executarContraOBot({ seed: salvo.seed, acoes: salvo.acoes }, false);
  if (!deuCerto) await novaPartidaContraOBot(null);
}

async function executarContraOBot(corpo, animar) {
  const geracao = estado.geracao;
  return ocupado(async () => {
    desenharAcoes([], null);
    $('dica').textContent = '';
    try {
      const resposta = await pedir('api/partida', { metodo: 'POST', corpo });
      if (geracao !== estado.geracao) return false;
      esconderAviso();
      estado.seed = resposta.seed;
      guardar(CHAVE_BOT, { seed: estado.seed, acoes: estado.acoes });
      $('console-info').replaceChildren(
        'Seed ', elemento('strong', '', String(estado.seed)), ' · ', link(`?seed=${estado.seed}`, 'jogar esta partida de novo'),
      );
      await mostrar(resposta.passos, resposta.acoesLegais, animar);
      return true;
    } catch (erro) {
      avisar([elemento('p', '', erro.message)], 'erro');
      if (estado.ultima) desenharAcoes(estado.ultima.acoes, estado.ultima.visao);
      return false;
    }
  });
}

async function jogarContraOBot(acao) {
  if (estado.ocupado) return;
  estado.acoes.push(acao);
  const deuCerto = await executarContraOBot({ seed: estado.seed, acoes: estado.acoes }, true);
  if (!deuCerto) estado.acoes.pop();
}

// ---------- Numa sala ----------

function nomeDigitado() {
  return $('nome').value.trim();
}

/** O nome para a sala; sem ele, volta ao menu pedindo. */
function exigirNome() {
  const nomeDaPessoa = nomeDigitado();
  if (!nomeDaPessoa) {
    mostrarInicio('Escreva seu nome para jogar numa sala.');
    $('nome').focus();
  }
  return nomeDaPessoa;
}

async function criarSala(formato) {
  const nomeDaPessoa = exigirNome();
  if (!nomeDaPessoa) return;
  try {
    const sala = await pedir('api/salas', { metodo: 'POST', corpo: { formato, nome: nomeDaPessoa } });
    entrarNaSala(sala);
  } catch (erro) {
    mostrarInicio(erro.message);
  }
}

/** No 2x2 contra bots: uma sala com você, começada na hora; os três outros lugares viram bots. */
async function contraBotsEmDuplas() {
  try {
    const sala = await pedir('api/salas', { metodo: 'POST', corpo: { formato: '2x2', nome: nomeDigitado() || 'Jogador' } });
    await pedir(`api/salas/${sala.codigo}/comecar`, { metodo: 'POST', token: sala.token, corpo: {} });
    entrarNaSala(sala);
  } catch (erro) {
    mostrarInicio(erro.message);
  }
}

/** Abre a sala do código: com o token guardado, volta para ela; sem ele, entra com o nome digitado. */
/** O código como o servidor o guarda: sem espaços nem hífens, em maiúsculas. */
const codigoDaSala = (digitado) => digitado.replace(/[\s-]/g, '').toUpperCase();

async function abrirSala(digitado) {
  const codigo = codigoDaSala(digitado);
  const salva = salaSalva();
  if (salva && salva.codigo === codigo) {
    entrarNaSala(salva);
    return;
  }
  $('codigo-sala').value = codigo;
  const nomeDaPessoa = nomeDigitado();
  if (!nomeDaPessoa) {
    mostrarInicio(`Escreva seu nome e clique em Entrar para entrar na sala ${codigo}.`);
    $('nome').focus();
    return;
  }
  try {
    const sala = await pedir(`api/salas/${encodeURIComponent(codigo)}/entrar`, { metodo: 'POST', corpo: { nome: nomeDaPessoa } });
    entrarNaSala(sala);
  } catch (erro) {
    mostrarInicio(erro.message);
  }
}

function entrarNaSala(sala) {
  prepararPartida('sala', sala.voce, []);
  estado.sala = { codigo: sala.codigo, token: sala.token, voce: sala.voce };
  estado.cursor = 0;
  estado.situacao = null;
  guardar(CHAVE_SALA, estado.sala);
  $('console-info').replaceChildren('Sala ', elemento('strong', '', sala.codigo));
  acompanharSala(estado.geracao);
}

/**
 * Pergunta ao servidor o que mudou, a cada segundo, até você sair da sala ou começar outra coisa. Depois do fim também,
 * para ver quem quer jogar de novo e a partida nova começar.
 */
async function acompanharSala(geracao) {
  while (estado.modo === 'sala' && estado.geracao === geracao) {
    if (!estado.ocupado) await atualizarSala(geracao);
    await esperar(INTERVALO_DA_SALA);
  }
}

async function atualizarSala(geracao) {
  await ocupado(async () => {
    const { codigo, token } = estado.sala;
    try {
      const resposta = await pedir(`api/salas/${codigo}?desde=${estado.cursor}`, { token });
      if (geracao === estado.geracao) await processarSala(resposta);
    } catch (erro) {
      if (geracao === estado.geracao) tratarErroDaSala(erro);
    }
  });
}

async function processarSala(resposta) {
  if (estado.erroNaTela) {
    esconderAviso();
    estado.erroNaTela = false;
  }
  estado.situacao = resposta.situacao;
  estado.anfitriao = resposta.anfitriao;
  estado.formato = resposta.formato;
  estado.queremJogarDeNovo = resposta.queremJogarDeNovo;
  estado.sairam = resposta.sairam;
  if (estado.partidaDaSala !== null && resposta.partida !== estado.partidaDaSala) {
    // Todos pediram para jogar de novo: os passos que chegaram são os da partida nova, desde o começo.
    limparMesa();
  }
  estado.partidaDaSala = resposta.partida;
  if (resposta.voce !== estado.sala.voce) {
    // Na sala de espera, trocar de lugar muda quem você é na mesa.
    estado.sala.voce = resposta.voce;
    guardar(CHAVE_SALA, estado.sala);
  }
  if (JSON.stringify(resposta.lugares) !== JSON.stringify(estado.lugares) || resposta.voce !== estado.eu) {
    definirLugares(resposta.lugares, resposta.voce);
  }
  $('mesa').classList.toggle('na-espera', resposta.situacao === 'esperando-jogadores');
  if (resposta.situacao === 'esperando-jogadores') {
    mostrarEspera();
    return;
  }
  if (estado.esperaNaTela) {
    esconderAviso();
    estado.esperaNaTela = '';
  }
  if (resposta.passos.length > 0 || !estado.ultima) {
    desenharAcoes([], null);
    // Ao voltar para a sala, o histórico inteiro chega de uma vez: sem animação.
    await mostrar(resposta.passos, resposta.acoesLegais, estado.cursor > 0);
  }
  estado.cursor = resposta.cursor;
  // Depois do fim, o aviso acompanha quem quer jogar de novo e quem saiu.
  if (resposta.situacao === 'finalizada' && estado.ultima?.visao?.vencedoraDaPartida) mostrarFim(estado.ultima.visao);
}

async function jogarNaSala(acao) {
  const geracao = estado.geracao;
  const cursorVisto = estado.cursor;
  await ateFicarLivre();
  if (geracao !== estado.geracao) return;
  if (estado.cursor !== cursorVisto) {
    nota('Alguém agiu antes da sua jogada. Veja a mesa e jogue de novo.');
    return;
  }
  await ocupado(async () => {
    desenharAcoes([], null);
    const { codigo, token } = estado.sala;
    try {
      const resposta = await pedir(`api/salas/${codigo}/jogadas`, { metodo: 'POST', token, corpo: { acao, cursor: cursorVisto } });
      if (geracao === estado.geracao) await processarSala(resposta);
    } catch (erro) {
      if (geracao !== estado.geracao) return;
      if (erro.status === 409) {
        nota('Alguém agiu antes da sua jogada. Veja a mesa e jogue de novo.');
      } else {
        tratarErroDaSala(erro);
      }
      if (estado.ultima) desenharAcoes(estado.ultima.acoes, estado.ultima.visao);
    }
  });
  if (geracao === estado.geracao && estado.cursor === cursorVisto) await atualizarSala(geracao);
}

/**
 * Uma mudança na sala: na espera, sentar noutro lugar ou, para quem criou a sala, começar a partida; depois do fim,
 * pedir para jogar de novo.
 */
async function mudarSala(caminho, corpo) {
  const geracao = estado.geracao;
  await ateFicarLivre();
  await ocupado(async () => {
    const { codigo, token } = estado.sala;
    try {
      const resposta = await pedir(`api/salas/${codigo}/${caminho}`, { metodo: 'POST', token, corpo });
      if (geracao === estado.geracao) await processarSala(resposta);
    } catch (erro) {
      if (geracao === estado.geracao) tratarErroDaSala(erro);
    }
  });
}

function tratarErroDaSala(erro) {
  if (erro.status === 404 || erro.status === 401) {
    // A sala expirou ou o servidor reiniciou: não há o que retomar.
    esquecer(CHAVE_SALA);
    estado.modo = null;
    estado.geracao += 1;
    mostrarInicio(erro.message);
    return;
  }
  avisar([elemento('p', '', erro.message)], 'erro');
  estado.erroNaTela = true;
}

/** Depois do fim, você sai da sala e volta ao menu; o servidor avisa os outros. */
function sairDaSala() {
  const sala = estado.sala;
  esquecer(CHAVE_SALA);
  estado.modo = null;
  estado.geracao += 1;
  mostrarInicio();
  avisarQueSaiu(sala);
}

/** Avisa o servidor que você saiu de uma sala que acabou. Se a sala já fechou, não há quem avisar. */
function avisarQueSaiu(sala) {
  fetch(`api/salas/${sala.codigo}/sair`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${sala.token}` },
    body: '{}',
  }).catch(() => {});
}

/** A sala de espera: o código, o link e, no 2x2, os lugares das duas duplas. */
function mostrarEspera() {
  const assinatura = JSON.stringify([estado.lugares, estado.eu, estado.anfitriao]);
  if (estado.esperaNaTela === assinatura) return;
  const endereco = new URL(location.pathname, location.origin);
  endereco.searchParams.set('sala', estado.sala.codigo);
  const copiar = elemento('button', 'botao discreto', 'Copiar link');
  copiar.type = 'button';
  copiar.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(endereco.href);
      copiar.textContent = 'Link copiado!';
    } catch (e) {
      copiar.textContent = 'Copie o link ao lado';
    }
  });
  const linkDaSala = elemento('div', 'link-da-sala');
  linkDaSala.append(elemento('code', '', endereco.href), copiar);
  const conteudo = [elemento('p', 'rotulo-da-sala', 'Sala'), elemento('h3', 'codigo-da-sala', estado.sala.codigo), linkDaSala];
  if (!duplas()) {
    conteudo.push(
      elemento('p', '', 'Mande o link para o seu amigo. A partida começa quando ele entrar.'),
      elemento('p', 'esperando', 'Esperando seu amigo'),
    );
  } else {
    conteudo.push(elemento('p', '', 'Mande o link para os amigos. Cada um escolhe um lugar; parceiros jogam frente a frente.'));
    const duplasNaMesa = elemento('div', 'duplas-da-sala');
    for (const equipe of [...new Set(estado.lugares.map((lugar) => lugar.equipe))]) {
      const coluna = elemento('div', `dupla-da-sala ${lado(equipe)}`);
      coluna.append(elemento('strong', '', equipe === minhaEquipe() ? 'Sua dupla' : 'Outra dupla'));
      for (const lugar of estado.lugares.filter((l) => l.equipe === equipe)) coluna.append(lugarNaEspera(lugar));
      duplasNaMesa.append(coluna);
    }
    conteudo.push(duplasNaMesa);
    if (estado.anfitriao === estado.eu) {
      const comecar = elemento('button', 'botao aumento', 'Começar');
      comecar.type = 'button';
      comecar.addEventListener('click', () => mudarSala('comecar', {}));
      conteudo.push(comecar, elemento('p', 'nota-da-sala', 'Os lugares vazios viram bots.'));
    } else {
      conteudo.push(elemento('p', 'esperando', `Esperando ${nome(estado.anfitriao)} começar`));
    }
  }
  avisar(conteudo, 'espera');
  estado.esperaNaTela = assinatura;
}

function lugarNaEspera(lugar) {
  const el = elemento('div', `lugar-na-espera${lugar.id === estado.eu ? ' seu' : ''}`);
  if (lugar.nome) {
    el.append(elemento('span', '', lugar.id === estado.eu ? `${lugar.nome} (você)` : lugar.nome));
  } else {
    el.append(elemento('span', 'vazio', 'Lugar vazio'));
    const sentar = elemento('button', 'botao-carta', 'sentar aqui');
    sentar.type = 'button';
    sentar.addEventListener('click', () => mudarSala('lugar', { lugar: lugar.id }));
    el.append(sentar);
  }
  return el;
}

// ---------- Mesa ----------

function desenharMesa(visao) {
  atualizarPlacar(visao.placar);

  for (const pos of ['topo', 'esquerda', 'direita']) $(`assento-${pos}`).hidden = true;
  for (const lugar of estado.lugares) {
    if (lugar.id === estado.eu) continue;
    const assento = $(`assento-${posicao(lugar.id)}`);
    assento.hidden = false;
    desenharAssento(assento, lugar.id, visao);
  }

  desenharInfo(visao);

  $('vira').replaceChildren(visao.vira ? Baralho.carta(visao.vira, 'media') : elemento('div', 'sem-vira', 'sem vira'));

  const vazas = $('vazas');
  vazas.replaceChildren();
  vazas.classList.toggle('quatro', duplas());
  const todas = visao.vazas.map((v) => ({ jogadas: v.jogadas, resultado: v.resultado }));
  if (!visao.vencedoraDaPartida) todas.push({ jogadas: visao.vazaAtual, resultado: null, atual: true });
  while (todas.length < 3) todas.push({ jogadas: [], resultado: null });
  todas.slice(0, 3).forEach((vaza, i) => vazas.append(desenharVaza(vaza, i + 1)));

  const suaMao = $('mao-voce');
  if (visao.mao.length > 0) {
    suaMao.replaceChildren(...visao.mao.map((c) => slot(Baralho.carta(c, 'grande'))));
  } else {
    // Na Rodada Escurinho você não vê as próprias cartas, só as posições.
    const quantas = visao.cartasNaMao[estado.eu] ?? 0;
    suaMao.replaceChildren(...Array.from({ length: quantas }, (_, i) => slot(Baralho.verso('grande', String(i + 1)))));
  }
}

/** Um jogador da mesa: nome, quantas cartas tem e as cartas viradas; as do parceiro, abertas quando a dupla se vê. */
function desenharAssento(assento, id, visao) {
  const quantas = visao.cartasNaMao[id] ?? 0;
  const vistas = visao.maosDosParceiros?.[id];
  const tamanho = assento.classList.contains('lado') ? 'mini' : 'pequena';
  const jogador = elemento('div', 'jogador');
  const avatar = elemento('span', `avatar ${lado(id)}`, (nome(id).charAt(0) || '?').toUpperCase());
  avatar.setAttribute('aria-hidden', 'true');
  const textos = elemento('div');
  const detalhe = vistas
    ? 'você está vendo as cartas'
    : quantas === 0 ? 'sem cartas na mão' : `${plural(quantas, 'carta')} na mão`;
  textos.append(elemento('strong', '', comMaiuscula(nome(id))), elemento('span', 'detalhe', detalhe));
  jogador.append(avatar, textos);
  const mao = elemento('div', `mao leque${vistas ? ' aberta' : ''}`);
  mao.append(...(vistas
    ? vistas.map((carta) => Baralho.carta(carta, tamanho))
    : Array.from({ length: quantas }, () => Baralho.verso(tamanho))));
  assento.classList.toggle('da-vez', visao.vezDe === id);
  assento.replaceChildren(jogador, mao);
}

function slot(carta) {
  const el = elemento('div', 'slot');
  el.append(carta, elemento('div', 'botoes-carta'));
  return el;
}

function desenharInfo(visao) {
  const info = $('info');
  info.replaceChildren(elemento('span', 'etiqueta', `Rodada ${visao.numeroDaRodada}`));
  const valor = visao.aposta.valor;
  info.append(elemento('span', `etiqueta${valor > 1 ? ' valendo' : ''}`, `Vale ${plural(valor, 'ponto')}`));
  info.append(elemento('span', 'etiqueta', `${comMaiuscula(nome(visao.carteador))} deu as cartas`));
  const tipo = visao.tipoDaRodada;
  if (tipo.tipo === 'DeOnze') {
    info.append(elemento('span', 'etiqueta especial',
      `Rodada de Onze: ${nome(tipo.equipeComOnze)} ${tipo.decidiuJogar ? 'decidiu jogar' : 'decide se joga ou corre'}`));
  } else if (tipo.tipo === 'Escurinho') {
    info.append(elemento('span', 'etiqueta especial', 'Rodada Escurinho: ninguém vê as próprias cartas'));
  }
  const pedido = visao.aposta.pedidoPendente;
  if (pedido) {
    const nomeDoNivel = NIVEIS[pedido.nivelProposto];
    info.append(elemento('span', 'etiqueta pedido',
      `${comMaiuscula(nome(pedido.pedinte))} pediu ${nomeDoNivel ? nomeDoNivel.toLowerCase() : 'aumento'} (${pedido.nivelProposto})`));
    const querem = pedido.queremVerAsCartas ?? [];
    if (querem.length > 0) {
      info.append(elemento('span', 'etiqueta', `${comMaiuscula(lista(querem.map(nome)))} ${querem.length === 1 ? 'quer' : 'querem'} ver as cartas`));
    }
  }
  if (visao.cartaDaVezNoDescarte) {
    const chip = elemento('span', 'etiqueta');
    chip.append(Baralho.carta(visao.cartaDaVezNoDescarte, 'mini'), `Descarte: a carta da vez é ${Baralho.nome(visao.cartaDaVezNoDescarte)}`);
    info.append(chip);
  }
  if (visao.descartadas.length > 0) {
    const chip = elemento('span', 'etiqueta');
    chip.append('Descartadas', ...visao.descartadas.map((c) => Baralho.carta(c, 'mini')));
    info.append(chip);
  }
}

/** Uma vaza: no 1x1, a carta do outro em cima e a sua embaixo; no 2x2, as quatro em cruz, cada uma do lado de quem jogou. */
function desenharVaza(vaza, numero) {
  const el = elemento('div', `vaza${vaza.atual ? ' atual' : ''}`);
  el.append(elemento('span', 'rotulo', `${numero}ª vaza`));
  const quatro = duplas();
  const par = elemento('div', quatro ? 'par quatro' : 'par');
  const tamanho = quatro ? 'vaza4' : 'pequena';
  for (const pos of quatro ? ['topo', 'esquerda', 'direita', 'baixo'] : ['topo', 'baixo']) {
    const jogador = estado.lugares.find((lugar) => posicao(lugar.id) === pos)?.id;
    const jogada = vaza.jogadas.find((j) => j.jogador === jogador);
    let peca;
    if (!jogada) {
      peca = elemento('div', `lugar ${tamanho}`);
    } else if (!jogada.carta) {
      peca = Baralho.verso(tamanho);
    } else {
      peca = Baralho.carta(jogada.carta, tamanho);
      if (jogada.encoberta) peca.classList.add('apagada');
    }
    peca.classList.add(`pos-${pos}`);
    par.append(peca);
  }
  el.append(par);
  const resultado = elemento('span', 'resultado');
  if (vaza.resultado?.tipo === 'Vencida') {
    resultado.textContent = nome(vaza.resultado.jogador);
    resultado.classList.add(lado(vaza.resultado.jogador));
  } else if (vaza.resultado?.tipo === 'Empatada') {
    resultado.textContent = 'empate';
    resultado.classList.add('empate');
  }
  el.append(resultado);
  return el;
}

// ---------- Ações: jogar clicando na carta, e o resto em botões ----------

function desenharAcoes(acoes, visao) {
  const barra = $('acoes');
  barra.replaceChildren();
  const slots = $('mao-voce').children;
  for (const s of slots) {
    s.classList.remove('jogavel');
    s.querySelector('.botoes-carta').replaceChildren();
    s.firstChild.removeAttribute('tabindex');
  }

  const naBarra = [];
  for (const { acao, texto } of acoes) {
    const s = slots[acao.indiceNaMao];
    if (acao.tipo === 'JogarCarta' && s) {
      tornarJogavel(s, acao, texto);
    } else if (acao.tipo === 'JogarEncoberta' && s) {
      s.querySelector('.botoes-carta').append(botaoDaCarta('encoberta', texto, acao, ''));
    } else if (acao.tipo === 'Descartar' && s) {
      s.querySelector('.botoes-carta').append(botaoDaCarta('descartar', texto, acao, 'descartar'));
    } else {
      naBarra.push({ acao, texto });
    }
  }
  const ordem = ['Aceitar', 'PedirAumento', 'RecusarDescarte', 'Correr', 'VerCartasDoParceiro'];
  naBarra.sort((a, b) => ordem.indexOf(a.acao.tipo) - ordem.indexOf(b.acao.tipo));
  for (const { acao, texto } of naBarra) barra.append(botao(acao, texto));

  $('dica').textContent = dica(acoes, visao);
  if (visao?.vencedoraDaPartida) mostrarFim(visao);
}

function tornarJogavel(s, acao, texto) {
  s.classList.add('jogavel');
  const carta = s.firstChild;
  carta.setAttribute('tabindex', '0');
  carta.setAttribute('aria-label', texto);
  carta.addEventListener('click', () => jogar(acao));
  carta.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      jogar(acao);
    }
  });
}

function botaoDaCarta(rotulo, texto, acao, classe) {
  const b = elemento('button', `botao-carta ${classe}`.trim(), rotulo);
  b.type = 'button';
  b.title = texto;
  b.setAttribute('aria-label', texto);
  b.addEventListener('click', () => jogar(acao));
  return b;
}

function botao(acao, texto) {
  const { principal, detalhe } = rotulo(acao, texto);
  const classes = { Aceitar: 'aceitar', PedirAumento: 'aumento', Correr: 'correr', VerCartasDoParceiro: 'ver' };
  const b = elemento('button', `botao ${classes[acao.tipo] ?? 'neutro'}`);
  b.type = 'button';
  b.title = texto;
  b.append(elemento('span', '', principal));
  if (detalhe) b.append(elemento('small', '', detalhe));
  b.addEventListener('click', () => jogar(acao));
  return b;
}

/** "Correr (bot ganha 1 ponto)" vira "Correr" com "bot ganha 1 ponto" embaixo; os aumentos ganham o nome do nível. */
function rotulo(acao, texto) {
  if (acao.tipo === 'PedirAumento') {
    const nivel = Number((texto.match(/para (\d+)/) ?? [])[1]);
    if (NIVEIS[nivel]) {
      return { principal: `${NIVEIS[nivel]}!`, detalhe: texto.startsWith('Aceitar') ? `aceita e pede ${nivel}` : `a rodada passa a valer ${nivel}` };
    }
  }
  const partes = texto.match(/^(.*?) \((.*)\)$/);
  return partes ? { principal: partes[1], detalhe: partes[2] } : { principal: texto, detalhe: '' };
}

function dica(acoes, visao) {
  if (!visao) return '';
  if (visao.vencedoraDaPartida) return 'Fim de partida.';
  const tipos = new Set(acoes.map(({ acao }) => acao.tipo));
  const pedido = visao.aposta.pedidoPendente;
  const outros = duplas() ? 'a dupla adversária' : nome(outraEquipe());
  if (acoes.length === 0) {
    if (estado.modo !== 'sala') return '';
    if (visao.vezDe && visao.vezDe !== estado.eu) return `Vez de ${nome(visao.vezDe)}…`;
    return duplas() ? 'Esperando os outros…' : `Esperando ${nome(outraEquipe())}…`;
  }
  if (pedido && tipos.has('Aceitar')) {
    const ver = tipos.has('VerCartasDoParceiro') ? ' Antes, vocês podem ver as cartas um do outro.' : '';
    return `${comMaiuscula(nome(pedido.pedinte))} pediu aumento: aceite, corra ou peça mais.${ver}`;
  }
  if (pedido && tipos.has('VerCartasDoParceiro')) {
    return `${comMaiuscula(nome(pedido.respondedor))} vai responder ao pedido. Se quiser, peça para vocês verem as cartas um do outro.`;
  }
  if (tipos.has('RecusarDescarte')) return 'Descarte: você tem a carta da vez. Descarte-a para comprar outra, ou não descarte.';
  if (tipos.has('Descartar')) {
    return tipos.has('JogarCarta') || tipos.has('Aceitar')
      ? 'Você tem a carta da vez: se quiser, descarte-a antes de jogar.'
      : 'Você tem a carta da vez: pode descartá-la até alguém jogar.';
  }
  if (visao.tipoDaRodada.tipo === 'DeOnze' && !visao.tipoDaRodada.decidiuJogar) {
    const vendo = Object.keys(visao.maosDosParceiros ?? {}).length > 0 ? ' As cartas do seu parceiro estão abertas em cima.' : '';
    return `${duplas() ? 'Sua dupla tem' : 'Você tem'} 11 pontos: jogue a Rodada de Onze (vale 3) ou corra (${outros} ganha 1).${vendo}`;
  }
  if (tipos.has('JogarCarta')) {
    return visao.mao.length === 0 ? 'Rodada Escurinho: clique numa carta virada para jogá-la às cegas.' : 'Sua vez: clique numa carta para jogá-la.';
  }
  return '';
}

// ---------- Placar ----------

function atualizarPlacar(placar) {
  const pontos = { voce: placar[minhaEquipe()], adversario: placar[outraEquipe()] };
  for (const time of ['voce', 'adversario']) {
    const el = $(`pontos-${time}`);
    const antes = estado.placar?.[time];
    el.textContent = pontos[time];
    if (antes !== undefined && antes !== pontos[time]) {
      el.classList.remove('mudou');
      void el.offsetWidth; // Reinicia a animação.
      el.classList.add('mudou');
    }
  }
  estado.placar = pontos;
}

// ---------- Histórico (console) ----------

function quem(evento) {
  return evento.jogador ?? evento.pedinte ?? evento.vencedora ?? evento.equipeComOnze ?? evento.equipe ?? evento.resultado?.jogador;
}

function abrirRodadaNoConsole(titulo, resumo, vira) {
  const rodada = elemento('li', 'rodada');
  const topo = elemento('div', 'rodada-topo');
  topo.append(elemento('span', 'numero', titulo));
  if (vira) topo.append(Baralho.carta(vira, 'mini'));
  if (resumo) topo.append(elemento('span', 'resumo', resumo));
  const linhas = elemento('ol');
  rodada.append(topo, linhas);
  $('historico').append(rodada);
  estado.rodadaNoConsole = linhas;
}

const ESTILO_DA_LINHA = {
  VazaFinalizada: 'divisor',
  DescarteEncerrado: 'discreta',
  AumentoPedido: 'aumento',
  AumentoAceito: 'aumento',
  JogadorCorreu: 'correu',
  RodadaFinalizada: 'ponto',
  RodadaAnulada: 'ponto',
  PlacarAtualizado: 'placar',
  PartidaFinalizada: 'fim',
  RodadaDeOnzeIniciada: 'especial',
  RodadaEscurinhoIniciada: 'especial',
  QuisVerAsCartas: 'discreta',
  DuplaViuAsCartas: 'aumento',
};

function registrar({ evento, texto }) {
  if (evento.tipo === 'RodadaIniciada') {
    const vira = evento.vira ? ` · vira ${Baralho.nome(evento.vira)}` : '';
    abrirRodadaNoConsole(`Rodada ${evento.numeroDaRodada}`, `${comMaiuscula(nome(evento.carteador))} deu as cartas${vira}`, evento.vira);
    return;
  }
  if (!estado.rodadaNoConsole) abrirRodadaNoConsole('Partida', 'retomada de onde você parou');
  const id = quem(evento);
  const estilo = ESTILO_DA_LINHA[evento.tipo] ?? '';
  // A faixa da esquerda tem a cor de quem age; o resultado da vaza é uma divisória, sem cor.
  const linha = elemento('li', `linha ${estilo === 'divisor' ? 'geral' : lado(id)} ${estilo}`.trim());
  linha.append(textoComAtor(texto, id));
  const cartas = evento.cartas ?? (evento.carta ? [evento.carta] : []);
  const desenhos = evento.tipo === 'CartaEncobertaJogada'
    ? [Baralho.verso('mini')]
    : cartas.map((carta) => Baralho.carta(carta, 'mini'));
  if (desenhos.length > 0) {
    const miniaturas = elemento('span', 'cartas-da-linha');
    miniaturas.setAttribute('aria-hidden', 'true'); // O texto já diz quais são.
    miniaturas.append(...desenhos);
    linha.append(miniaturas);
  }
  estado.rodadaNoConsole.append(linha);
  const console = $('historico');
  console.scrollTop = console.scrollHeight;
}

/** O texto da linha, com o nome de quem age em destaque quando a frase começa por ele ("Beto jogou…"). */
function textoComAtor(texto, id) {
  const el = elemento('span', 'texto');
  const ator = id === undefined ? '' : `${comMaiuscula(nome(id))} `;
  if (ator && texto.startsWith(ator)) {
    el.append(elemento('strong', 'ator', ator.trim()), texto.slice(ator.length - 1));
  } else {
    el.textContent = texto;
  }
  return el;
}

function nota(texto) {
  if (!estado.rodadaNoConsole) abrirRodadaNoConsole('Partida', '');
  const linha = elemento('li', 'linha nota');
  linha.append(elemento('span', 'texto', texto));
  estado.rodadaNoConsole.append(linha);
  const console = $('historico');
  console.scrollTop = console.scrollHeight;
}

// ---------- Avisos sobre a mesa ----------

function terminaRodada(passo) {
  return passo.eventos.some(({ evento }) => evento.tipo === 'RodadaFinalizada' || evento.tipo === 'RodadaAnulada');
}

function mostrarResumo(passo) {
  const linhas = [];
  for (const { evento, texto } of passo.eventos) {
    if (evento.tipo === 'RodadaIniciada' || evento.tipo === 'PartidaFinalizada') break;
    if (['VazaFinalizada', 'JogadorCorreu', 'RodadaFinalizada', 'RodadaAnulada'].includes(evento.tipo)) linhas.push(elemento('p', '', texto));
  }
  avisar(linhas);
}

/** Mostra um aviso sobre a mesa; quem desenha a sala de espera ou o fim anota depois o que deixou na tela. */
function avisar(conteudo, classe = '') {
  const aviso = $('aviso');
  aviso.className = `aviso ${classe}`.trim();
  aviso.replaceChildren(...conteudo);
  aviso.hidden = false;
  estado.esperaNaTela = '';
  estado.fimNaTela = '';
}

function esconderAviso() {
  $('aviso').hidden = true;
  estado.esperaNaTela = '';
  estado.fimNaTela = '';
}

/**
 * O fim da partida. Contra o bot, jogar de novo é outra partida. Numa sala, cada um escolhe entre jogar de novo, com as
 * mesmas pessoas, e sair; o aviso mostra quem já pediu e quem saiu.
 */
function mostrarFim(visao) {
  const assinatura = JSON.stringify([visao.placar, estado.queremJogarDeNovo, estado.sairam]);
  if (estado.fimNaTela === assinatura) return;
  const venceu = visao.vencedoraDaPartida === minhaEquipe();
  const p = { nos: visao.placar[minhaEquipe()], eles: visao.placar[outraEquipe()] };
  const contraOBot = estado.modo === 'bot';
  let titulo;
  if (venceu) titulo = duplas() ? 'Vocês venceram!' : 'Você venceu!';
  else if (duplas()) titulo = 'A dupla adversária venceu';
  else titulo = contraOBot ? 'O bot venceu' : `${comMaiuscula(nome(outraEquipe()))} venceu`;
  const conteudo = [elemento('h3', '', titulo), elemento('p', 'placar-final', venceu ? `${p.nos} a ${p.eles}` : `${p.eles} a ${p.nos}`)];
  const botoes = elemento('div', 'botoes-do-fim');

  if (contraOBot) {
    botoes.append(botaoDoFim('Jogar de novo', 'aumento', () => novaPartidaContraOBot(null)));
  } else {
    // Os bots sempre topam jogar de novo; só as pessoas decidem.
    const outros = estado.lugares.filter((lugar) => lugar.id !== estado.eu && !lugar.bot);
    const sairam = outros.filter((lugar) => estado.sairam.includes(lugar.id));
    const querem = outros.filter((lugar) => estado.queremJogarDeNovo.includes(lugar.id));
    const euQuero = estado.queremJogarDeNovo.includes(estado.eu);
    const nomes = (lugares) => lista(lugares.map((lugar) => nome(lugar.id)));
    if (sairam.length > 0) {
      conteudo.push(elemento('p', 'situacao-do-fim', `${comMaiuscula(nomes(sairam))} ${sairam.length === 1 ? 'saiu' : 'saíram'} da sala.`));
    } else if (euQuero) {
      const faltam = outros.filter((lugar) => !querem.includes(lugar));
      conteudo.push(elemento('p', 'esperando', `Esperando ${nomes(faltam)} topar`));
    } else if (querem.length > 0) {
      const verbo = querem.length === 1 ? 'quer' : 'querem';
      conteudo.push(elemento('p', 'situacao-do-fim', `${comMaiuscula(nomes(querem))} ${verbo} jogar de novo.`));
    }
    if (sairam.length === 0 && !euQuero) {
      botoes.append(botaoDoFim('Jogar de novo', 'aumento', () => mudarSala('jogar-de-novo', {})));
    }
    botoes.append(botaoDoFim('Sair para o menu', 'discreto', sairDaSala));
  }
  conteudo.push(botoes);
  avisar(conteudo, 'fim');
  estado.fimNaTela = assinatura;
}

function botaoDoFim(texto, classe, acao) {
  const botao = elemento('button', `botao ${classe}`, texto);
  botao.type = 'button';
  botao.addEventListener('click', acao);
  return botao;
}

function link(href, texto) {
  const a = elemento('a', '', texto);
  a.href = href;
  return a;
}

// ---------- Menu ----------

function mostrarInicio(erro) {
  $('jogo').hidden = true;
  $('inicio').hidden = false;
  $('continuar').hidden = estado.modo === null;
  const aviso = $('erro-inicio');
  aviso.textContent = erro ?? '';
  aviso.hidden = !erro;
}

function mostrarJogo() {
  $('inicio').hidden = true;
  $('jogo').hidden = false;
}

$('naipes').append(...['OUROS', 'COPAS', 'ESPADAS', 'PAUS'].map(Baralho.simbolo));

$('nome').value = ler(CHAVE_NOME) ?? '';
$('nome').addEventListener('input', () => guardar(CHAVE_NOME, nomeDigitado()));

$('menu').addEventListener('click', () => mostrarInicio());
$('continuar').addEventListener('click', mostrarJogo);

$('bot-1x1').addEventListener('click', () => {
  if (podeTrocarDePartida()) novaPartidaContraOBot(null);
});
$('bot-2x2').addEventListener('click', () => {
  if (podeTrocarDePartida()) contraBotsEmDuplas();
});
$('sala-1x1').addEventListener('click', () => {
  if (podeTrocarDePartida()) criarSala('1x1');
});
$('sala-2x2').addEventListener('click', () => {
  if (podeTrocarDePartida()) criarSala('2x2');
});

$('entrar-sala').addEventListener('submit', (e) => {
  e.preventDefault();
  const codigo = codigoDaSala($('codigo-sala').value);
  if (codigo && podeTrocarDePartida(codigo)) abrirSala(codigo);
});

(async function comecar() {
  const parametros = new URLSearchParams(location.search);
  const daUrl = { sala: parametros.get('sala'), seed: parametros.get('seed') };
  if (daUrl.sala !== null || daUrl.seed !== null) history.replaceState(null, '', location.pathname);
  if (daUrl.sala) {
    await abrirSala(daUrl.sala);
    return;
  }
  if (daUrl.seed !== null && /^\d{1,15}$/.test(daUrl.seed)) {
    await novaPartidaContraOBot(Number(daUrl.seed));
    return;
  }
  const modo = ler(CHAVE_MODO);
  if (modo === 'sala' && salaSalva()) {
    entrarNaSala(salaSalva());
    return;
  }
  if (modo === 'bot' && botSalvo()) {
    await retomarContraOBot(botSalvo());
    return;
  }
  mostrarInicio();
})();
