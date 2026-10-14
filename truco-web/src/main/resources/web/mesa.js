'use strict';

// A página só conhece a sua visão e os eventos que você pode ver, no formato de docs/protocolo.md: como o cliente Unity.
// O servidor não guarda nada; a página guarda a seed e as suas jogadas, e manda tudo a cada pedido.

const API = 'api/partida';
const CHAVE = 'truco-partida';
const PAUSA = 500;
const PAUSA_FIM_DE_RODADA = 1600;

const VALORES = {
  AS: 'A', DOIS: '2', TRES: '3', QUATRO: '4', CINCO: '5', SEIS: '6', SETE: '7',
  OITO: '8', NOVE: '9', DEZ: '10', DAMA: 'Q', VALETE: 'J', REI: 'K',
};
const NAIPES = { COPAS: '♥', ESPADAS: '♠', OUROS: '♦', PAUS: '♣' };
const VERMELHOS = new Set(['COPAS', 'OUROS']);
const NOMES = { voce: 'você', bot: 'bot' };

const estado = { seed: null, acoes: [], ocupado: false, ultima: null };

const $ = (id) => document.getElementById(id);

function elemento(tag, classe, texto) {
  const el = document.createElement(tag);
  if (classe) el.className = classe;
  if (texto !== undefined) el.textContent = texto;
  return el;
}

const esperar = (ms) => new Promise((pronto) => setTimeout(pronto, ms));

const nome = (id) => NOMES[id] ?? id;

const comMaiuscula = (texto) => texto.charAt(0).toUpperCase() + texto.slice(1);

// ---------- Guardar a partida no navegador (para sobreviver a um recarregar) ----------

function salvar() {
  try {
    localStorage.setItem(CHAVE, JSON.stringify({ seed: estado.seed, acoes: estado.acoes }));
  } catch (e) {
    // Sem armazenamento (aba anônima, por exemplo): a partida só não sobrevive a um recarregar.
  }
}

function carregar() {
  try {
    const salvo = JSON.parse(localStorage.getItem(CHAVE));
    if (salvo && Number.isSafeInteger(salvo.seed) && Array.isArray(salvo.acoes)) return salvo;
  } catch (e) {
    // Ignora o que estiver salvo de forma inválida.
  }
  return null;
}

// ---------- Conversa com o servidor ----------

async function pedir(corpo) {
  const demorando = setTimeout(
    () => avisar('Acordando o servidor… No plano gratuito ele dorme quando ninguém joga, e o primeiro acesso pode levar até um minuto.'),
    2000,
  );
  try {
    const resposta = await fetch(API, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo),
    });
    const json = await resposta.json().catch(() => ({}));
    if (!resposta.ok) throw new Error(json.erro || `O servidor respondeu ${resposta.status}`);
    return json;
  } catch (erro) {
    if (erro instanceof TypeError) throw new Error('Não foi possível falar com o servidor. Tente de novo.');
    throw erro;
  } finally {
    clearTimeout(demorando);
  }
}

async function executar(corpo, animar) {
  estado.ocupado = true;
  desenharAcoes([]);
  try {
    const resposta = await pedir(corpo);
    esconderAviso();
    estado.seed = resposta.seed;
    salvar();
    atualizarRodape();
    for (const [i, passo] of resposta.passos.entries()) {
      if (animar && i > 0) await esperar(PAUSA);
      passo.eventos.forEach(registrar);
      if (animar && terminaRodada(passo)) {
        mostrarResumo(passo);
        await esperar(PAUSA_FIM_DE_RODADA);
        esconderAviso();
      }
      desenharMesa(passo.visao);
    }
    estado.ultima = resposta;
    desenharAcoes(resposta.acoesLegais, resposta.passos.at(-1).visao);
    return true;
  } catch (erro) {
    avisar(erro.message);
    if (estado.ultima) desenharAcoes(estado.ultima.acoesLegais, estado.ultima.passos.at(-1).visao);
    return false;
  } finally {
    estado.ocupado = false;
  }
}

async function novaPartida(seed) {
  estado.acoes = [];
  estado.ultima = null;
  $('historico').replaceChildren();
  await executar(seed === null ? { acoes: [] } : { seed, acoes: [] }, true);
}

async function jogar(acao) {
  if (estado.ocupado) return;
  estado.acoes.push(acao);
  const deuCerto = await executar({ seed: estado.seed, acoes: estado.acoes }, true);
  if (!deuCerto) estado.acoes.pop();
}

// ---------- Desenho ----------

