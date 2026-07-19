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

    // Estado de troca pendente (solicitação recebida aguardando resposta)
    private volatile Map<String, Object> trocaPendente = null;

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
        server.createContext("/transacao",       this::handleTransacao);
        server.createContext("/troca/solicitar", this::handleTrocaSolicitar);
        server.createContext("/troca/receber",   this::handleTrocaReceber);
        server.createContext("/troca/confirmar", this::handleTrocaConfirmar);
        server.createContext("/troca/pendente",  this::handleTrocaPendente);
        server.createContext("/troca/responder", this::handleTrocaResponder);
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
    // POST /troca/solicitar
    // Envia solicitação de troca para o nó rival via HTTP
    // body: { enderecoRival, meuPokemon, pokemonSolicitado }
    // ---------------------------------------------------------------
    private void handleTrocaSolicitar(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;

        String bodyStr = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = GSON.fromJson(bodyStr, Map.class);

        String enderecoRival    = (String) body.get("enderecoRival");
        String meuPokemon       = (String) body.get("meuPokemon");
        String pokemonSolicitado= (String) body.get("pokemonSolicitado");

        if (enderecoRival == null || meuPokemon == null || pokemonSolicitado == null) {
            responder(ex, 400, Map.of("erro", "Campos obrigatorios ausentes."));
            return;
        }

        try {
            String[] partes  = enderecoRival.split(":");
            int portaP2P     = Integer.parseInt(partes[1].trim());
            int portaRest    = 9000 + (portaP2P - 8000);
            String url       = "http://127.0.0.1:" + portaRest + "/troca/receber";

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("remetente",        "treinador_" + node.getPortaLocal());
            payload.put("chaveRemetente",   wallet.getEnderecoPublico());
            payload.put("pokemonOferecido", meuPokemon);
            payload.put("pokemonSolicitado",pokemonSolicitado);
            payload.put("enderecoRemetente","127.0.0.1:" + node.getPortaLocal());

            byte[] payloadBytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);

            java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(payloadBytes);

            int code = conn.getResponseCode();
            if (code == 200) {
                responder(ex, 200, Map.of("sucesso", true, "mensagem", "Solicitacao enviada ao rival."));
            } else {
                responder(ex, 502, Map.of("erro", "Rival retornou HTTP " + code));
            }
        } catch (Exception e) {
            responder(ex, 502, Map.of("erro", "Nao foi possivel contatar o rival: " + e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // POST /troca/receber  (chamado pelo nó do solicitante diretamente)
    // Armazena a solicitação pendente para o frontend consultar
    // ---------------------------------------------------------------
    private void handleTrocaReceber(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        String bodyStr = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = GSON.fromJson(bodyStr, Map.class);
        trocaPendente = body;
        System.out.println("[REST] Solicitacao de troca recebida de: " + body.get("remetente"));
        responder(ex, 200, Map.of("sucesso", true));
    }

    // ---------------------------------------------------------------
    // GET /troca/pendente
    // Frontend consulta se há solicitação pendente
    // ---------------------------------------------------------------
    private void handleTrocaPendente(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;
        if (trocaPendente != null) {
            Map<String, Object> resp = new LinkedHashMap<>(trocaPendente);
            resp.put("pendente", true);
            responder(ex, 200, resp);
        } else {
            responder(ex, 200, Map.of("pendente", false));
        }
    }

    // ---------------------------------------------------------------
    // POST /troca/responder
    // body: { aceitar: true/false }
    // ---------------------------------------------------------------
    private void handleTrocaResponder(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;

        String bodyStr = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = GSON.fromJson(bodyStr, Map.class);
        boolean aceitar = Boolean.TRUE.equals(body.get("aceitar"));

        if (trocaPendente == null) {
            responder(ex, 400, Map.of("erro", "Nenhuma troca pendente."));
            return;
        }

        if (!aceitar) {
            System.out.println("[REST] Troca recusada pelo treinador local.");
            trocaPendente = null;
            responder(ex, 200, Map.of("sucesso", true, "aceito", false));
            return;
        }

        // Aceita: executa as duas TXs — uma em cada direção
        try {
            String chaveRemetente    = (String) trocaPendente.get("chaveRemetente");
            String pokemonOferecido  = (String) trocaPendente.get("pokemonOferecido");
            String pokemonSolicitado = (String) trocaPendente.get("pokemonSolicitado");
            String enderecoRemetente = (String) trocaPendente.get("enderecoRemetente");

            // TX 1: remetente → eu (pokemonOferecido vem para mim)
            java.security.PublicKey chaveRem = dsid.utils.KeyUtils.stringToPublicKey(chaveRemetente);
            // Nota: a TX de transferência do remetente para mim será criada pelo remetente
            // Aqui criamos apenas a nossa TX de envio do pokemonSolicitado para o remetente

            // TX local: eu envio pokemonSolicitado para o remetente
            dsid.core.Transaction txLocal = new dsid.core.Transaction(
                    wallet.getChavePublica(), chaveRem, pokemonSolicitado);
            txLocal.generateSignature(wallet.getChavePrivada());
            blockchain.adicionarTransacao(txLocal);
            node.broadcast("TX|" + dsid.network.BlockchainSerializer.serializarTransacao(txLocal));

            // Notifica o remetente para criar a TX dele (pokemonOferecido → eu)
            String[] partes  = enderecoRemetente.split(":");
            int portaRest    = 9000 + (Integer.parseInt(partes[1].trim()) - 8000);
            String urlConf   = "http://127.0.0.1:" + portaRest + "/troca/confirmar";

            Map<String, Object> conf = Map.of(
                "chaveDestinatario", wallet.getEnderecoPublico(),
                "pokemon",           pokemonOferecido
            );
            byte[] confBytes = GSON.toJson(conf).getBytes(StandardCharsets.UTF_8);

            java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) new java.net.URL(urlConf).openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(confBytes);
            conn.getResponseCode();

            trocaPendente = null;
            System.out.println("[REST] Troca aceita! TX criadas. Minerando bloco...");

            // Minera em background para confirmar as TXs (sem recompensa de Pokémon)
            new Thread(() -> {
                try {
                    Thread.sleep(800); // aguarda TX do remetente chegar via /confirmar
                    minerarSemRecompensa();
                } catch (Exception e) {
                    System.err.println("[REST] Erro ao minerar bloco de troca: " + e.getMessage());
                }
            }).start();

            responder(ex, 200, Map.of("sucesso", true, "aceito", true));

        } catch (Exception e) {
            responder(ex, 500, Map.of("erro", "Erro ao executar troca: " + e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // POST /troca/confirmar
    // Chamado pelo receptor quando aceita — solicita ao remetente criar sua TX
    // body: { chaveDestinatario, pokemon }
    // ---------------------------------------------------------------
    private void handleTrocaConfirmar(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;

        String bodyStr = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = GSON.fromJson(bodyStr, Map.class);

        String chaveDestStr = (String) body.get("chaveDestinatario");
        String pokemon      = (String) body.get("pokemon");

        try {
            java.security.PublicKey chaveDest = dsid.utils.KeyUtils.stringToPublicKey(chaveDestStr);
            dsid.core.Transaction tx = new dsid.core.Transaction(
                    wallet.getChavePublica(), chaveDest, pokemon);
            tx.generateSignature(wallet.getChavePrivada());
            blockchain.adicionarTransacao(tx);
            node.broadcast("TX|" + dsid.network.BlockchainSerializer.serializarTransacao(tx));

            System.out.println("[REST] TX de confirmacao criada: " + pokemon + " → destinatario");

            // Minera em background para confirmar a TX (sem recompensa de Pokémon)
            new Thread(() -> {
                try {
                    Thread.sleep(300);
                    minerarSemRecompensa();
                } catch (Exception e) {
                    System.err.println("[REST] Erro ao minerar bloco de confirmacao: " + e.getMessage());
                }
            }).start();

            responder(ex, 200, Map.of("sucesso", true));
        } catch (Exception e) {
            responder(ex, 500, Map.of("erro", e.getMessage()));
        }
    }


    /**
     * Minera um bloco apenas para confirmar TXs pendentes.
     * Não gera recompensa de Pokémon — usa TX interna __MINE__ descartada pelo inventário.
     */
    private void minerarSemRecompensa() {
        try {
            if (blockchain.getPendingTransactions().isEmpty()) {
                dsid.core.Transaction dummy = new dsid.core.Transaction(
                        wallet.getChavePublica(), wallet.getChavePublica(), "__MINE__");
                dummy.generateSignature(wallet.getChavePrivada());
                blockchain.adicionarTransacao(dummy);
            }
            // Usa minerarBlocoConfirmacao — não gera recompensa de Pokémon (__NONE__)
            dsid.core.Block bloco = miner.minerarBlocoConfirmacao(wallet.getChavePublica());
            if (bloco != null) {
                node.broadcast("NEW_BLOCK|" + dsid.network.BlockchainSerializer.serializarBloco(bloco));
                System.out.println("[REST] Bloco de confirmação minerado: #" + bloco.getHeight());
            }
        } catch (Exception e) {
            System.err.println("[REST] Erro em minerarSemRecompensa: " + e.getMessage());
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
        POKEMON_IDS.put("Bulbasaur", 1);
        POKEMON_IDS.put("Ivysaur", 2);
        POKEMON_IDS.put("Venusaur", 3);
        POKEMON_IDS.put("Charmander", 4);
        POKEMON_IDS.put("Charmeleon", 5);
        POKEMON_IDS.put("Charizard", 6);
        POKEMON_IDS.put("Squirtle", 7);
        POKEMON_IDS.put("Wartortle", 8);
        POKEMON_IDS.put("Blastoise", 9);
        POKEMON_IDS.put("Caterpie", 10);
        POKEMON_IDS.put("Metapod", 11);
        POKEMON_IDS.put("Butterfree", 12);
        POKEMON_IDS.put("Weedle", 13);
        POKEMON_IDS.put("Kakuna", 14);
        POKEMON_IDS.put("Beedrill", 15);
        POKEMON_IDS.put("Pidgey", 16);
        POKEMON_IDS.put("Pidgeotto", 17);
        POKEMON_IDS.put("Pidgeot", 18);
        POKEMON_IDS.put("Rattata", 19);
        POKEMON_IDS.put("Raticate", 20);
        POKEMON_IDS.put("Spearow", 21);
        POKEMON_IDS.put("Fearow", 22);
        POKEMON_IDS.put("Ekans", 23);
        POKEMON_IDS.put("Arbok", 24);
        POKEMON_IDS.put("Pikachu", 25);
        POKEMON_IDS.put("Raichu", 26);
        POKEMON_IDS.put("Sandshrew", 27);
        POKEMON_IDS.put("Sandslash", 28);
        POKEMON_IDS.put("NidoranF", 29);
        POKEMON_IDS.put("Nidorina", 30);
        POKEMON_IDS.put("Nidoqueen", 31);
        POKEMON_IDS.put("NidoranM", 32);
        POKEMON_IDS.put("Nidorino", 33);
        POKEMON_IDS.put("Nidoking", 34);
        POKEMON_IDS.put("Clefairy", 35);
        POKEMON_IDS.put("Clefable", 36);
        POKEMON_IDS.put("Vulpix", 37);
        POKEMON_IDS.put("Ninetales", 38);
        POKEMON_IDS.put("Jigglypuff", 39);
        POKEMON_IDS.put("Wigglytuff", 40);
        POKEMON_IDS.put("Zubat", 41);
        POKEMON_IDS.put("Golbat", 42);
        POKEMON_IDS.put("Oddish", 43);
        POKEMON_IDS.put("Gloom", 44);
        POKEMON_IDS.put("Vileplume", 45);
        POKEMON_IDS.put("Paras", 46);
        POKEMON_IDS.put("Parasect", 47);
        POKEMON_IDS.put("Venonat", 48);
        POKEMON_IDS.put("Venomoth", 49);
        POKEMON_IDS.put("Diglett", 50);
        POKEMON_IDS.put("Dugtrio", 51);
        POKEMON_IDS.put("Meowth", 52);
        POKEMON_IDS.put("Persian", 53);
        POKEMON_IDS.put("Psyduck", 54);
        POKEMON_IDS.put("Golduck", 55);
        POKEMON_IDS.put("Mankey", 56);
        POKEMON_IDS.put("Primeape", 57);
        POKEMON_IDS.put("Growlithe", 58);
        POKEMON_IDS.put("Arcanine", 59);
        POKEMON_IDS.put("Poliwag", 60);
        POKEMON_IDS.put("Poliwhirl", 61);
        POKEMON_IDS.put("Poliwrath", 62);
        POKEMON_IDS.put("Abra", 63);
        POKEMON_IDS.put("Kadabra", 64);
        POKEMON_IDS.put("Alakazam", 65);
        POKEMON_IDS.put("Machop", 66);
        POKEMON_IDS.put("Machoke", 67);
        POKEMON_IDS.put("Machamp", 68);
        POKEMON_IDS.put("Bellsprout", 69);
        POKEMON_IDS.put("Weepinbell", 70);
        POKEMON_IDS.put("Victreebel", 71);
        POKEMON_IDS.put("Tentacool", 72);
        POKEMON_IDS.put("Tentacruel", 73);
        POKEMON_IDS.put("Geodude", 74);
        POKEMON_IDS.put("Graveler", 75);
        POKEMON_IDS.put("Golem", 76);
        POKEMON_IDS.put("Ponyta", 77);
        POKEMON_IDS.put("Rapidash", 78);
        POKEMON_IDS.put("Slowpoke", 79);
        POKEMON_IDS.put("Slowbro", 80);
        POKEMON_IDS.put("Magnemite", 81);
        POKEMON_IDS.put("Magneton", 82);
        POKEMON_IDS.put("Farfetchd", 83);
        POKEMON_IDS.put("Doduo", 84);
        POKEMON_IDS.put("Dodrio", 85);
        POKEMON_IDS.put("Seel", 86);
        POKEMON_IDS.put("Dewgong", 87);
        POKEMON_IDS.put("Grimer", 88);
        POKEMON_IDS.put("Muk", 89);
        POKEMON_IDS.put("Shellder", 90);
        POKEMON_IDS.put("Cloyster", 91);
        POKEMON_IDS.put("Gastly", 92);
        POKEMON_IDS.put("Haunter", 93);
        POKEMON_IDS.put("Gengar", 94);
        POKEMON_IDS.put("Onix", 95);
        POKEMON_IDS.put("Drowzee", 96);
        POKEMON_IDS.put("Hypno", 97);
        POKEMON_IDS.put("Krabby", 98);
        POKEMON_IDS.put("Kingler", 99);
        POKEMON_IDS.put("Voltorb", 100);
        POKEMON_IDS.put("Electrode", 101);
        POKEMON_IDS.put("Exeggcute", 102);
        POKEMON_IDS.put("Exeggutor", 103);
        POKEMON_IDS.put("Cubone", 104);
        POKEMON_IDS.put("Marowak", 105);
        POKEMON_IDS.put("Hitmonlee", 106);
        POKEMON_IDS.put("Hitmonchan", 107);
        POKEMON_IDS.put("Lickitung", 108);
        POKEMON_IDS.put("Koffing", 109);
        POKEMON_IDS.put("Weezing", 110);
        POKEMON_IDS.put("Rhyhorn", 111);
        POKEMON_IDS.put("Rhydon", 112);
        POKEMON_IDS.put("Chansey", 113);
        POKEMON_IDS.put("Tangela", 114);
        POKEMON_IDS.put("Kangaskhan", 115);
        POKEMON_IDS.put("Horsea", 116);
        POKEMON_IDS.put("Seadra", 117);
        POKEMON_IDS.put("Goldeen", 118);
        POKEMON_IDS.put("Seaking", 119);
        POKEMON_IDS.put("Staryu", 120);
        POKEMON_IDS.put("Starmie", 121);
        POKEMON_IDS.put("MrMime", 122);
        POKEMON_IDS.put("Scyther", 123);
        POKEMON_IDS.put("Jynx", 124);
        POKEMON_IDS.put("Electabuzz", 125);
        POKEMON_IDS.put("Magmar", 126);
        POKEMON_IDS.put("Pinsir", 127);
        POKEMON_IDS.put("Tauros", 128);
        POKEMON_IDS.put("Magikarp", 129);
        POKEMON_IDS.put("Gyarados", 130);
        POKEMON_IDS.put("Lapras", 131);
        POKEMON_IDS.put("Ditto", 132);
        POKEMON_IDS.put("Eevee", 133);
        POKEMON_IDS.put("Vaporeon", 134);
        POKEMON_IDS.put("Jolteon", 135);
        POKEMON_IDS.put("Flareon", 136);
        POKEMON_IDS.put("Porygon", 137);
        POKEMON_IDS.put("Omanyte", 138);
        POKEMON_IDS.put("Omastar", 139);
        POKEMON_IDS.put("Kabuto", 140);
        POKEMON_IDS.put("Kabutops", 141);
        POKEMON_IDS.put("Aerodactyl", 142);
        POKEMON_IDS.put("Snorlax", 143);
        POKEMON_IDS.put("Articuno", 144);
        POKEMON_IDS.put("Zapdos", 145);
        POKEMON_IDS.put("Moltres", 146);
        POKEMON_IDS.put("Dratini", 147);
        POKEMON_IDS.put("Dragonair", 148);
        POKEMON_IDS.put("Dragonite", 149);
        POKEMON_IDS.put("Mewtwo", 150);
        POKEMON_IDS.put("Mew", 151);
        POKEMON_IDS.put("__MINE__", 0);
        POKEMON_IDS.put("__NONE__", 0);
    }
    private int pokemonNameToId(String nome) {
        return POKEMON_IDS.getOrDefault(nome, 1);
    }
}