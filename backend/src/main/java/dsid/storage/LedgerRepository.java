// Caminho: src/main/java/dsid/storage/LedgerRepository.java
package dsid.storage;

import dsid.core.Block;
import dsid.core.Transaction;
import dsid.utils.KeyUtils;

import java.security.PublicKey;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

public class LedgerRepository {

    public LedgerRepository() {
        createTables();
    }

    public void createTables() {
        String sqlBlocks =
            "CREATE TABLE IF NOT EXISTS blocks ("
          + "  id             INTEGER PRIMARY KEY AUTOINCREMENT,"
          + "  height         INTEGER NOT NULL DEFAULT 0,"
          + "  hash           TEXT    NOT NULL UNIQUE,"
          + "  previous_hash  TEXT    NOT NULL,"
          + "  timestamp      INTEGER NOT NULL,"
          + "  nonce          INTEGER NOT NULL,"
          + "  miner_key      TEXT,"
          + "  reward_pokemon TEXT"
          + ");";

        String sqlTransactions =
            "CREATE TABLE IF NOT EXISTS transactions ("
          + "  id                 INTEGER PRIMARY KEY AUTOINCREMENT,"
          + "  transaction_id     TEXT    NOT NULL UNIQUE,"
          + "  block_hash         TEXT    NOT NULL,"
          + "  remetente          TEXT,"
          + "  destinatario       TEXT,"
          + "  id_pokemon         TEXT    NOT NULL,"
          + "  timestamp          INTEGER NOT NULL DEFAULT 0,"
          + "  assinatura_digital BLOB,"
          + "  FOREIGN KEY (block_hash) REFERENCES blocks(hash)"
          + ");";

        try (Connection conn = SQLiteConnection.conectar();
             Statement stmt  = conn.createStatement()) {
            stmt.execute(sqlBlocks);
            stmt.execute(sqlTransactions);
            System.out.println("[STORAGE] Tabelas verificadas / criadas com sucesso.");
        } catch (SQLException e) {
            System.err.println("[STORAGE] Erro ao criar tabelas: " + e.getMessage());
        }

        runMigrations();
    }

