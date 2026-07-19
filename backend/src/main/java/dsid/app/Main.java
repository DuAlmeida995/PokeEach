// Caminho: src/main/java/dsid/app/Main.java
package dsid.app;

import dsid.api.RestServer;
import dsid.core.Blockchain;
import dsid.core.Transaction;
import dsid.crypto.Wallet;
import dsid.network.BlockchainSerializer;
import dsid.network.P2PNode;
import dsid.service.MiningService;
import dsid.storage.LedgerRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Ponto de entrada da aplicação PokeEach.
 *
 * Uso:
 *   # Nó semente (porta P2P 8081, API REST na 9081):
 *   java -jar pokeeach.jar 8081
 *
 *   # Segundo nó:
 *   java -jar pokeeach.jar 8082 127.0.0.1:8081
 *
 *   # Sem argumentos → modo demo local
 */
public class Main {

    private static final int DIFFICULTY = 2;

    public static void main(String[] args) throws Exception {
        if (args.length >= 1) {
            modoP2P(args);
        } else {
            modoDemo();
        }
    }

    // ---------------------------------------------------------------
    // Modo P2P real
    // ---------------------------------------------------------------
    private static void modoP2P(String[] args) throws Exception {
        int portaP2P  = Integer.parseInt(args[0]);
        int portaRest = 9000 + (portaP2P - 8000); // ex: 8081 → 9081

        // Banco de dados exclusivo por nó — evita compartilhamento de estado
        String nomeDb = "blockchain_" + portaP2P + ".db";
        dsid.storage.SQLiteConnection.setDbName(nomeDb);
        System.out.println("[STORAGE] Banco de dados: " + nomeDb);

        System.out.println("╔══════════════════════════════════════╗");
        System.out.printf( "║    PokeEach Node — P2P %-5d  REST %-4d║%n", portaP2P, portaRest);
        System.out.println("╚══════════════════════════════════════╝");

        String           nomeTreinador = "treinador_" + portaP2P;
        Wallet           wallet        = new Wallet(nomeTreinador);
        LedgerRepository repository    = new LedgerRepository();
        Blockchain       blockchain    = new Blockchain(DIFFICULTY);
        MiningService    miner         = new MiningService(blockchain, repository);
        P2PNode          node          = new P2PNode(blockchain, repository, portaP2P);

        // Adiciona vizinhos passados como argumentos
        for (int i = 1; i < args.length; i++) {
            String[] partes = args[i].split(":");
            node.adicionarVizinho(partes[0], Integer.parseInt(partes[1]));
        }

        node.iniciar();

        // Inicia API REST
        RestServer rest = new RestServer(portaRest, node, blockchain, repository, miner, wallet);
        rest.iniciar();

        // Sincroniza com vizinhos
        Thread.sleep(1000);
        node.syncInicial();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n[MAIN] Pressione ENTER para minerar um bloco de teste...");
        br.readLine();

        // Cria TX de teste e minera
        Transaction tx = new Transaction(
                wallet.getChavePublica(), wallet.getChavePublica(),
                "CAPTURA_" + System.currentTimeMillis());
        tx.generateSignature(wallet.getChavePrivada());
        blockchain.adicionarTransacao(tx);

        dsid.core.Block bloco = miner.minerarBlocoPendente(wallet.getChavePublica());
        if (bloco != null) {
            node.broadcast("NEW_BLOCK|" + BlockchainSerializer.serializarBloco(bloco));
        }

        System.out.println("\n[MAIN] Pressione ENTER para encerrar...");
        br.readLine();

        rest.parar();
        node.parar();
    }

    // ---------------------------------------------------------------
    // Modo demo local
    // ---------------------------------------------------------------
    private static void modoDemo() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║       PokeEach Blockchain Demo       ║");
        System.out.println("╚══════════════════════════════════════╝\n");

        System.out.println("── Passo 1: Carteiras ──────────────────");
        Wallet ash       = new Wallet("ash");
        Wallet misty     = new Wallet("misty");
        Wallet minerador = new Wallet("minerador_brock");

        System.out.println("── Passo 2: Blockchain + Banco ─────────");
        LedgerRepository repository = new LedgerRepository();
        Blockchain       blockchain = new Blockchain(DIFFICULTY);
        MiningService    miner      = new MiningService(blockchain, repository);

        System.out.println("── Passo 3: Transações ─────────────────");
        Transaction tx1 = new Transaction(ash.getChavePublica(), misty.getChavePublica(), "Pikachu");
        tx1.generateSignature(ash.getChavePrivada());
        blockchain.adicionarTransacao(tx1);

        Transaction tx2 = new Transaction(misty.getChavePublica(), ash.getChavePublica(), "Starmie");
        tx2.generateSignature(misty.getChavePrivada());
        blockchain.adicionarTransacao(tx2);

        System.out.println("\n── Passo 3b: Teste Double-Spend ────────");
        try {
            Transaction txFraude = new Transaction(ash.getChavePublica(), minerador.getChavePublica(), "Pikachu");
            txFraude.generateSignature(ash.getChavePrivada());
            blockchain.adicionarTransacao(txFraude);
            System.out.println("❌ Double-spend não detectado!");
        } catch (IllegalStateException e) {
            System.out.println("✅ Double-spend bloqueado: " + e.getMessage());
        }

        System.out.println("\n── Passo 4: Mineração ──────────────────");
        var bloco = miner.minerarBlocoPendente(minerador.getChavePublica());

        System.out.println("\n── Passo 5: Validação ──────────────────");
        System.out.println("Cadeia válida? " + (blockchain.isCadeiaValida() ? "✅ Sim" : "❌ Não"));

        System.out.println("\n── Passo 6: Inventários ─────────────────");
        List<String> invAsh      = repository.getInventarioDoTreinador(ash.getChavePublica());
        List<String> invMisty    = repository.getInventarioDoTreinador(misty.getChavePublica());
        List<String> recompensas = repository.getPokemonsRecompensaDoMinerador(minerador.getChavePublica());
        System.out.println("Ash:       " + invAsh);
        System.out.println("Misty:     " + invMisty);
        System.out.println("Minerador: " + recompensas);

        if (bloco != null) {
            System.out.println("\n── Passo 7: Bloco ───────────────────────");
            System.out.println(bloco);
        }

        System.out.println("\n══ Demo finalizado ══");
    }
}