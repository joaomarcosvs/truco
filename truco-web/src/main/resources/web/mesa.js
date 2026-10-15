'use strict';

// A página só conhece a sua visão e os eventos que você pode ver, no formato de docs/protocolo.md, como o cliente Unity.
// O servidor não guarda nada: a página guarda a seed e as suas jogadas, e manda tudo a cada pedido.

const API = 'api/partida';
const CHAVE = 'truco-partida';
const PAUSA = 550;
const PAUSA_FIM_DE_RODADA = 1900;
const NOMES = { voce: 'você', bot: 'bot' };
// Só para os rótulos dos botões de aumento; as regras ficam no servidor.
const NIVEIS = { 3: 'Truco', 6: 'Seis', 9: 'Nove', 12: 'Doze' };

const estado = { seed: null, acoes: [], ocupado: false, ultima: null, rodadaNoConsole: null, placar: null };

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
const plural = (n, palavra) => `${n} ${palavra}${n === 1 ? '' : 's'}`;

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
    () => avisar([elemento('p', '', 'Acordando o servidor… No plano gratuito ele dorme quando ninguém joga, e o primeiro acesso pode levar até um minuto.')]),
    2000,
  );
  try {
    const resposta = await fetch(API, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) });
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
  $('dica').textContent = '';
  try {
    const resposta = await pedir(corpo);
    esconderAviso();
    estado.seed = resposta.seed;
    salvar();
    atualizarSeed();
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
    avisar([elemento('p', '', erro.message)], 'erro');
    if (estado.ultima) desenharAcoes(estado.ultima.acoesLegais, estado.ultima.passos.at(-1).visao);
    return false;
  } finally {
    estado.ocupado = false;
  }
}

async function novaPartida(seed) {
  estado.acoes = [];
  estado.ultima = null;
  estado.placar = null;
  estado.rodadaNoConsole = null;
  $('historico').replaceChildren();
  await executar(seed === null ? { acoes: [] } : { seed, acoes: [] }, true);
}

async function jogar(acao) {
  if (estado.ocupado) return;
  estado.acoes.push(acao);
  const deuCerto = await executar({ seed: estado.seed, acoes: estado.acoes }, true);
  if (!deuCerto) estado.acoes.pop();
}

// ---------- Mesa ----------

function desenharMesa(visao) {
  atualizarPlacar(visao.placar);

  const cartasDoBot = visao.cartasNaMao.bot ?? 0;
  $('cartas-bot').textContent = cartasDoBot === 0 ? 'sem cartas na mão' : `${plural(cartasDoBot, 'carta')} na mão`;
  $('mao-bot').replaceChildren(...Array.from({ length: cartasDoBot }, () => Baralho.verso('pequena')));

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
    const quantas = visao.cartasNaMao.voce ?? 0;
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

function desenharVaza(vaza, numero) {
  const el = elemento('div', `vaza${vaza.atual ? ' atual' : ''}`);
  el.append(elemento('span', 'rotulo', `${numero}ª vaza`));
  const par = elemento('div', 'par');
  for (const jogador of ['bot', 'voce']) {
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
    resultado.textContent = vaza.resultado.jogador === 'voce' ? 'você' : 'bot';
    resultado.classList.add(vaza.resultado.jogador === 'voce' ? 'voce' : 'bot');
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
  if (!visao || acoes.length === 0) return visao?.vencedoraDaPartida ? 'Fim de partida.' : '';
  const tipos = new Set(acoes.map(({ acao }) => acao.tipo));
  if (visao.aposta.pedidoPendente) return 'O bot pediu aumento: aceite, corra ou peça mais.';
  if (visao.cartaDaVezNoDescarte) return 'Descarte: você tem a carta da vez. Descarte-a para comprar outra, ou não descarte.';
  if (visao.tipoDaRodada.tipo === 'DeOnze' && !visao.tipoDaRodada.decidiuJogar) return 'Você tem 11 pontos: jogue a Rodada de Onze (vale 3) ou corra (o bot ganha 1).';
  if (tipos.has('JogarCarta')) {
    return visao.mao.length === 0 ? 'Rodada Escurinho: clique numa carta virada para jogá-la às cegas.' : 'Sua vez: clique numa carta para jogá-la.';
  }
  return '';
}

// ---------- Placar ----------

function atualizarPlacar(placar) {
  for (const time of ['voce', 'bot']) {
    const el = $(`pontos-${time}`);
    const antes = estado.placar?.[time];
    el.textContent = placar[time];
    if (antes !== undefined && antes !== placar[time]) {
      el.classList.remove('mudou');
      void el.offsetWidth; // Reinicia a animação.
      el.classList.add('mudou');
    }
  }
  estado.placar = { ...placar };
}

// ---------- Histórico (console) ----------

function quem(evento) {
  const id = evento.jogador ?? evento.pedinte ?? evento.vencedora ?? evento.equipeComOnze ?? evento.resultado?.jogador;
  return id === 'voce' || id === 'bot' ? id : 'geral';
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
  const ator = quem(evento);
  const classes = {
    RodadaFinalizada: 'ponto', RodadaAnulada: 'ponto', PlacarAtualizado: 'placar', PartidaFinalizada: 'fim',
  };
  const linha = elemento('li', `linha ${ator} ${classes[evento.tipo] ?? ''}`.trim());
  linha.append(elemento('span', 'quem', ator === 'geral' ? '' : nome(ator)), elemento('span', 'texto', texto));
  estado.rodadaNoConsole.append(linha);
  const console = $('historico');
  console.scrollTop = console.scrollHeight;
}

function nota(texto) {
  if (!estado.rodadaNoConsole) abrirRodadaNoConsole('Partida', '');
  const linha = elemento('li', 'linha nota');
  linha.append(elemento('span', 'quem', ''), elemento('span', 'texto', texto));
  estado.rodadaNoConsole.append(linha);
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
  const voceVenceu = visao.vencedoraDaPartida === 'voce';
  const p = visao.placar;
  const botao = elemento('button', 'botao aumento', 'Jogar de novo');
  botao.type = 'button';
  botao.addEventListener('click', () => novaPartida(null));
  avisar([
    elemento('h3', '', voceVenceu ? 'Você venceu!' : 'O bot venceu'),
    elemento('p', '', voceVenceu ? `${p.voce} a ${p.bot}` : `${p.bot} a ${p.voce}`),
    botao,
  ], 'fim');
}

function atualizarSeed() {
  $('seed').textContent = estado.seed;
  $('repetir').href = `?seed=${estado.seed}`;
}

// ---------- Início ----------

$('naipes').append(...['OUROS', 'COPAS', 'ESPADAS', 'PAUS'].map(Baralho.simbolo));

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
