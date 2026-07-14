// Caminho: src/main/java/dsid/network/P2PNode.java
package dsid.network;

import dsid.core.Blockchain;
import dsid.storage.LedgerRepository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class P2PNode {

    private final Blockchain           blockchain;
    private final LedgerRepository     repository;
    private final int                  portaLocal;
    private final List<String>         vizinhosConhecidos = new CopyOnWriteArrayList<>();
    private       SocketServer         servidor;
    private       HeartbeatService     heartbeat;

    public P2PNode(Blockchain blockchain, LedgerRepository repository, int portaLocal) {
        this.blockchain = blockchain;
        this.repository = repository;
        this.portaLocal = portaLocal;
    }

    // ---------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------
    public void iniciar() {
        MessageParser parser = new MessageParser(this, repository);
        servidor  = new SocketServer(portaLocal, parser);
        heartbeat = new HeartbeatService(this);
        servidor.start();
        heartbeat.iniciar();
        System.out.println("[NODE] Nó P2P iniciado na porta " + portaLocal);
    }

    public void syncInicial() {
        if (vizinhosConhecidos.isEmpty()) {
            System.out.println("[NODE] Sem vizinhos conhecidos — operando como nó semente.");
            return;
        }
        System.out.println("[NODE] Iniciando sync com " + vizinhosConhecidos.size() + " vizinhos...");
        // Inclui portaLocal para que o vizinho saiba onde enviar a CHAIN
        String payload = BlockchainSerializer.serializarGetChain(
                blockchain.getUltimoBloco().getHeight(), portaLocal);
        for (String vizinho : vizinhosConhecidos) {
            String[] p = vizinho.split(":");
            SocketClient.enviarMensagem(p[0], Integer.parseInt(p[1]), "GET_CHAIN|" + payload);
        }
    }

    public void parar() {
        if (heartbeat != null) heartbeat.parar();
        if (servidor  != null) servidor.pararServidor();
        System.out.println("[NODE] Nó P2P encerrado.");
    }

    // ---------------------------------------------------------------
    // Gestão de vizinhos
    // ---------------------------------------------------------------
    public void adicionarVizinho(String ip, int porta) {
        String endereco = ip + ":" + porta;
        if (!vizinhosConhecidos.contains(endereco)) {
            vizinhosConhecidos.add(endereco);
            System.out.println("[NODE] Vizinho adicionado: " + endereco);
        }
    }

    public void removerVizinho(String endpoint) {
        vizinhosConhecidos.remove(endpoint);
        System.out.println("[NODE] Vizinho removido por timeout: " + endpoint);
    }

    public void confirmarVizinhoVivo(String endpoint) {
        if (heartbeat != null) heartbeat.registrarPong(endpoint);
        if (!vizinhosConhecidos.contains(endpoint)) {
            vizinhosConhecidos.add(endpoint);
            if (heartbeat != null) heartbeat.reativarVizinho(endpoint);
            System.out.println("[NODE] Vizinho reativado: " + endpoint);
        }
    }

    public List<String> getVizinhosAtivos() {
        return Collections.unmodifiableList(vizinhosConhecidos);
    }

    // ---------------------------------------------------------------
    // Gossip
    // ---------------------------------------------------------------
    public void broadcast(String mensagem) {
        if (vizinhosConhecidos.isEmpty()) return;
        System.out.println("[NODE] Broadcast para " + vizinhosConhecidos.size() + " vizinhos.");
        for (String vizinho : vizinhosConhecidos) {
            String[] p = vizinho.split(":");
            SocketClient.enviarMensagem(p[0], Integer.parseInt(p[1]), mensagem);
        }
    }

    public void solicitarSyncDe(String endpoint) {
        String payload = BlockchainSerializer.serializarGetChain(
                blockchain.getUltimoBloco().getHeight(), portaLocal);
        if (endpoint == null || endpoint.isEmpty()) {
            for (String v : vizinhosConhecidos) {
                String[] p = v.split(":");
                SocketClient.enviarMensagem(p[0], Integer.parseInt(p[1]), "GET_CHAIN|" + payload);
            }
        } else {
            String[] p = endpoint.split(":");
            if (p.length >= 2)
                SocketClient.enviarMensagem(p[0], Integer.parseInt(p[1]), "GET_CHAIN|" + payload);
            else
                // só IP sem porta — tenta todos
                for (String v : vizinhosConhecidos) {
                    String[] vp = v.split(":");
                    SocketClient.enviarMensagem(vp[0], Integer.parseInt(vp[1]), "GET_CHAIN|" + payload);
                }
        }
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------
    public Blockchain       getBlockchain() { return blockchain; }
    public LedgerRepository getRepository() { return repository; }
    public int              getPortaLocal()  { return portaLocal; }
}