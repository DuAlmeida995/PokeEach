package dsid.network;

import java.io.PrintWriter;
import java.net.Socket;

public class SocketClient {

    // um metodo fire and forget basicamente
    public static void enviarMensagem(String ipDestino, int portaDestino, String mensagem) {
        try {
            // 1. tenta abrir um tubo TCP direto com o IP e a porta do vizinho
            Socket socket = new Socket(ipDestino, portaDestino);
            
            // 2. cria o canal de saida de texto (o 'true' força o envio imediato, sem ficar preso no buffer)
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            
            // 3. grita a mensagem pelo tubo
            out.println(mensagem);
            
            // 4. fecha a conexao liberando recursos de rede 
            socket.close();
            
            System.out.println("[CLIENTE] Mensagem enviada com sucesso para " + ipDestino + ":" + portaDestino);
            
        } catch (Exception e) {
            // Se o vizinho estiver com o PC desligado ou o Firewall bloqueando, cai aqui.
            // O try/catch blinda o nosso backend para que nosso jogo n crashe!
            System.out.println("[CLIENTE] Falha ao conectar com " + ipDestino + ":" + portaDestino + ". O nó parece estar offline.");
        }
    }
}