    /**
     * Adiciona colunas novas em bancos criados por versões anteriores do código.
     * Se a coluna já existir o erro "duplicate column" é silenciado — operação segura e idempotente.
     */
    private void runMigrations() {
        String[][] migrations = {
            { "blocks",       "height",             "INTEGER NOT NULL DEFAULT 0" },
            { "blocks",       "miner_key",           "TEXT" },
            { "blocks",       "reward_pokemon",      "TEXT" },
            { "transactions", "transaction_id",      "TEXT" },
            { "transactions", "timestamp",           "INTEGER NOT NULL DEFAULT 0" },
            { "transactions", "assinatura_digital",  "BLOB" },
        };

        try (Connection conn = SQLiteConnection.conectar()) {
            for (String[] m : migrations) {
                try (Statement s = conn.createStatement()) {
                    s.execute("ALTER TABLE " + m[0] + " ADD COLUMN " + m[1] + " " + m[2]);
                } catch (SQLException e) {
                    if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                        System.err.println("[STORAGE] Migração (" + m[0] + "." + m[1] + "): " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[STORAGE] Falha ao conectar para migrações: " + e.getMessage());
        }
    }

    public void saveBlock(Block block) {
        String sqlBlock =
            "INSERT OR IGNORE INTO blocks"
          + "(height, hash, previous_hash, timestamp, nonce, miner_key, reward_pokemon)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?)";

        String sqlTx =
            "INSERT OR IGNORE INTO transactions"
          + "(transaction_id, block_hash, remetente, destinatario, id_pokemon, timestamp, assinatura_digital)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = SQLiteConnection.conectar()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psBlock = conn.prepareStatement(sqlBlock)) {
                psBlock.setInt(   1, block.getHeight());
                psBlock.setString(2, block.getHash());
                psBlock.setString(3, block.getPreviousHash());
                psBlock.setLong(  4, block.getTimestamp());
                psBlock.setInt(   5, block.getNonce());
                psBlock.setString(6, block.getMinerKey());
                psBlock.setString(7, block.getRewardPokemon());
                psBlock.executeUpdate();
            }

            try (PreparedStatement psTx = conn.prepareStatement(sqlTx)) {
                for (Transaction tx : block.getTransactions()) {
                    psTx.setString(1, tx.getTransactionId());
                    psTx.setString(2, block.getHash());
                    psTx.setString(3, KeyUtils.publicKeyToString(tx.getRemetente()));
                    psTx.setString(4, KeyUtils.publicKeyToString(tx.getDestinatario()));
                    psTx.setString(5, tx.getIdPokemon());
                    psTx.setLong(  6, tx.getTimestamp());
                    psTx.setBytes( 7, tx.getAssinaturaDigital());
                    psTx.executeUpdate();
                }
            }

            conn.commit();
            System.out.println("[STORAGE] Bloco #" + block.getHeight() + " salvo com sucesso.");

        } catch (SQLException e) {
            System.err.println("[STORAGE] Erro ao salvar bloco. Rollback efetuado: " + e.getMessage());
        }
    }

    public Block getLatestBlock() {
        String sql = "SELECT * FROM blocks ORDER BY height DESC LIMIT 1";
        try (Connection conn = SQLiteConnection.conectar();
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)) {

            if (rs.next()) return blockFromResultSet(rs);
        } catch (Exception e) {
            System.err.println("[STORAGE] Erro ao buscar último bloco: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retorna o inventário completo do treinador considerando o estado atual de cada Pokémon.
     *
     * Lógica unificada:
     *   1. Pokémon de mineração: aparece se não foi transferido depois (sem TX posterior saindo dele)
     *   2. Pokémon de troca: aparece se a última TX do Pokémon tem o treinador como destinatário
     *
     * Ignora Pokémon internos (__MINE__, CAPTURA_*).
     */
    public List<String> getInventarioDoTreinador(PublicKey chavePublica) {
        String chaveStr = KeyUtils.publicKeyToString(chavePublica);
        Set<String> inventario = new LinkedHashSet<>();

        // 1. Pokémon recebidos por TX onde esta chave é o destinatário mais recente
        String sqlTx =
            "SELECT t.id_pokemon "
          + "FROM transactions t "
          + "INNER JOIN ("
          + "  SELECT id_pokemon, MAX(timestamp) AS max_ts "
          + "  FROM transactions GROUP BY id_pokemon"
          + ") latest ON t.id_pokemon = latest.id_pokemon AND t.timestamp = latest.max_ts "
          + "WHERE t.destinatario = ? "
          + "  AND t.id_pokemon NOT LIKE 'CAPTURA\\_%' ESCAPE '\\' "
          + "  AND t.id_pokemon != '__MINE__'";

        try (Connection conn = SQLiteConnection.conectar();
             PreparedStatement ps = conn.prepareStatement(sqlTx)) {
            ps.setString(1, chaveStr);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) inventario.add(rs.getString("id_pokemon"));
        } catch (Exception e) {
            System.err.println("[STORAGE] Erro ao buscar inventário TX: " + e.getMessage());
        }

        // 2. Pokémon de mineração que NÃO foram transferidos depois
        String sqlMine =
            "SELECT b.reward_pokemon FROM blocks b "
          + "WHERE b.miner_key = ? "
          + "  AND b.reward_pokemon IS NOT NULL "
          + "  AND b.reward_pokemon != '' "
          + "  AND b.reward_pokemon NOT LIKE 'CAPTURA\\_%' ESCAPE '\\' "
          + "  AND b.reward_pokemon != '__MINE__' "
          + "  AND b.reward_pokemon != '__NONE__' "
          // Exclui se já existe TX onde este treinador enviou esse Pokémon para outra pessoa
          + "  AND NOT EXISTS ("
          + "    SELECT 1 FROM transactions t2 "
          + "    WHERE t2.id_pokemon = b.reward_pokemon "
          + "      AND t2.remetente = ? "
          + "  )";

        try (Connection conn = SQLiteConnection.conectar();
             PreparedStatement ps = conn.prepareStatement(sqlMine)) {
            ps.setString(1, chaveStr);
            ps.setString(2, chaveStr);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) inventario.add(rs.getString("reward_pokemon"));
        } catch (Exception e) {
            System.err.println("[STORAGE] Erro ao buscar recompensas de mineração: " + e.getMessage());
        }

        return new ArrayList<>(inventario);
    }

    /** Mantido para compatibilidade com RestServer — delega para getInventarioDoTreinador */
    public List<String> getPokemonsRecompensaDoMinerador(PublicKey chavePublica) {
        // Recompensas são agora tratadas dentro do inventário unificado
        // Retorna lista vazia para evitar duplicação no RestServer
        return new ArrayList<>();
    }

    private Block blockFromResultSet(ResultSet rs) throws Exception {
        String prevHash      = rs.getString("previous_hash");
        String hash          = rs.getString("hash");
        long   timestamp     = rs.getLong("timestamp");
        int    nonce         = rs.getInt("nonce");
        int    height        = rs.getInt("height");
        String minerKey      = rs.getString("miner_key");
        String rewardPokemon = rs.getString("reward_pokemon");

        List<Transaction> txs = getTransactionsForBlock(hash);

        Block block = new Block(height, prevHash, txs, null, rewardPokemon);
        block.setHashFromDb(hash);
        block.setTimestampFromDb(timestamp);
        block.setNonceFromDb(nonce);
        block.setHeightFromDb(height);
        block.setMinerKeyFromDb(minerKey != null ? minerKey : "GENESIS");
        block.setRewardPokemonFromDb(rewardPokemon != null ? rewardPokemon : "");
        return block;
    }

    private List<Transaction> getTransactionsForBlock(String blockHash) {
        List<Transaction> txs = new ArrayList<>();
        try (Connection conn = SQLiteConnection.conectar();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM transactions WHERE block_hash = ?")) {

            ps.setString(1, blockHash);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                PublicKey remetente    = KeyUtils.stringToPublicKey(rs.getString("remetente"));
                PublicKey destinatario = KeyUtils.stringToPublicKey(rs.getString("destinatario"));
                long tsOriginal = rs.getLong("timestamp");
                Transaction tx = new Transaction(remetente, destinatario, rs.getString("id_pokemon"), tsOriginal);
                byte[] assinatura = rs.getBytes("assinatura_digital");
                if (assinatura != null) tx.setAssinaturaFromDb(assinatura);
                txs.add(tx);
            }
        } catch (Exception e) {
            System.err.println("[STORAGE] Erro ao recuperar transações do bloco " + blockHash + ": " + e.getMessage());
        }
        return txs;
    }
}