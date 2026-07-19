package dsid.network;

import java.io.PrintWriter;
import java.net.Socket;

/**
 * cliente TCP fire-and-forget
 * abre conexao, envia mensagem e fecha imediatamente
 * timeout de conexao de 3s evita bloqueio por peers offline
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