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

/**
 * Servidor HTTP REST embutido no JAR — sem dependências externas.
 * Usa com.sun.net.httpserver.HttpServer (Java 17 nativo).
 *
 * Endpoints:
 *   GET  /status               → height, peers, mempool size, chave pública local
 *   GET  /inventario           → Pokémon do treinador local
 *   GET  /inventario/{chave}   → Pokémon de outro treinador (pela chave pública Base64)
 *   GET  /peers                → lista de nós online conhecidos
 *   POST /minerar              → minera um bloco, retorna Pokémon sorteado
 *   POST /transacao            → cria e submete TX à mempool + broadcast
 */
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

        server = HttpServer.create(new InetSocketAddress("localhost", porta), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        // Registra rotas
        server.createContext("/status",     this::handleStatus);
        server.createContext("/inventario", this::handleInventario);
        server.createContext("/peers",      this::handlePeers);
        server.createContext("/minerar",    this::handleMinerar);
        server.createContext("/transacao",  this::handleTransacao);
    }

    public void iniciar() {
        server.start();
        System.out.println("[REST] API disponível em http://localhost:"
                + server.getAddress().getPort());
    }

    public void parar() {
        server.stop(0);
    }

    // ---------------------------------------------------------------
    // GET /status
    // ---------------------------------------------------------------
    private void handleStatus(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("height",       blockchain.getUltimoBloco().getHeight());
        body.put("peers",        node.getVizinhosAtivos().size());
        body.put("mempool",      blockchain.getPendingTransactions().size());
        body.put("chavePublica", wallet.getEnderecoPublico());
        body.put("nomeTreinador","treinador_" + node.getPortaLocal());

        responder(ex, 200, body);
    }

    // ---------------------------------------------------------------
    // GET /inventario          → inventário local
    // GET /inventario/{chave}  → inventário de outro treinador
    // ---------------------------------------------------------------
    private void handleInventario(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;

        String path  = ex.getRequestURI().getPath();
        String[] seg = path.split("/");

        PublicKey chave;
        try {
            if (seg.length >= 3 && !seg[2].isBlank()) {
                // /inventario/{chaveBase64}
                String chaveBase64 = java.net.URLDecoder.decode(seg[2], StandardCharsets.UTF_8);
                chave = dsid.utils.KeyUtils.stringToPublicKey(chaveBase64);
            } else {
                // /inventario → treinador local
                chave = wallet.getChavePublica();
            }
        } catch (Exception e) {
            responder(ex, 400, Map.of("erro", "Chave pública inválida: " + e.getMessage()));
            return;
        }

        List<String> pokemons     = repository.getInventarioDoTreinador(chave);
        List<String> recompensas  = repository.getPokemonsRecompensaDoMinerador(chave);

        // Une inventário de trocas + recompensas de mineração
        Set<String> todos = new LinkedHashSet<>();
        todos.addAll(pokemons);
        todos.addAll(recompensas);

        // Monta lista com id numérico para a PokeAPI
        List<Map<String, Object>> lista = new ArrayList<>();
        for (String nome : todos) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("nome", nome);
            p.put("id",   pokemonNameToId(nome));
            lista.add(p);
        }

        responder(ex, 200, Map.of("pokemon", lista,
                                   "chave",   KeyUtils.publicKeyToString(chave)));
    }

    // ---------------------------------------------------------------
    // GET /peers
    // ---------------------------------------------------------------
    private void handlePeers(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;
        responder(ex, 200, Map.of("peers", node.getVizinhosAtivos()));
    }

    // ---------------------------------------------------------------
    // POST /minerar
    // ---------------------------------------------------------------
    private void handleMinerar(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;

        // Adiciona uma TX de "captura" (self-send) para ter algo na mempool
        try {
            dsid.core.Transaction tx = new dsid.core.Transaction(
                    wallet.getChavePublica(),
                    wallet.getChavePublica(),
                    "CAPTURA_" + System.currentTimeMillis());
            tx.generateSignature(wallet.getChavePrivada());
            blockchain.adicionarTransacao(tx);
        } catch (Exception e) {
            // Se já houver TX pendente de captura, ignora e minera assim mesmo
        }

        dsid.core.Block bloco = miner.minerarBlocoPendente(wallet.getChavePublica());

        if (bloco == null) {
            responder(ex, 400, Map.of("erro", "Mempool vazia ou falha na mineração."));
            return;
        }

        // Propaga o bloco para a rede
        String blocoJson = BlockchainSerializer.serializarBloco(bloco);
        node.broadcast("NEW_BLOCK|" + blocoJson);

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
    // Body JSON: { "destinatario": "<chaveBase64>", "idPokemon": "Pikachu" }
    // ---------------------------------------------------------------
    private void handleTransacao(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;

        String bodyStr = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        Map<?, ?> body;
        try {
            body = GSON.fromJson(bodyStr, Map.class);
        } catch (Exception e) {
            responder(ex, 400, Map.of("erro", "JSON inválido"));
            return;
        }

        String destBase64 = (String) body.get("destinatario");
        String idPokemon  = (String) body.get("idPokemon");

        if (destBase64 == null || idPokemon == null) {
            responder(ex, 400, Map.of("erro", "Campos 'destinatario' e 'idPokemon' são obrigatórios."));
            return;
        }

        try {
            PublicKey destKey = KeyUtils.stringToPublicKey(destBase64);

            dsid.core.Transaction tx = new dsid.core.Transaction(
                    wallet.getChavePublica(), destKey, idPokemon);
            tx.generateSignature(wallet.getChavePrivada());
            blockchain.adicionarTransacao(tx);

            // Propaga a TX para a rede via Gossip
            String txJson = BlockchainSerializer.serializarTransacao(tx);
            node.broadcast("TX|" + txJson);

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

    /** Verifica método HTTP e adiciona headers CORS em toda resposta. */
    private boolean method(HttpExchange ex, String esperado) throws IOException {
        // CORS — permite chamadas do frontend React (localhost:3000)
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return false;
        }
        if (!esperado.equals(ex.getRequestMethod())) {
            responder(ex, 405, Map.of("erro", "Método não permitido"));
            return false;
        }
        return true;
    }

    private void responder(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    /** Mapeia nome de Pokémon para ID numérico da PokeAPI. */
    private static final Map<String, Integer> POKEMON_IDS = new HashMap<>();
    static {
        POKEMON_IDS.put("Bulbasaur",  1);   POKEMON_IDS.put("Charmander", 4);
        POKEMON_IDS.put("Squirtle",   7);   POKEMON_IDS.put("Pikachu",   25);
        POKEMON_IDS.put("Eevee",    133);   POKEMON_IDS.put("Meowth",    52);
        POKEMON_IDS.put("Psyduck",   54);   POKEMON_IDS.put("Geodude",   74);
        POKEMON_IDS.put("Machop",    66);   POKEMON_IDS.put("Gastly",    92);
        POKEMON_IDS.put("Magikarp", 129);   POKEMON_IDS.put("Ditto",    132);
        POKEMON_IDS.put("Snorlax",  143);   POKEMON_IDS.put("Lapras",   131);
        POKEMON_IDS.put("Jolteon",  135);   POKEMON_IDS.put("Flareon",  136);
        POKEMON_IDS.put("Vaporeon", 134);   POKEMON_IDS.put("Porygon",  137);
        POKEMON_IDS.put("Mew",      151);   POKEMON_IDS.put("Dragonite",149);
    }

    private int pokemonNameToId(String nome) {
        return POKEMON_IDS.getOrDefault(nome, 1);
    }
}