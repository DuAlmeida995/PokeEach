package dsid.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatService {

    private static final long INTERVALO_PING_S  = 5;
    private static final long TIMEOUT_INICIAL_MS = 15_000; // 3 ciclos sem PONG = suspeito
    private static final long TIMEOUT_MAX_MS     = 60_000;

    private final P2PNode node;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "HeartbeatService");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, Long> ultimoPong   = new ConcurrentHashMap<>();
    private final Map<String, Long> timeoutAtual = new ConcurrentHashMap<>();

    public HeartbeatService(P2PNode node) {
        this.node = node;
    }

    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::cicloHeartbeat,
                INTERVALO_PING_S, INTERVALO_PING_S, TimeUnit.SECONDS);
        System.out.println("[HEARTBEAT] Serviço iniciado (intervalo=" + INTERVALO_PING_S + "s).");
    }

    public void parar() {
        scheduler.shutdownNow();
    }

    public void registrarPong(String endpoint) {
        ultimoPong.put(endpoint, System.currentTimeMillis());
        System.out.println("[HEARTBEAT] PONG de " + endpoint + " — nó ativo.");
    }

    public void reativarVizinho(String endpoint) {
        ultimoPong.put(endpoint, System.currentTimeMillis());
        long novoTimeout = Math.min(
                timeoutAtual.getOrDefault(endpoint, TIMEOUT_INICIAL_MS) * 2,
                TIMEOUT_MAX_MS);
        timeoutAtual.put(endpoint, novoTimeout);
        System.out.println("[HEARTBEAT] Vizinho " + endpoint + " reativado. Timeout=" + novoTimeout + "ms.");
    }

    private void cicloHeartbeat() {
        long agora = System.currentTimeMillis();

        for (String vizinho : node.getVizinhosAtivos()) {
            String[] partes = vizinho.split(":");
            String ip    = partes[0];
            int    porta = Integer.parseInt(partes[1]);

            SocketClient.enviarMensagem(ip, porta, "PING|" + node.getPortaLocal());
            long ultimoTs = ultimoPong.getOrDefault(vizinho, agora);
            long limite   = timeoutAtual.getOrDefault(vizinho, TIMEOUT_INICIAL_MS);

            if (agora - ultimoTs > limite) {
                System.out.println("[HEARTBEAT] ⚠️  Vizinho " + vizinho
                        + " suspeito de crash (sem PONG há "
                        + (agora - ultimoTs) / 1000 + "s). Removendo.");
                node.removerVizinho(vizinho);
                ultimoPong.remove(vizinho);
                timeoutAtual.remove(vizinho);
            }
        }
    }
}