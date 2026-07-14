// Caminho: src/main/java/dsid/network/SocketServer.java
package dsid.network;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Servidor TCP P2P do PokeEach.
 *
 * Melhorias em relação à versão original:
 *   - Thread pool (ExecutorService) com até 10 threads: cada conexão
 *     de vizinho é tratada em paralelo, sem bloquear o accept loop.
 *   - IP e porta do remetente são extraídos e repassados ao MessageParser,
 *     permitindo que PING responda PONG corretamente e que GET_CHAIN
 *     envie CHAIN de volta ao solicitante.
 *   - Timeout de leitura de 5 s por socket para evitar conexões travadas.
 *   - Shutdown limpo do pool ao parar o servidor.
 */
public class SocketServer extends Thread {

    private static final int MAX_THREADS     = 10;
    private static final int SOCKET_TIMEOUT  = 5_000; // ms

    private final int           porta;
    private final MessageParser parser;
    private volatile boolean    rodando = true;
    private ServerSocket        serverSocket;
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);

    public SocketServer(int porta, MessageParser parser) {
        this.porta  = porta;
        this.parser = parser;
        setDaemon(true); // não impede o JVM de fechar
        setName("SocketServer-" + porta);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(porta);
            System.out.println("[REDE] Servidor P2P escutando na porta: " + porta);

            while (rodando) {
                try {
                    Socket socketVizinho = serverSocket.accept();
                    // Cada conexão é tratada em thread separada do pool
                    pool.submit(() -> tratarConexao(socketVizinho));
                } catch (Exception e) {
                    if (rodando) {
                        System.out.println("[REDE] Erro ao aceitar conexão: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (rodando) System.out.println("[REDE] Erro fatal no servidor: " + e.getMessage());
        }
    }

    private void tratarConexao(Socket socket) {
        String ip   = socket.getInetAddress().getHostAddress();
        int    porta = socket.getPort();

        try {
            socket.setSoTimeout(SOCKET_TIMEOUT);
            java.io.BufferedReader in = new java.io.BufferedReader(
                    new java.io.InputStreamReader(socket.getInputStream()));

            String mensagem = in.readLine();
            if (mensagem != null && !mensagem.isBlank()) {
                System.out.println("[REDE] Mensagem de " + ip + ":" + porta + " → " + mensagem.substring(0, Math.min(80, mensagem.length())) + "...");
                parser.processarMensagem(mensagem, ip, porta);
            }
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("[REDE] Timeout lendo de " + ip + ":" + porta);
        } catch (Exception e) {
            System.out.println("[REDE] Erro ao ler de " + ip + ":" + porta + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    public void pararServidor() {
        rodando = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            pool.shutdown();
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (Exception e) {
            pool.shutdownNow();
        }
        System.out.println("[REDE] Servidor P2P encerrado.");
    }
}