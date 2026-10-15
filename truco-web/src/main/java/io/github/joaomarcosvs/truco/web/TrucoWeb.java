package io.github.joaomarcosvs.truco.web;

import java.io.IOException;

/**
 * Sobe a página de teste: você contra o {@code BotAleatorio} ou contra um amigo, no navegador. A porta vem da variável de ambiente
 * {@code PORT}, como pedem os serviços de hospedagem; sem ela, usa 8080.
 */
public final class TrucoWeb {

    private TrucoWeb() {}

    public static void main(String[] args) throws IOException {
        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        ServidorWeb servidor = ServidorWeb.iniciar(porta);
        System.out.println("Truco Paulista na web: http://localhost:" + servidor.porta());
    }
}