function carta(c, pequena) {
  const el = elemento('div', `carta${pequena ? ' pequena' : ''}${VERMELHOS.has(c.naipe) ? ' vermelha' : ''}`);
  const valor = VALORES[c.valor] ?? c.valor;
  const naipe = NAIPES[c.naipe] ?? c.naipe;
  el.append(elemento('span', 'canto', `${valor}\n${naipe}`), elemento('span', 'meio', naipe), elemento('span', 'canto baixo', valor));
  el.setAttribute('aria-label', `${valor}${naipe}`);
  return el;
}

function verso(pequena, texto) {
  const el = elemento('div', `carta verso${pequena ? ' pequena' : ''}`);
  el.append(elemento('span', 'meio', texto ?? ''));
  el.setAttribute('aria-label', 'carta virada');
  return el;
}

function selo(el, texto) {
  el.append(elemento('span', 'selo', texto));
  return el;
}

function desenharMesa(visao) {
  const pontos = visao.placar;
  $('placar').textContent = `Você ${pontos.voce} × ${pontos.bot} Bot`;

  const info = $('info');
  info.replaceChildren(elemento('span', 'chip', `Rodada ${visao.numeroDaRodada} · vale ${visao.aposta.valor}`));
  info.append(elemento('span', 'chip', `${comMaiuscula(nome(visao.carteador))} deu as cartas`));
  const tipo = visao.tipoDaRodada;
  if (tipo.tipo === 'DeOnze') {
    info.append(elemento('span', 'chip especial',
      `Rodada de Onze: ${nome(tipo.equipeComOnze)} ${tipo.decidiuJogar ? 'decidiu jogar' : 'decide se joga ou corre'}`));
  } else if (tipo.tipo === 'Escurinho') {
    info.append(elemento('span', 'chip especial', 'Rodada Escurinho: ninguém vê as próprias cartas'));
  }
  const pedido = visao.aposta.pedidoPendente;
  if (pedido) {
    info.append(elemento('span', 'chip pedido', `${comMaiuscula(nome(pedido.pedinte))} pediu aumento para ${pedido.nivelProposto}`));
  }
  if (visao.cartaDaVezNoDescarte) {
    const c = visao.cartaDaVezNoDescarte;
    info.append(elemento('span', 'chip', `Descarte: carta da vez ${VALORES[c.valor]}${NAIPES[c.naipe]}`));
  }
  if (visao.descartadas.length > 0) {
    const nomes = visao.descartadas.map((c) => `${VALORES[c.valor]}${NAIPES[c.naipe]}`).join(' ');
    info.append(elemento('span', 'chip', `Descartadas: ${nomes}`));
  }

  $('vira').replaceChildren(visao.vira ? carta(visao.vira) : elemento('span', 'rotulo', 'sem vira'));

  $('mao-bot').replaceChildren(...Array.from({ length: visao.cartasNaMao.bot ?? 0 }, () => verso()));

  const suaMao = $('mao-voce');
  if (visao.mao.length > 0) {
    suaMao.replaceChildren(...visao.mao.map((c) => carta(c)));
  } else {
    // Na Rodada Escurinho você não vê as próprias cartas, só as posições.
    const quantas = visao.cartasNaMao.voce ?? 0;
    suaMao.replaceChildren(...Array.from({ length: quantas }, (_, i) => verso(false, `[${i + 1}]`)));
  }

  const vazas = $('vazas');
  vazas.replaceChildren();
  const todas = [...visao.vazas.map((v) => ({ jogadas: v.jogadas, resultado: v.resultado }))];
  if (!visao.vencedoraDaPartida) todas.push({ jogadas: visao.vazaAtual, resultado: null, atual: true });
  while (todas.length < 3) todas.push({ jogadas: [], resultado: null });
  todas.slice(0, 3).forEach((vaza, i) => vazas.append(desenharVaza(vaza, i + 1)));
}

function desenharVaza(vaza, numero) {
  const el = elemento('div', `vaza${vaza.atual ? ' atual' : ''}`);
  el.append(elemento('span', 'rotulo', `Vaza ${numero}`));
  for (const jogador of ['bot', 'voce']) {
    const jogada = vaza.jogadas.find((j) => j.jogador === jogador);
    if (!jogada) {
      el.append(elemento('div', 'carta pequena vazia'));
    } else if (!jogada.carta) {
      el.append(selo(verso(true), 'encoberta'));
    } else {
      const c = carta(jogada.carta, true);
      if (jogada.encoberta) {
        c.classList.add('encoberta');
        selo(c, 'encoberta');
      }
      el.append(c);
    }
  }
  let resultado = '';
  if (vaza.resultado?.tipo === 'Vencida') resultado = `${nome(vaza.resultado.jogador)} venceu`;
  if (vaza.resultado?.tipo === 'Empatada') resultado = 'empate';
  el.append(elemento('span', 'resultado', resultado));
  return el;
}

