package storage;

import core.Block;
import core.Transaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;

public class LedgerRepository {

    public static void salvarBloco(Block bloco) {
        
        String sqlInsertBloco = "INSERT INTO blocks (hash, previous_hash, timestamp, nonce) VALUES (?, ?, ?, ?)";
        String sqlInsertTx = "INSERT INTO transactions (transaction_id, block_hash, remetente, destinatario, id_pokemon, timestamp, assinatura_digital) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = SQLiteConnection.conectar()) {
            
            // aqui aplicamos o conceito ACID: desligamos o salvamento automatico,
            // pois se o bloco tiver 50 transacoes e o PC acabar a energia na transacao 49,
            // o banco faz rollback, impedindo assim a existencia de blocos corrompidos
            conn.setAutoCommit(false);

            // salva os dados do bloco
            try (PreparedStatement pstmtBloco = conn.prepareStatement(sqlInsertBloco)) {
                pstmtBloco.setString(1, bloco.getHash());
                pstmtBloco.setString(2, bloco.getPreviousHash());
                pstmtBloco.setLong(3, bloco.getTimestamp());
                pstmtBloco.setInt(4, bloco.getNonce());
                pstmtBloco.executeUpdate();
            }

            // salva o conteudo das transacoes
            try (PreparedStatement pstmtTx = conn.prepareStatement(sqlInsertTx)) {
                for (Transaction tx : bloco.getTransactions()) {
                    pstmtTx.setString(1, tx.getTransactionId());
                    pstmtTx.setString(2, bloco.getHash()); // A Chave Estrangeira ligando a TX ao Bloco
                    
                    String remetenteStr = (tx.getRemetente() != null) ? Base64.getEncoder().encodeToString(tx.getRemetente().getEncoded()) : "genesis";
                    String destStr = (tx.getDestinatario() != null) ? Base64.getEncoder().encodeToString(tx.getDestinatario().getEncoded()) : "genesis";
                    
                    pstmtTx.setString(3, remetenteStr);
                    pstmtTx.setString(4, destStr);
                    pstmtTx.setString(5, tx.getIdPokemon());
                    pstmtTx.setLong(6, tx.getTimestamp());
                    pstmtTx.setBytes(7, tx.getAssinaturaDigital());
                    
                    pstmtTx.executeUpdate();
                }
            }

            conn.commit();
            System.out.println("[STORAGE] Bloco " + bloco.getHash().substring(0, 8) + "... salvo fisicamente no disco!");

        } catch (SQLException e) {
            System.out.println("[STORAGE] Erro crítico ao salvar o bloco. A operação foi cancelada. " + e.getMessage());
        }
    }
}