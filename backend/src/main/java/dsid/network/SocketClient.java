// Caminho: src/main/java/dsid/network/SocketClient.java
package dsid.network;

import java.io.PrintWriter;
import java.net.Socket;

/**
 * Cliente TCP fire-and-forget do PokeEach.
 * Abre conexão, envia mensagem e fecha imediatamente.
 * Timeout de conexão de 3 s evita bloqueio por peers offline.
 */
public class SocketClient {

    private static final int CONNECT_TIMEOUT_MS = 3_000;

    public static void enviarMensagem(String ipDestino, int portaDestino, String mensagem) {
        try {
            Socket socket = new Socket();
            socket.connect(
                new java.net.InetSocketAddress(ipDestino, portaDestino),
                CONNECT_TIMEOUT_MS);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(mensagem);
            socket.close();

            System.out.println("[CLIENTE] → " + ipDestino + ":" + portaDestino
                    + " [" + mensagem.substring(0, Math.min(40, mensagem.length())) + "...]");

        } catch (java.net.SocketTimeoutException e) {
            System.out.println("[CLIENTE] Timeout ao conectar em " + ipDestino + ":" + portaDestino);
        } catch (Exception e) {
            System.out.println("[CLIENTE] Falha ao conectar em " + ipDestino + ":" + portaDestino
                    + " — nó offline? (" + e.getMessage() + ")");
        }
    }
}