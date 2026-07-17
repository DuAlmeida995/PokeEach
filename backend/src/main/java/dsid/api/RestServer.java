// Caminho: src/main/java/dsid/api/RestServer.java
package dsid.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dsid.core.Blockchain;
import dsid.crypto.Wallet;
import dsid.network.BlockchainSerializer;
import dsid.network.P2PNode;
import dsid.service.MiningService;
import dsid.storage.LedgerRepository;
import dsid.utils.KeyUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.Executors;

public class RestServer {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final HttpServer       server;
    private final P2PNode          node;
    private final Blockchain       blockchain;
    private final LedgerRepository repository;
    private final MiningService    miner;
    private final Wallet           wallet;

    public RestServer(int porta, P2PNode node, Blockchain blockchain,
                      LedgerRepository repository, MiningService miner, Wallet wallet)
            throws IOException {
        this.node       = node;
        this.blockchain = blockchain;
        this.repository = repository;
        this.miner      = miner;
        this.wallet     = wallet;

        // Escuta em 0.0.0.0 para aceitar conexões internas do proxy
        server = HttpServer.create(new InetSocketAddress(porta), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/status",     this::handleStatus);
        server.createContext("/inventario", this::handleInventario);
        server.createContext("/inventario/", this::handleInventario);
        server.createContext("/peers",      this::handlePeers);
        server.createContext("/minerar",    this::handleMinerar);
        server.createContext("/transacao",  this::handleTransacao);
    }

    public void iniciar() {
        server.start();
        System.out.println("[REST] API em http://localhost:" + server.getAddress().getPort());
    }

    public void parar() { server.stop(0); }

    // ---------------------------------------------------------------
    // GET /status
    // ---------------------------------------------------------------
    private void handleStatus(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("height",        blockchain.getUltimoBloco().getHeight());
        body.put("peers",         node.getVizinhosAtivos().size());
        body.put("mempool",       blockchain.getPendingTransactions().size());
        body.put("chavePublica",  wallet.getEnderecoPublico());
        body.put("nomeTreinador", "treinador_" + node.getPortaLocal());
        responder(ex, 200, body);
    }

    // ---------------------------------------------------------------
    // GET /inventario         → inventário local
    // GET /inventario/{addr}  → proxy para /inventario do nó rival
    //    addr = "IP:portaP2P" ex: "127.0.0.1:8082"
    // ---------------------------------------------------------------
    private void handleInventario(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;

        // Lê query string: /inventario?peer=127.0.0.1%3A8082
        String query = ex.getRequestURI().getQuery();
        String peerParam = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("peer=")) {
                    peerParam = java.net.URLDecoder.decode(
                        param.substring(5), StandardCharsets.UTF_8);
                    break;
                }
            }
        }

        // ── Inventário LOCAL ────────────────────────────────────────
        if (peerParam == null || peerParam.isBlank()) {
            List<String> trocas      = repository.getInventarioDoTreinador(wallet.getChavePublica());
            List<String> recompensas = repository.getPokemonsRecompensaDoMinerador(wallet.getChavePublica());

            Set<String> todos = new LinkedHashSet<>();
            todos.addAll(trocas);
            todos.addAll(recompensas);

            List<Map<String, Object>> lista = new ArrayList<>();
            for (String nome : todos) {
                if (nome.startsWith("CAPTURA_") || nome.equals("__MINE__")) continue;
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("nome", nome);
                p.put("id",   pokemonNameToId(nome));
                lista.add(p);
            }
            responder(ex, 200, Map.of("pokemon", lista,
                                       "chave",   wallet.getEnderecoPublico()));
            return;
        }

        // ── Inventário do RIVAL — proxy HTTP ───────────────────────
        String invUrl = "";
        try {
            String[] partes = peerParam.split(":");
            int portaP2P    = Integer.parseInt(partes[1].trim());
            int portaRest   = 9000 + (portaP2P - 8000);
            invUrl = "http://127.0.0.1:" + portaRest + "/inventario";

            System.out.println("[REST] Proxy inventario → " + invUrl);

            java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) new java.net.URL(invUrl).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code == 200) {
                String json  = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                // Não adiciona CORS aqui — já foi adicionado pelo method() acima
                responder(ex, 200, com.google.gson.JsonParser.parseString(json));
            } else {
                System.err.println("[REST] Proxy retornou HTTP " + code + " de " + invUrl);
                responder(ex, 502, Map.of("erro", "Peer retornou HTTP " + code));
            }
        } catch (Exception e) {
            System.err.println("[REST] Proxy erro: " + e.getMessage() + " | URL: " + invUrl);
            responder(ex, 502, Map.of("erro", "Nao foi possivel contatar o peer: " + e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // GET /peers
    // ---------------------------------------------------------------
    private void handlePeers(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;

        List<Map<String, Object>> lista = new ArrayList<>();

        for (String endpoint : node.getVizinhosAtivos()) {
            Map<String, Object> peer = new LinkedHashMap<>();
            peer.put("endereco", endpoint);

            String statusUrl = "";
            try {
                String[] partes = endpoint.split(":");
                int portaP2P    = Integer.parseInt(partes[1].trim());
                int portaRest   = 9000 + (portaP2P - 8000);
                statusUrl = "http://127.0.0.1:" + portaRest + "/status";

                java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) new java.net.URL(statusUrl).openConnection();
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(1500);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> st = GSON.fromJson(json, Map.class);
                    peer.put("nome",         st.getOrDefault("nomeTreinador", endpoint));
                    peer.put("chavePublica", st.getOrDefault("chavePublica",  ""));
                    peer.put("height",       st.getOrDefault("height",        0));
                    peer.put("online",       true);
                } else {
                    peer.put("nome",   endpoint);
                    peer.put("online", false);
                }
            } catch (Exception e) {
                System.err.println("[REST] Peers status erro: " + e.getMessage() + " | " + statusUrl);
                peer.put("nome",   endpoint);
                peer.put("online", false);
            }

            lista.add(peer);
        }

        responder(ex, 200, Map.of("peers", lista));
    }

    // ---------------------------------------------------------------
    // POST /minerar
    // ---------------------------------------------------------------
    private void handleMinerar(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;

        dsid.core.Block bloco = miner.minerarBlocoPendente(wallet.getChavePublica());

        if (bloco == null) {
            try {
                dsid.core.Transaction tx = new dsid.core.Transaction(
                        wallet.getChavePublica(), wallet.getChavePublica(), "__MINE__");
                tx.generateSignature(wallet.getChavePrivada());
                blockchain.adicionarTransacao(tx);
                bloco = miner.minerarBlocoPendente(wallet.getChavePublica());
            } catch (Exception e) {
                responder(ex, 400, Map.of("erro", "Falha na mineracao: " + e.getMessage()));
                return;
            }
        }

        if (bloco == null) {
            responder(ex, 400, Map.of("erro", "Falha na mineracao."));
            return;
        }

        node.broadcast("NEW_BLOCK|" + BlockchainSerializer.serializarBloco(bloco));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sucesso",       true);
        resp.put("height",        bloco.getHeight());
        resp.put("hash",          bloco.getHash());
        resp.put("rewardPokemon", bloco.getRewardPokemon());
        resp.put("rewardId",      pokemonNameToId(bloco.getRewardPokemon()));
        responder(ex, 200, resp);
    }

    // ---------------------------------------------------------------
    // POST /transacao
    // ---------------------------------------------------------------
    private void handleTransacao(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;

        String bodyStr = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = GSON.fromJson(bodyStr, Map.class);

        if (body == null) { responder(ex, 400, Map.of("erro", "JSON invalido")); return; }

        String destBase64 = (String) body.get("destinatario");
        String idPokemon  = (String) body.get("idPokemon");

        if (destBase64 == null || idPokemon == null) {
            responder(ex, 400, Map.of("erro", "Campos 'destinatario' e 'idPokemon' obrigatorios."));
            return;
        }

        try {
            PublicKey destKey = KeyUtils.stringToPublicKey(destBase64);
            dsid.core.Transaction tx = new dsid.core.Transaction(
                    wallet.getChavePublica(), destKey, idPokemon);
            tx.generateSignature(wallet.getChavePrivada());
            blockchain.adicionarTransacao(tx);
            node.broadcast("TX|" + BlockchainSerializer.serializarTransacao(tx));

            responder(ex, 200, Map.of(
                    "sucesso",       true,
                    "transactionId", tx.getTransactionId(),
                    "pokemon",       idPokemon));

        } catch (IllegalStateException e) {
            responder(ex, 409, Map.of("erro", "Double-spend: " + e.getMessage()));
        } catch (Exception e) {
            responder(ex, 500, Map.of("erro", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private boolean method(HttpExchange ex, String esperado) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return false;
        }
        if (!esperado.equals(ex.getRequestMethod())) {
            responder(ex, 405, Map.of("erro", "Metodo nao permitido"));
            return false;
        }
        return true;
    }

    private void responder(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static final Map<String, Integer> POKEMON_IDS = new HashMap<>();
    static {
        POKEMON_IDS.put("Bulbasaur",  1);  POKEMON_IDS.put("Charmander",  4);
        POKEMON_IDS.put("Squirtle",   7);  POKEMON_IDS.put("Pikachu",    25);
        POKEMON_IDS.put("Eevee",    133);  POKEMON_IDS.put("Meowth",     52);
        POKEMON_IDS.put("Psyduck",   54);  POKEMON_IDS.put("Geodude",    74);
        POKEMON_IDS.put("Machop",    66);  POKEMON_IDS.put("Gastly",     92);
        POKEMON_IDS.put("Magikarp", 129);  POKEMON_IDS.put("Ditto",     132);
        POKEMON_IDS.put("Snorlax",  143);  POKEMON_IDS.put("Lapras",    131);
        POKEMON_IDS.put("Jolteon",  135);  POKEMON_IDS.put("Flareon",   136);
        POKEMON_IDS.put("Vaporeon", 134);  POKEMON_IDS.put("Porygon",   137);
        POKEMON_IDS.put("Mew",      151);  POKEMON_IDS.put("Dragonite", 149);
        POKEMON_IDS.put("__MINE__",   0);
    }

    private int pokemonNameToId(String nome) {
        return POKEMON_IDS.getOrDefault(nome, 1);
    }
}