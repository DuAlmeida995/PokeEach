// Caminho: src/main/java/dsid/core/Blockchain.java
package dsid.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Livro-razão imutável da rede PokeEach.
 *
 * Melhorias nesta versão:
 *   - Deduplicação de TX na mempool (evita a mesma TX entrar duas vezes).
 *   - Validação de double-spend básica: rejeita TX cujo Pokémon já aparece
 *     em outra TX pendente na mempool.
 *   - substituirCadeiaSeForMaior(): implementa o "Longest Chain Wins" para
 *     aceitar cadeias recebidas de outros nós via P2P.
 *   - txJaConfirmada(): verifica se uma TX já foi incluída em algum bloco
 *     anterior, evitando replay attacks.
 */
public class Blockchain {

    private final List<Block>       chain;
    private final List<Transaction> pendingTransactions; // mempool
    private final int               difficulty;

    // IDs de transações já na mempool — para dedup rápido O(1)
    private final Set<String> txIdsNaMempool     = new HashSet<>();
    // Pokémon com TX pendente — para detecção de double-spend
    private final Set<String> pokemonsNaMempool  = new HashSet<>();
    // IDs de transações já confirmadas em blocos — para anti-replay
    private final Set<String> txIdsConfirmadas   = new HashSet<>();

    public Blockchain(int difficulty) {
        this.chain               = new ArrayList<>();
        this.pendingTransactions = new ArrayList<>();
        this.difficulty          = difficulty;
        criarBlocoGenesis();
    }

    // ---------------------------------------------------------------
    // Gênesis
    // ---------------------------------------------------------------
    private void criarBlocoGenesis() {
        System.out.println("[CHAIN] Criando Bloco Gênesis...");
        Block genesis = new Block(0, "0", new ArrayList<>(), null, "");
        genesis.mineBlock(difficulty);
        chain.add(genesis);
        System.out.println("[CHAIN] Bloco Gênesis adicionado: " + genesis.getHash());
    }

    // ---------------------------------------------------------------
    // Acesso à cadeia
    // ---------------------------------------------------------------
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

    // ---------------------------------------------------------------
    // Mempool — com dedup e proteção a double-spend
    // ---------------------------------------------------------------
    /**
     * Adiciona uma transação validada à mempool.
     *
     * Verificações em ordem:
     *   1. Remetente e destinatário não-nulos.
     *   2. Assinatura digital válida.
     *   3. TX não duplicada (mesmo transactionId já na mempool).
     *   4. TX não já confirmada em bloco anterior (anti-replay).
     *   5. Pokémon não tem outra TX pendente (double-spend básico).
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

    /**
     * Limpa a mempool após um bloco ser minerado com sucesso.
     * Registra as TXs do bloco como confirmadas para anti-replay futuro.
     */
    public void limparMempool(Block blocoConfirmado) {
        for (Transaction tx : blocoConfirmado.getTransactions()) {
            txIdsConfirmadas.add(tx.getTransactionId());
        }
        pendingTransactions.clear();
        txIdsNaMempool.clear();
        pokemonsNaMempool.clear();
        System.out.println("[CHAIN] Mempool limpa. " + txIdsConfirmadas.size() + " TXs confirmadas no histórico.");
    }

    /** Sobrecarga sem parâmetro — para compatibilidade com código existente. */
    public void limparMempool() {
        pendingTransactions.clear();
        txIdsNaMempool.clear();
        pokemonsNaMempool.clear();
        System.out.println("[CHAIN] Mempool limpa.");
    }

    // ---------------------------------------------------------------
    // Adição de bloco
    // ---------------------------------------------------------------
    public void adicionarBlocoMinerado(Block bloco) {
        // Registra TXs do bloco como confirmadas
        for (Transaction tx : bloco.getTransactions()) {
            txIdsConfirmadas.add(tx.getTransactionId());
        }
        chain.add(bloco);
        System.out.println("[CHAIN] Bloco #" + bloco.getHeight() + " adicionado à cadeia.");
    }

    // ---------------------------------------------------------------
    // Longest Chain Wins — recebe cadeia de outro nó via P2P
    // ---------------------------------------------------------------
    /**
     * Substitui a cadeia local se a cadeia recebida for mais longa e válida.
     * Implementa o critério "Longest Chain Wins" do protocolo de consenso.
     *
     * @param novaCadeia Lista de blocos recebida via CHAIN do vizinho.
     * @return true se a cadeia local foi substituída.
     */
    public boolean substituirCadeiaSeForMaior(List<Block> novaCadeia) {
        if (novaCadeia == null || novaCadeia.isEmpty()) return false;

        // Só substitui se a nova cadeia for MAIS LONGA
        if (novaCadeia.size() <= chain.size()) {
            System.out.println("[CHAIN] Cadeia recebida não é maior (height=" + (novaCadeia.size()-1)
                    + " vs local=" + (chain.size()-1) + "). Mantendo cadeia atual.");
            return false;
        }

        // Valida a nova cadeia antes de aceitar
        if (!validarCadeiaExterna(novaCadeia)) {
            System.out.println("[CHAIN] ❌ Cadeia recebida inválida. Rejeitada.");
            return false;
        }

        // Substitui
        chain.clear();
        chain.addAll(novaCadeia);

        // Reconstrói o índice de TXs confirmadas a partir da nova cadeia
        txIdsConfirmadas.clear();
        for (Block b : chain) {
            for (Transaction tx : b.getTransactions()) {
                txIdsConfirmadas.add(tx.getTransactionId());
            }
        }

        System.out.println("[CHAIN] ✅ Cadeia substituída! Nova height: " + (chain.size()-1));
        return true;
    }

    /**
     * Valida uma cadeia externa recebida via P2P (mesma lógica do isCadeiaValida,
     * mas aplicada a uma lista arbitrária em vez de this.chain).
     */
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

    // ---------------------------------------------------------------
    // Validação da cadeia local
    // ---------------------------------------------------------------
    public boolean isCadeiaValida() {
        return validarCadeiaExterna(chain);
    }

    // ---------------------------------------------------------------
    // Utilitários
    // ---------------------------------------------------------------
    /** Verifica se uma TX já foi confirmada em algum bloco (anti-replay). */
    public boolean txJaConfirmada(String transactionId) {
        return txIdsConfirmadas.contains(transactionId);
    }
}