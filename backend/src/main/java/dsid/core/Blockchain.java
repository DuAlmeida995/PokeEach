package dsid.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Livro-razao imutavel da rede PokeEach.
 
public class Blockchain {

    private final List<Block>       chain;
    private final List<Transaction> pendingTransactions; // mempool
    private final int               difficulty;

    private final Set<String> txIdsNaMempool     = new HashSet<>();
    private final Set<String> pokemonsNaMempool  = new HashSet<>();
    private final Set<String> txIdsConfirmadas   = new HashSet<>();

    public Blockchain(int difficulty) {
        this.chain               = new ArrayList<>();
        this.pendingTransactions = new ArrayList<>();
        this.difficulty          = difficulty;
        criarBlocoGenesis();
    }

    private void criarBlocoGenesis() {
        System.out.println("[CHAIN] Criando Bloco Gênesis...");
        Block genesis = new Block(0, "0", new ArrayList<>(), null, "");
        genesis.mineBlock(difficulty);
        chain.add(genesis);
        System.out.println("[CHAIN] Bloco Gênesis adicionado: " + genesis.getHash());
    }

    public Block getUltimoBloco() {
        return chain.get(chain.size() - 1);
    }

    public int getNextHeight() {
        return chain.size();
    }

    public List<Block> getChain() {
        return chain;
    }

    public int getDifficulty() {
        return difficulty;
    }

    // Mempool, com dedup e protecao a double-spend
    /**
     * Adiciona uma transacao validada na mempool
     *
     * Verificacoes em ordem:
     *   1. remetente e destinatario nao-nulos
     *   2. assinatura digital valida.
     *   3. TX nao duplicada (mesmo transactionId ja na mempool).
     *   4. TX nao ja confirmada em bloco anterior (anti-replay).
     *   5. Pokemon nao tem outra TX pendente (double-spend basico).
     */
    public void adicionarTransacao(Transaction tx) {
        if (tx.getRemetente() == null || tx.getDestinatario() == null) {
            throw new IllegalArgumentException("[CHAIN] TX rejeitada: remetente e destinatário obrigatórios.");
        }
        if (!tx.verifySignature()) {
            throw new SecurityException("[CHAIN] TX rejeitada: assinatura digital inválida.");
        }
        if (txIdsNaMempool.contains(tx.getTransactionId())) {
            System.out.println("[CHAIN] TX duplicada ignorada: " + tx.getTransactionId());
            return;
        }
        if (txIdsConfirmadas.contains(tx.getTransactionId())) {
            System.out.println("[CHAIN] TX já confirmada em bloco anterior — replay rejeitado: " + tx.getTransactionId());
            return;
        }
        if (pokemonsNaMempool.contains(tx.getIdPokemon())) {
            throw new IllegalStateException("[CHAIN] TX rejeitada: double-spend detectado para o Pokémon '" + tx.getIdPokemon() + "'.");
        }

        pendingTransactions.add(tx);
        txIdsNaMempool.add(tx.getTransactionId());
        pokemonsNaMempool.add(tx.getIdPokemon());
        System.out.println("[CHAIN] TX " + tx.getTransactionId() + " adicionada à mempool.");
    }

    public List<Transaction> getPendingTransactions() {
        return new ArrayList<>(pendingTransactions);
    }

    public void limparMempool(Block blocoConfirmado) {
        for (Transaction tx : blocoConfirmado.getTransactions()) {
            txIdsConfirmadas.add(tx.getTransactionId());
        }
        pendingTransactions.clear();
        txIdsNaMempool.clear();
        pokemonsNaMempool.clear();
        System.out.println("[CHAIN] Mempool limpa. " + txIdsConfirmadas.size() + " TXs confirmadas no histórico.");
    }

    public void limparMempool() {
        pendingTransactions.clear();
        txIdsNaMempool.clear();
        pokemonsNaMempool.clear();
        System.out.println("[CHAIN] Mempool limpa.");
    }

    public void adicionarBlocoMinerado(Block bloco) {
        for (Transaction tx : bloco.getTransactions()) {
            txIdsConfirmadas.add(tx.getTransactionId());
        }
        chain.add(bloco);
        System.out.println("[CHAIN] Bloco #" + bloco.getHeight() + " adicionado à cadeia.");
    }

    // Longest Chain Wins — recebe cadeia de outro no via P2P
    /**
     * Substitui a cadeia local se a cadeia recebida for mais longa e valida, e
     * implementa o criterio "Longest Chain Wins" do protocolo de consenso.
     *
     * @param novaCadeia lista de blocos recebida via CHAIN do vizinho.
     * @return true se a cadeia local foi substituida.
     */
    public boolean substituirCadeiaSeForMaior(List<Block> novaCadeia) {
        if (novaCadeia == null || novaCadeia.isEmpty()) return false;

        if (novaCadeia.size() <= chain.size()) {
            System.out.println("[CHAIN] Cadeia recebida não é maior (height=" + (novaCadeia.size()-1)
                    + " vs local=" + (chain.size()-1) + "). Mantendo cadeia atual.");
            return false;
        }

        if (!validarCadeiaExterna(novaCadeia)) {
            System.out.println("[CHAIN] ❌ Cadeia recebida inválida. Rejeitada.");
            return false;
        }

        chain.clear();
        chain.addAll(novaCadeia);

        txIdsConfirmadas.clear();
        for (Block b : chain) {
            for (Transaction tx : b.getTransactions()) {
                txIdsConfirmadas.add(tx.getTransactionId());
            }
        }

        System.out.println("[CHAIN] ✅ Cadeia substituída! Nova height: " + (chain.size()-1));
        return true;
    }

    public boolean validarCadeiaExterna(List<Block> cadeia) {
        String hashTarget = "0".repeat(difficulty);

        for (int i = 1; i < cadeia.size(); i++) {
            Block atual    = cadeia.get(i);
            Block anterior = cadeia.get(i - 1);

            if (!atual.getHash().equals(atual.calculateHash())) {
                System.out.println("[CHAIN] Hash inválido no bloco externo #" + i);
                return false;
            }
            if (!anterior.getHash().equals(atual.getPreviousHash())) {
                System.out.println("[CHAIN] Encadeamento quebrado no bloco externo #" + i);
                return false;
            }
            if (!atual.getHash().startsWith(hashTarget)) {
                System.out.println("[CHAIN] PoW inválido no bloco externo #" + i);
                return false;
            }
            for (Transaction tx : atual.getTransactions()) {
                if (!tx.verifySignature()) {
                    System.out.println("[CHAIN] Assinatura inválida no bloco externo #" + i);
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isCadeiaValida() {
        return validarCadeiaExterna(chain);
    }

    public boolean txJaConfirmada(String transactionId) {
        return txIdsConfirmadas.contains(transactionId);
    }
}