function desenharAcoes(acoes, visao) {
  const area = $('acoes');
  area.replaceChildren();
  for (const { acao, texto } of acoes) {
    const botao = elemento('button', `botao ${classeDoBotao(acao)}`, texto);
    botao.type = 'button';
    botao.addEventListener('click', () => jogar(acao));
    area.append(botao);
  }
  // Clicar numa carta da mão também a joga aberta.
  const cartas = $('mao-voce').children;
  for (const { acao, texto } of acoes) {
    if (acao.tipo !== 'JogarCarta') continue;
    const el = cartas[acao.indiceNaMao];
    if (!el) continue;
    el.classList.add('jogavel');
    el.tabIndex = 0;
    el.title = texto;
    el.addEventListener('click', () => jogar(acao));
    el.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        jogar(acao);
      }
    });
  }
  if (visao?.vencedoraDaPartida) mostrarFim(visao);
}

function classeDoBotao(acao) {
  switch (acao.tipo) {
    case 'Aceitar':
    case 'JogarCarta':
      return 'principal';
    case 'PedirAumento':
      return 'aumento';
    case 'Correr':
      return 'perigo';
    default:
      return '';
  }
}

// ---------- Histórico e avisos ----------

function registrar({ evento, texto }) {
  const lista = $('historico');
  const item = elemento('li', '', texto);
  if (evento.tipo === 'RodadaIniciada') item.className = 'rodada';
  if (evento.tipo === 'PartidaFinalizada') item.className = 'fim';
  lista.append(item);
  lista.parentElement.scrollTop = lista.parentElement.scrollHeight;
}

function nota(texto) {
  $('historico').append(elemento('li', 'nota', texto));
}

function terminaRodada(passo) {
  return passo.eventos.some(({ evento }) => evento.tipo === 'RodadaFinalizada' || evento.tipo === 'RodadaAnulada');
}

function mostrarResumo(passo) {
  const linhas = [];
  for (const { evento, texto } of passo.eventos) {
    if (evento.tipo === 'RodadaIniciada' || evento.tipo === 'PartidaFinalizada') break;
    if (['VazaFinalizada', 'JogadorCorreu', 'RodadaFinalizada', 'RodadaAnulada'].includes(evento.tipo)) linhas.push(texto);
  }
  avisar(linhas.join(' '));
}

function avisar(texto) {
  const aviso = $('aviso');
  aviso.className = 'aviso';
  aviso.replaceChildren(texto);
  aviso.hidden = false;
}

function esconderAviso() {
  $('aviso').hidden = true;
}

function mostrarFim(visao) {
  const aviso = $('aviso');
  const voceVenceu = visao.vencedoraDaPartida === 'voce';
  const p = visao.placar;
  aviso.className = 'aviso fim';
  const botao = elemento('button', 'botao', 'Jogar de novo');
  botao.type = 'button';
  botao.addEventListener('click', () => novaPartida(null));
  aviso.replaceChildren(voceVenceu ? `Você venceu, ${p.voce} a ${p.bot}!` : `O bot venceu, ${p.bot} a ${p.voce}.`, botao);
  aviso.hidden = false;
}

function atualizarRodape() {
  $('seed').textContent = estado.seed;
  $('repetir').href = `?seed=${estado.seed}`;
}

// ---------- Início ----------

$('nova').addEventListener('click', () => {
  if (estado.ocupado) return;
  const emAndamento = estado.ultima && !estado.ultima.passos.at(-1).visao.vencedoraDaPartida && estado.acoes.length > 0;
  if (emAndamento && !confirm('Abandonar esta partida e começar outra?')) return;
  novaPartida(null);
});

(async function comecar() {
  const daUrl = new URLSearchParams(location.search).get('seed');
  if (daUrl !== null && /^\d{1,15}$/.test(daUrl)) {
    history.replaceState(null, '', location.pathname);
    await novaPartida(Number(daUrl));
    return;
  }
  const salvo = carregar();
  if (salvo) {
    estado.acoes = salvo.acoes;
    nota('Partida retomada de onde você parou.');
    const deuCerto = await executar({ seed: salvo.seed, acoes: salvo.acoes }, false);
    if (deuCerto) return;
  }
  await novaPartida(null);
})();
