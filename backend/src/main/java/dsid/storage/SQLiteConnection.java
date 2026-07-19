package dsid.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteConnection {

    private static String dbName = "blockchain_kanto.db";

    public static void setDbName(String nome) {
        dbName = nome;
    }

    public static String getDbName() {
        return dbName;
    }

    public static Connection conectar() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + dbName);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao conectar com o banco de dados SQLite", e);
        }
    }
}