package storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteConnection {
    
    private static final String URL = "jdbc:sqlite:blockchain_kanto.db";

    public static Connection conectar() {
        try {
            return DriverManager.getConnection(URL);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao conectar com o banco de dados SQLite", e);
        }
    }

    // configura a estrutura inicial do banco de dados
    public static void inicializarBanco() {
        String sqlBlocos = "CREATE TABLE IF NOT EXISTS blocks ("
                + "hash TEXT PRIMARY KEY,"
                + "previous_hash TEXT NOT NULL,"
                + "timestamp INTEGER NOT NULL,"
                + "nonce INTEGER NOT NULL"
                + ");";

        String sqlTransacoes = "CREATE TABLE IF NOT EXISTS transactions ("
                + "transaction_id TEXT PRIMARY KEY,"
                + "block_hash TEXT NOT NULL," 
                + "remetente TEXT,"
                + "destinatario TEXT,"
                + "id_pokemon TEXT NOT NULL,"
                + "timestamp INTEGER NOT NULL,"
                + "assinatura_digital BLOB," // BLOB: tipo ideal para guardar array de bytes
                + "FOREIGN KEY (block_hash) REFERENCES blocks (hash)"
                + ");";

        try (Connection conn = conectar(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlBlocos);
            stmt.execute(sqlTransacoes);
            System.out.println("[STORAGE] Banco de dados embutido inicializado com sucesso.");
        } catch (SQLException e) {
            System.out.println("[STORAGE] Erro ao criar as tabelas: " + e.getMessage());
        }
    }
}