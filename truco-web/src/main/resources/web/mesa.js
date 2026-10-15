'use strict';

// A página só conhece a sua visão e os eventos que você pode ver, no formato de docs/protocolo.md, como o cliente Unity.
// Contra o bot, o servidor não guarda nada: a página guarda a seed e as suas jogadas, e manda tudo a cada pedido.
// Contra um amigo, a partida fica numa sala, na memória do servidor: a página guarda o código e o seu token, pergunta
// o que mudou a cada segundo e manda as suas jogadas.

const CHAVE_MODO = 'truco-modo';
const CHAVE_BOT = 'truco-partida';
const CHAVE_SALA = 'truco-sala';
const PAUSA = 550;
const PAUSA_FIM_DE_RODADA = 1900;
const INTERVALO_DA_SALA = 1000;
// Só para os rótulos dos botões de aumento; as regras ficam no servidor.
const NIVEIS = { 3: 'Truco', 6: 'Seis', 9: 'Nove', 12: 'Doze' };

const estado = {
  modo: null, // 'bot' ou 'sala'
  eu: 'voce',
  outro: 'bot',
  nomes: { voce: 'você', bot: 'bot' },
  geracao: 0, // muda a cada partida nova, para ignorar respostas da anterior
  // Contra o bot
  seed: null,
  acoes: [],
  // Na sala
  sala: null, // { codigo, token, voce }
  cursor: 0,
  situacao: null,
  esperaNaTela: false,
  erroNaTela: false,
  // Mesa
  ocupado: false,
  ultima: null, // { acoes, visao }: o que está desenhado agora
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
  const demorando = setTimeout(
    () => avisar([elemento('p', '', 'Acordando o servidor… No plano gratuito ele dorme quando ninguém joga, e o primeiro acesso pode levar até um minuto.')]),
    2000,
  );
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

function jogar(acao) {
  if (estado.modo === 'bot') jogarContraOBot(acao);
  else if (estado.modo === 'sala') jogarNaSala(acao);
}

// ---------- Começar uma partida ----------

function prepararPartida(modo, eu, outro, nomes) {
  estado.geracao += 1;
  estado.modo = modo;
  estado.eu = eu;
  estado.outro = outro;
  estado.nomes = nomes;
  estado.ultima = null;
  estado.placar = null;
  estado.rodadaNoConsole = null;
  estado.esperaNaTela = false;
  estado.erroNaTela = false;
  guardar(CHAVE_MODO, modo);

  const nomeDoOutro = comMaiuscula(nomes[outro]);
  $('nome-no-placar').textContent = nomeDoOutro;
  $('nome-adversario').textContent = nomeDoOutro;
  $('avatar-adversario').textContent = nomeDoOutro.charAt(0);
  $('pontos-voce').textContent = '0';
  $('pontos-adversario').textContent = '0';
  $('historico').replaceChildren();
  ['mao-adversario', 'mao-voce', 'vira', 'vazas', 'info', 'acoes'].forEach((id) => $(id).replaceChildren());
  $('cartas-adversario').textContent = '';
  $('dica').textContent = '';
  esconderAviso();
  mostrarJogo();
}

function emAndamento() {
  const acabou = Boolean(estado.ultima?.visao?.vencedoraDaPartida);
  if (estado.modo === 'bot') return estado.acoes.length > 0 && !acabou;
  if (estado.modo === 'sala') return estado.situacao === 'jogando' && !acabou;
  return false;
}

function podeTrocarDePartida() {
  return !emAndamento() || confirm('Abandonar a partida em andamento?');
}

// ---------- Contra o bot ----------

async function novaPartidaContraOBot(seed) {
  prepararPartida('bot', 'voce', 'bot', { voce: 'você', bot: 'bot' });
  estado.acoes = [];
  await executarContraOBot(seed === null ? { acoes: [] } : { seed, acoes: [] }, true);
}

async function retomarContraOBot(salvo) {
  prepararPartida('bot', 'voce', 'bot', { voce: 'você', bot: 'bot' });
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

// ---------- Contra um amigo, numa sala ----------

async function criarSala() {
  try {
    const sala = await pedir('api/salas', { metodo: 'POST' });
    entrarNaSala(sala);
  } catch (erro) {
    mostrarInicio(erro.message);
  }
}

/** Abre a sala do código: com o token guardado, volta para ela; sem ele, entra como convidado. */
async function abrirSala(digitado) {
  const codigo = digitado.replace(/[\s-]/g, '').toUpperCase();
  const salva = salaSalva();
  if (salva && salva.codigo === codigo) {
    entrarNaSala(salva);
    return;
  }
  try {
    const sala = await pedir(`api/salas/${encodeURIComponent(codigo)}/entrar`, { metodo: 'POST' });
    entrarNaSala(sala);
  } catch (erro) {
    mostrarInicio(erro.message);
  }
}

function entrarNaSala(sala) {
  const outro = sala.voce === 'anfitriao' ? 'convidado' : 'anfitriao';
  prepararPartida('sala', sala.voce, outro, { [sala.voce]: 'você', [outro]: 'adversário' });
  estado.sala = { codigo: sala.codigo, token: sala.token, voce: sala.voce };
  estado.cursor = 0;
  estado.situacao = null;
  guardar(CHAVE_SALA, estado.sala);
  $('console-info').replaceChildren('Sala ', elemento('strong', '', sala.codigo));
  acompanharSala(estado.geracao);
}

/** Pergunta ao servidor o que mudou, a cada segundo, até a partida acabar ou outra começar. */
async function acompanharSala(geracao) {
  while (estado.modo === 'sala' && estado.geracao === geracao) {
    if (!estado.ocupado) await atualizarSala(geracao);
    if (estado.situacao === 'finalizada') return;
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
  if (resposta.situacao === 'esperando-adversario') {
    mostrarEspera();
    return;
  }
  if (estado.esperaNaTela) {
    esconderAviso();
    estado.esperaNaTela = false;
  }
  if (resposta.passos.length > 0 || !estado.ultima) {
    desenharAcoes([], null);
    // Ao voltar para a sala, o histórico inteiro chega de uma vez: sem animação.
    await mostrar(resposta.passos, resposta.acoesLegais, estado.cursor > 0);
  }
  estado.cursor = resposta.cursor;
}

async function jogarNaSala(acao) {
  const geracao = estado.geracao;
  const cursorVisto = estado.cursor;
  await ateFicarLivre();
  if (geracao !== estado.geracao) return;
  if (estado.cursor !== cursorVisto) {
    nota('O adversário agiu antes da sua jogada. Veja a mesa e jogue de novo.');
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
        nota('O adversário agiu antes da sua jogada. Veja a mesa e jogue de novo.');
      } else {
        tratarErroDaSala(erro);
      }
      if (estado.ultima) desenharAcoes(estado.ultima.acoes, estado.ultima.visao);
    }
  });
  if (geracao === estado.geracao && estado.cursor === cursorVisto) await atualizarSala(geracao);
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

function mostrarEspera() {
  if (estado.esperaNaTela) return;
  estado.esperaNaTela = true;
  const endereco = new URL(location.pathname, location.origin);
  endereco.searchParams.set('sala', estado.sala.codigo);
  const copiar = elemento('button', 'botao aumento', 'Copiar link');
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
  avisar([
    elemento('p', 'rotulo-da-sala', 'Sala'),
    elemento('h3', 'codigo-da-sala', estado.sala.codigo),
    elemento('p', '', 'Mande o link para o seu amigo. A partida começa quando ele entrar.'),
    linkDaSala,
    elemento('p', 'esperando', 'Esperando seu amigo'),
  ], 'espera');
}

// ---------- Mesa ----------

function desenharMesa(visao) {
  atualizarPlacar(visao.placar);

  const cartasDoOutro = visao.cartasNaMao[estado.outro] ?? 0;
  $('cartas-adversario').textContent = cartasDoOutro === 0 ? 'sem cartas na mão' : `${plural(cartasDoOutro, 'carta')} na mão`;
  $('mao-adversario').replaceChildren(...Array.from({ length: cartasDoOutro }, () => Baralho.verso('pequena')));

  desenharInfo(visao);

  $('vira').replaceChildren(visao.vira ? Baralho.carta(visao.vira, 'media') : elemento('div', 'sem-vira', 'sem vira'));

  const vazas = $('vazas');
  vazas.replaceChildren();
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

/** A classe de cor de um jogador: você em dourado, o outro em azul. */
function lado(id) {
  if (id === estado.eu) return 'voce';
  if (id === estado.outro) return 'adversario';
  return 'geral';
}

function desenharVaza(vaza, numero) {
  const el = elemento('div', `vaza${vaza.atual ? ' atual' : ''}`);
  el.append(elemento('span', 'rotulo', `${numero}ª vaza`));
  const par = elemento('div', 'par');
  for (const jogador of [estado.outro, estado.eu]) {
    const jogada = vaza.jogadas.find((j) => j.jogador === jogador);
    if (!jogada) {
      par.append(elemento('div', 'lugar'));
    } else if (!jogada.carta) {
      par.append(Baralho.verso('pequena'));
    } else {
      const carta = Baralho.carta(jogada.carta, 'pequena');
      if (jogada.encoberta) carta.classList.add('apagada');
      par.append(carta);
    }
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
  const ordem = ['Aceitar', 'PedirAumento', 'RecusarDescarte', 'Correr'];
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
  const classes = { Aceitar: 'aceitar', PedirAumento: 'aumento', Correr: 'correr' };
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
  const outro = nome(estado.outro);
  if (acoes.length === 0) return estado.modo === 'sala' ? 'Esperando o adversário…' : '';
  const tipos = new Set(acoes.map(({ acao }) => acao.tipo));
  if (visao.aposta.pedidoPendente) return `O ${outro} pediu aumento: aceite, corra ou peça mais.`;
  if (tipos.has('RecusarDescarte')) return 'Descarte: você tem a carta da vez. Descarte-a para comprar outra, ou não descarte.';
  if (tipos.has('Descartar')) {
    return tipos.has('JogarCarta') || tipos.has('Aceitar')
      ? 'Você tem a carta da vez: se quiser, descarte-a antes de jogar.'
      : `Você tem a carta da vez: pode descartá-la até o ${outro} jogar.`;
  }
  if (visao.tipoDaRodada.tipo === 'DeOnze' && !visao.tipoDaRodada.decidiuJogar) return `Você tem 11 pontos: jogue a Rodada de Onze (vale 3) ou corra (o ${outro} ganha 1).`;
  if (tipos.has('JogarCarta')) {
    return visao.mao.length === 0 ? 'Rodada Escurinho: clique numa carta virada para jogá-la às cegas.' : 'Sua vez: clique numa carta para jogá-la.';
  }
  return '';
}

// ---------- Placar ----------

function atualizarPlacar(placar) {
  const pontos = { voce: placar[estado.eu], adversario: placar[estado.outro] };
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
  return evento.jogador ?? evento.pedinte ?? evento.vencedora ?? evento.equipeComOnze ?? evento.resultado?.jogador;
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

function registrar({ evento, texto }) {
  if (evento.tipo === 'RodadaIniciada') {
    const vira = evento.vira ? ` · vira ${Baralho.nome(evento.vira)}` : '';
    abrirRodadaNoConsole(`Rodada ${evento.numeroDaRodada}`, `${comMaiuscula(nome(evento.carteador))} deu as cartas${vira}`, evento.vira);
    return;
  }
  if (!estado.rodadaNoConsole) abrirRodadaNoConsole('Partida', 'retomada de onde você parou');
  const id = quem(evento);
  const classes = {
    RodadaFinalizada: 'ponto', RodadaAnulada: 'ponto', PlacarAtualizado: 'placar', PartidaFinalizada: 'fim',
  };
  const linha = elemento('li', `linha ${lado(id)} ${classes[evento.tipo] ?? ''}`.trim());
  linha.append(elemento('span', 'quem', lado(id) === 'geral' ? '' : nome(id)), elemento('span', 'texto', texto));
  estado.rodadaNoConsole.append(linha);
  const console = $('historico');
  console.scrollTop = console.scrollHeight;
}

function nota(texto) {
  if (!estado.rodadaNoConsole) abrirRodadaNoConsole('Partida', '');
  const linha = elemento('li', 'linha nota');
  linha.append(elemento('span', 'quem', ''), elemento('span', 'texto', texto));
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

function avisar(conteudo, classe = '') {
  const aviso = $('aviso');
  aviso.className = `aviso ${classe}`.trim();
  aviso.replaceChildren(...conteudo);
  aviso.hidden = false;
}

function esconderAviso() {
  $('aviso').hidden = true;
}

function mostrarFim(visao) {
  const voceVenceu = visao.vencedoraDaPartida === estado.eu;
  const p = { voce: visao.placar[estado.eu], outro: visao.placar[estado.outro] };
  const contraOBot = estado.modo === 'bot';
  const botao = elemento('button', 'botao aumento', contraOBot ? 'Jogar de novo' : 'Voltar ao menu');
  botao.type = 'button';
  botao.addEventListener('click', () => (contraOBot ? novaPartidaContraOBot(null) : mostrarInicio()));
  avisar([
    elemento('h3', '', voceVenceu ? 'Você venceu!' : `O ${nome(estado.outro)} venceu`),
    elemento('p', '', voceVenceu ? `${p.voce} a ${p.outro}` : `${p.outro} a ${p.voce}`),
    botao,
  ], 'fim');
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

$('menu').addEventListener('click', () => mostrarInicio());
$('continuar').addEventListener('click', mostrarJogo);

$('jogar-bot').addEventListener('click', () => {
  if (podeTrocarDePartida()) novaPartidaContraOBot(null);
});

$('criar-sala').addEventListener('click', () => {
  if (podeTrocarDePartida()) criarSala();
});

$('entrar-sala').addEventListener('submit', (e) => {
  e.preventDefault();
  const codigo = $('codigo-sala').value.trim();
  if (codigo && podeTrocarDePartida()) abrirSala(codigo);
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
