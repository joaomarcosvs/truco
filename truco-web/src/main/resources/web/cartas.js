'use strict';

// Baralho espanhol: as cartas são recortes da folha "Baraja española completa" (baralho-espanhol.png), de Basquetteur,
// a partir do trabalho de Germarquezm, sob a licença CC BY-SA 3.0, via Wikimedia Commons:
// https://commons.wikimedia.org/wiki/File:Baraja_espa%C3%B1ola_completa.png
// A folha é usada sem alteração; o CSS só mostra o pedaço de cada carta.
//
// O core usa as cartas do Truco Paulista (A, 2–7, 10, Q, J em copas, espadas, ouros e paus). Aqui elas aparecem como o
// baralho espanhol de 40 cartas, na mesma ordem de força: paus são bastos, e 10, Q e J são sota (10), cavalo (11) e
// rei (12), porque no paulista 10 < Q < J, como sota < cavalo < rei.

const Baralho = (() => {
  // A folha tem 12 colunas (valores 1 a 12) e 5 fileiras: ouros, copas, espadas, bastos e, por último, uma carta em
  // branco e o verso.
  const COLUNAS = { AS: 0, DOIS: 1, TRES: 2, QUATRO: 3, CINCO: 4, SEIS: 5, SETE: 6, OITO: 7, NOVE: 8, DEZ: 9, DAMA: 10, VALETE: 11 };
  const FILEIRAS = { OUROS: 0, COPAS: 1, ESPADAS: 2, PAUS: 3 };
  const VERSO = { coluna: 1, fileira: 4 };

  const NOMES_DOS_VALORES = { AS: 'ás', DEZ: 'sota', DAMA: 'cavalo', VALETE: 'rei' };
  const NUMEROS = { DOIS: 2, TRES: 3, QUATRO: 4, CINCO: 5, SEIS: 6, SETE: 7, OITO: 8, NOVE: 9 };
  const NAIPES = { OUROS: 'ouros', COPAS: 'copas', ESPADAS: 'espadas', PAUS: 'bastos' };

  function nome(carta) {
    const valor = NOMES_DOS_VALORES[carta.valor] ?? String(NUMEROS[carta.valor] ?? carta.valor);
    return `${valor} de ${NAIPES[carta.naipe]}`;
  }

  function recorte(coluna, fileira, classes, rotulo) {
    const el = document.createElement('div');
    el.className = `carta ${classes}`;
    el.setAttribute('role', 'img');
    el.setAttribute('aria-label', rotulo);
    el.style.setProperty('--coluna', coluna);
    el.style.setProperty('--fileira', fileira);
    return el;
  }

  function carta(c, tamanho = 'grande') {
    return recorte(COLUNAS[c.valor], FILEIRAS[c.naipe], tamanho, nome(c));
  }

  /** O verso; com um rótulo (a posição na mão, na Rodada Escurinho), escrito por cima. */
  function verso(tamanho = 'grande', rotulo = '') {
    const el = recorte(VERSO.coluna, VERSO.fileira, `verso ${tamanho}`, 'carta virada');
    if (rotulo) el.append(Object.assign(document.createElement('span'), { className: 'posicao', textContent: rotulo }));
    return el;
  }

  // ---------- Os quatro naipes desenhados, só para enfeitar o título ----------

  const SVG = 'http://www.w3.org/2000/svg';
  const OURO = '#f4c430';
  const OURO_ESCURO = '#a8740f';

  function el(tag, atributos = {}) {
    const elemento = document.createElementNS(SVG, tag);
    for (const [nomeDoAtributo, valor] of Object.entries(atributos)) elemento.setAttribute(nomeDoAtributo, valor);
    return elemento;
  }

  const DESENHOS = {
    OUROS: () => [
      el('circle', { r: 12, fill: OURO, stroke: OURO_ESCURO, 'stroke-width': 1.6 }),
      el('circle', { r: 7, fill: 'none', stroke: OURO_ESCURO, 'stroke-width': 1 }),
    ],
    COPAS: () => [
      el('path', { d: 'M-11,-11 L11,-11 C11,-1 6,5 0,5 C-6,5 -11,-1 -11,-11Z', fill: '#c8402f', stroke: '#7a2318', 'stroke-width': 1.2 }),
      el('rect', { x: -2, y: 5, width: 4, height: 6.5, fill: OURO }),
      el('ellipse', { cx: 0, cy: 13, rx: 8.5, ry: 2.8, fill: OURO }),
    ],
    ESPADAS: () => [
      el('path', { d: 'M-2.6,-6 L2.6,-6 L2.6,11 L0,16.5 L-2.6,11Z', fill: '#8fbbea', stroke: '#23466e', 'stroke-width': 1 }),
      el('rect', { x: -9, y: -8.8, width: 18, height: 3, rx: 1.3, fill: OURO }),
      el('rect', { x: -1.7, y: -14.5, width: 3.4, height: 6, fill: '#8b4513' }),
    ],
    PAUS: () => [
      el('path', { d: 'M-2.2,15.5 C-2.6,8 -5.8,-4 -5.8,-10 C-5.8,-16 5.8,-16 5.8,-10 C5.8,-4 2.6,8 2.2,15.5Z', fill: '#6aa84f', stroke: '#2f5a1a', 'stroke-width': 1.1 }),
    ],
  };

  function simbolo(naipe) {
    const svg = el('svg', { viewBox: '-14 -18 28 36', class: 'naipe', 'aria-hidden': 'true' });
    svg.append(...DESENHOS[naipe]());
    return svg;
  }

  return { carta, verso, nome, simbolo };
})();
