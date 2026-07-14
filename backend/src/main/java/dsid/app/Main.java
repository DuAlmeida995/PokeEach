// Caminho: src/main/java/dsid/app/Main.java
package dsid.app;

import dsid.core.Blockchain;
import dsid.core.Transaction;
import dsid.crypto.Wallet;
import dsid.network.P2PNode;
import dsid.service.MiningService;
import dsid.storage.LedgerRepository;

import java.util.List;

/**
 * Ponto de entrada da aplicação PokeEach.
 *
 * Modo de uso:
 *   # Nó semente (sem vizinhos):
 *   java -jar pokeeach.jar 8081
 *
 *   # Nó novo (conhece o semente):
 *   java -jar pokeeach.jar 8082 127.0.0.1:8081
 *
 *   # Terceiro nó (conhece ambos):
 *   java -jar pokeeach.jar 8083 127.0.0.1:8081 127.0.0.1:8082
 *
 * Sem argumentos: executa a simulação local completa (modo demo).
 */
public class Main {

    private static final int DIFFICULTY = 2;

    public static void main(String[] args) throws Exception {

        if (args.length >= 1) {
            // ── Modo P2P real ──────────────────────────────────────
            modoP2P(args);
        } else {
            // ── Modo demo local (sem rede) ─────────────────────────
            modoDemo();
        }
    }

    // ---------------------------------------------------------------
    // Modo P2P: cada instância do JAR é um nó independente
    // ---------------------------------------------------------------
    private static void modoP2P(String[] args) throws Exception {
        int portaLocal = Integer.parseInt(args[0]);
        String nomeTreinador = "treinador_" + portaLocal;

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║    PokeEach Node — porta " + portaLocal + "         ║");
        System.out.println("╚══════════════════════════════════════╝");

        Wallet            wallet     = new Wallet(nomeTreinador);
        LedgerRepository  repository = new LedgerRepository();
        Blockchain        blockchain = new Blockchain(DIFFICULTY);
        MiningService     miner      = new MiningService(blockchain, repository);
        P2PNode           node       = new P2PNode(blockchain, repository, portaLocal);

        // Adiciona vizinhos passados como argumentos (ex: 127.0.0.1:8081)
        for (int i = 1; i < args.length; i++) {
            String[] partes = args[i].split(":");
            node.adicionarVizinho(partes[0], Integer.parseInt(partes[1]));
        }

        node.iniciar();

        // Aguarda 1s para o servidor subir, depois sincroniza
        Thread.sleep(1000);
        node.syncInicial();

        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
        System.out.println("\n[MAIN] Nó pronto. Pressione ENTER para minerar um bloco de teste...");
        br.readLine();

        // Cria uma transação de teste e minera
        Transaction tx = new Transaction(
                wallet.getChavePublica(), wallet.getChavePublica(), "Pikachu_teste");
        tx.generateSignature(wallet.getChavePrivada());
        blockchain.adicionarTransacao(tx);

        var bloco = miner.minerarBlocoPendente(wallet.getChavePublica());
        if (bloco != null) {
            node.broadcast("NEW_BLOCK|" + dsid.network.BlockchainSerializer.serializarBloco(bloco));
        }

        System.out.println("\n[MAIN] Pressione ENTER para encerrar...");
        br.readLine();
        node.parar();
    }

    // ---------------------------------------------------------------
    // Modo demo: simulação local completa (sem sockets reais)
    // ---------------------------------------------------------------
    private static void modoDemo() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║       PokeEach Blockchain Demo       ║");
        System.out.println("╚══════════════════════════════════════╝\n");

        System.out.println("── Passo 1: Carteiras ──────────────────");
        Wallet ash      = new Wallet("ash");
        Wallet misty    = new Wallet("misty");
        Wallet minerador = new Wallet("minerador_brock");
        System.out.println("Ash       → " + ash.getEnderecoPublico().substring(0, 24) + "...");
        System.out.println("Misty     → " + misty.getEnderecoPublico().substring(0, 24) + "...");
        System.out.println("Minerador → " + minerador.getEnderecoPublico().substring(0, 24) + "...\n");

        System.out.println("── Passo 2: Blockchain + Banco de Dados ─");
        LedgerRepository repository = new LedgerRepository();
        Blockchain blockchain = new Blockchain(DIFFICULTY);
        MiningService miner   = new MiningService(blockchain, repository);
        System.out.println();

        System.out.println("── Passo 3: Transações ─────────────────");
        Transaction tx1 = new Transaction(ash.getChavePublica(), misty.getChavePublica(), "Pikachu");
        tx1.generateSignature(ash.getChavePrivada());
        blockchain.adicionarTransacao(tx1);

        Transaction tx2 = new Transaction(misty.getChavePublica(), ash.getChavePublica(), "Starmie");
        tx2.generateSignature(misty.getChavePrivada());
        blockchain.adicionarTransacao(tx2);

        // Testa proteção de double-spend
        System.out.println("\n── Passo 3b: Teste de Double-Spend ─────");
        try {
            Transaction txFraude = new Transaction(ash.getChavePublica(), minerador.getChavePublica(), "Pikachu");
            txFraude.generateSignature(ash.getChavePrivada());
            blockchain.adicionarTransacao(txFraude);
            System.out.println("❌ Double-spend não detectado!");
        } catch (IllegalStateException e) {
            System.out.println("✅ Double-spend bloqueado: " + e.getMessage());
        }
        System.out.println();

        System.out.println("── Passo 4: Mineração ──────────────────");
        var blocoMinerado = miner.minerarBlocoPendente(minerador.getChavePublica());
        System.out.println();

        System.out.println("── Passo 5: Validação da Cadeia ────────");
        System.out.println("Cadeia válida? " + (blockchain.isCadeiaValida() ? "✅ Sim" : "❌ Não") + "\n");

        System.out.println("── Passo 6: Inventários no Banco ───────");
        List<String> invAsh      = repository.getInventarioDoTreinador(ash.getChavePublica());
        List<String> invMisty    = repository.getInventarioDoTreinador(misty.getChavePublica());
        List<String> recompensas = repository.getPokemonsRecompensaDoMinerador(minerador.getChavePublica());
        System.out.println("Inventário do Ash:        " + invAsh);
        System.out.println("Inventário da Misty:      " + invMisty);
        System.out.println("Recompensas do Minerador: " + recompensas + "\n");

        if (blocoMinerado != null) {
            System.out.println("── Passo 7: Resumo do Bloco ────────────");
            System.out.println(blocoMinerado);
        }

        System.out.println("\n══ Demo finalizado com sucesso ══");
    }
}