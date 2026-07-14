// Caminho: src/main/java/dsid/core/Block.java
package dsid.core;

import dsid.crypto.HashUtils;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class Block {

    private int    height;
    private String hash;
    private String previousHash;
    private long   timestamp;
    private int    nonce;
    private String minerKey;
    private String rewardPokemon;
    private final List<Transaction> transactions;

    // Timestamp fixo do Gênesis — hash idêntico em todos os nós
    public static final long GENESIS_TIMESTAMP = 1700000000000L;

    // ---------------------------------------------------------------
    // Construtor principal — para criação de blocos novos
    // ---------------------------------------------------------------
    public Block(int height, String previousHash, List<Transaction> transactions,
                 PublicKey minerKey, String rewardPokemon) {
        this.height        = height;
        this.previousHash  = previousHash;
        this.transactions  = transactions;
        this.minerKey      = (minerKey != null)
                             ? Base64.getEncoder().encodeToString(minerKey.getEncoded())
                             : "GENESIS";
        this.rewardPokemon = (rewardPokemon != null) ? rewardPokemon : "";
        this.timestamp     = (height == 0) ? GENESIS_TIMESTAMP : System.currentTimeMillis();
        this.nonce         = 0;
        this.hash          = calculateHash();
    }

    /**
     * Construtor de reconstrução — usado pelo BD e pela rede (BlockchainSerializer).
     * Recebe todos os campos já calculados, incluindo timestamp e nonce originais.
     * NÃO recalcula hash no construtor — preserva o hash recebido.
     */
    public Block(int height, String previousHash, List<Transaction> transactions,
                 String minerKey, String rewardPokemon, long timestamp, int nonce, String hash) {
        this.height        = height;
        this.previousHash  = previousHash;
        this.transactions  = transactions;
        this.minerKey      = (minerKey != null) ? minerKey : "GENESIS";
        this.rewardPokemon = (rewardPokemon != null) ? rewardPokemon : "";
        this.timestamp     = timestamp;
        this.nonce         = nonce;
        this.hash          = hash; // hash já calculado — não recalcula
    }

    /** Retrocompatibilidade — 2 parâmetros */
    public Block(String previousHash, List<Transaction> transactions) {
        this(0, previousHash, transactions, (PublicKey) null, "");
    }

    // ---------------------------------------------------------------
    // Hashing
    // ---------------------------------------------------------------
    public String calculateHash() {
        String data = height
                + previousHash
                + Long.toString(timestamp)
                + Integer.toString(nonce)
                + minerKey
                + rewardPokemon
                + transactions.toString();
        return HashUtils.applySha256(data);
    }

    // ---------------------------------------------------------------
    // Proof-of-Work
    // ---------------------------------------------------------------
    public void mineBlock(int difficulty) {
        String target = "0".repeat(difficulty);
        while (!hash.startsWith(target)) {
            nonce++;
            hash = calculateHash();
        }
        System.out.println("[BLOCK] Bloco #" + height + " minerado! Hash: " + hash);
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------
    public int    getHeight()        { return height; }
    public String getHash()          { return hash; }
    public String getPreviousHash()  { return previousHash; }
    public long   getTimestamp()     { return timestamp; }
    public int    getNonce()         { return nonce; }
    public String getMinerKey()      { return minerKey; }
    public String getRewardPokemon() { return rewardPokemon; }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    // ---------------------------------------------------------------
    // Setters para reconstrução do BD (legado — mantidos para compatibilidade)
    // ---------------------------------------------------------------
    public void setHashFromDb(String hash)          { this.hash = hash; }
    public void setTimestampFromDb(long timestamp)  { this.timestamp = timestamp; }
    public void setNonceFromDb(int nonce)           { this.nonce = nonce; }
    public void setHeightFromDb(int height)         { this.height = height; }
    public void setMinerKeyFromDb(String minerKey)  { this.minerKey = minerKey; }
    public void setRewardPokemonFromDb(String name) { this.rewardPokemon = name; }

    @Override
    public String toString() {
        return "Block{"
             + "\n  height="         + height
             + ",\n  hash='"         + hash           + '\''
             + ",\n  previousHash='" + previousHash   + '\''
             + ",\n  timestamp="     + timestamp
             + ",\n  nonce="         + nonce
             + ",\n  minerKey='"     + minerKey.substring(0, Math.min(20, minerKey.length())) + "...'"
             + ",\n  rewardPokemon='"+ rewardPokemon  + '\''
             + ",\n  transactions="  + transactions.size()
             + "\n}";
    }
}