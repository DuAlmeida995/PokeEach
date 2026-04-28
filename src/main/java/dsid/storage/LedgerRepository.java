package dsid.storage;

import dsid.core.Block;
import dsid.core.Transaction;

import java.security.PublicKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import dsid.utils.KeyUtils;


public class LedgerRepository {

    public LedgerRepository() {
        createTables();
    }

    public void createTables() {
    // Script para criar as tabelas de Blocos e Transações
    String sqlBlocks = "CREATE TABLE IF NOT EXISTS blocks ("
                     + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                     + " hash TEXT NOT NULL,"
                     + " previous_hash TEXT,"
                     + " timestamp LONG NOT NULL,"
                     + " nonce INTEGER NOT NULL"
                     + ");";

    String sqlTransactions = "CREATE TABLE IF NOT EXISTS transactions ("
                           + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                           + " block_hash TEXT NOT NULL,"
                           + " remetente TEXT,"
                           + " destinatario TEXT,"
                           + " id_pokemon TEXT,"
                           + " FOREIGN KEY (block_hash) REFERENCES blocks(hash)"
                           + ");";

    try (Connection conn = SQLiteConnection.conectar();
         Statement stmt = conn.createStatement()) {
        
        stmt.execute(sqlBlocks);
        stmt.execute(sqlTransactions);
        System.out.println("[STORAGE] Tabelas verificadas/criadas com sucesso.");
        
    } catch (SQLException e) {
        System.err.println("[STORAGE] Erro ao criar tabelas: " + e.getMessage());
    }
}

    public Block getLatestBlock() {
    // A query ordena pelo ID/Timestamp decrescente e pega apenas o primeiro (o mais recente)
    String sql = "SELECT * FROM blocks ORDER BY id DESC LIMIT 1";
    
    try (Connection conn = SQLiteConnection.conectar();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {

        if (rs.next()) {
            // Aqui você reconstrói o objeto Block com os dados do banco
            // IMPORTANTE: Ajuste os nomes das colunas conforme sua tabela
            String prevHash = rs.getString("previous_hash");
            String hash = rs.getString("hash");
            long timestamp = rs.getLong("timestamp");
            
            // Para as transações, como elas são uma lista, você precisará
            // de uma lógica para recuperá-las (ou converter de JSON se salvou assim)
            List<Transaction> transactions = getTransactionsForBlock(hash);

            Block block = new Block(prevHash, transactions);
            // Se você tiver setters para hash e timestamp, use-os para manter a fidelidade ao banco
            // block.setHash(hash); 
            
            return block;
        }
    } catch (SQLException e) {
        System.err.println("[STORAGE] Erro ao buscar último bloco: " + e.getMessage());
    }
    
    return null; // Retorna null se a blockchain estiver vazia (caso do Bloco Gênese)
}

    private List<Transaction> getTransactionsForBlock(String blockHash) {
    List<Transaction> transactions = new ArrayList<>();
    String sql = "SELECT * FROM transactions WHERE block_hash = ?";

    try (Connection conn = SQLiteConnection.conectar();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        pstmt.setString(1, blockHash);
        ResultSet rs = pstmt.executeQuery();

        while (rs.next()) {
            // Aqui você reconstrói a transação. 
            // Como você usa PublicKey, precisará converter a String do banco de volta para PublicKey
            PublicKey remetente = KeyUtils.stringToPublicKey(rs.getString("remetente"));
            PublicKey destinatario = KeyUtils.stringToPublicKey(rs.getString("destinatario"));
            String idPokemon = rs.getString("id_pokemon");

            transactions.add(new Transaction(remetente, destinatario, idPokemon));
        }
    } catch (Exception e) {
        System.err.println("[STORAGE] Erro ao recuperar transações: " + e.getMessage());
    }
    return transactions;
}

public void saveBlock(Block block) {
    String sqlBlock = "INSERT INTO blocks(hash, previous_hash, timestamp, nonce) VALUES(?,?,?, ?)";
    String sqlTrans = "INSERT INTO transactions(block_hash, remetente, destinatario, id_pokemon) VALUES(?,?,?,?)";

    try (Connection conn = SQLiteConnection.conectar()) {
        conn.setAutoCommit(false); // Garante que ou salva tudo ou nada (Atomicidade)

        // 1. Salva o Bloco
        try (PreparedStatement pstmtBlock = conn.prepareStatement(sqlBlock)) {
            pstmtBlock.setString(1, block.getHash());
            pstmtBlock.setString(2, block.getPreviousHash());
            pstmtBlock.setLong(3, block.getTimestamp());
            pstmtBlock.setInt(4, block.getNonce());
            pstmtBlock.executeUpdate();
        }

        // 2. Salva as Transações do Bloco
        try (PreparedStatement pstmtTrans = conn.prepareStatement(sqlTrans)) {
            for (Transaction t : block.getTransactions()) {
                pstmtTrans.setString(1, block.getHash());
                // Converte PublicKey para String para salvar no banco
                pstmtTrans.setString(2, KeyUtils.publicKeyToString(t.getRemetente()));
                pstmtTrans.setString(3, KeyUtils.publicKeyToString(t.getDestinatario()));
                pstmtTrans.setString(4, t.getIdPokemon());
                pstmtTrans.executeUpdate();
            }
        }

        conn.commit(); // Finaliza a transação no banco
    } catch (SQLException e) {
        System.err.println("[STORAGE] Erro ao salvar bloco completo: " + e.getMessage());
    }
}

}