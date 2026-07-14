// Caminho: src/main/java/dsid/core/Transaction.java
package dsid.core;

import dsid.crypto.HashUtils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Date;

public class Transaction {

    private String    transactionId;
    private PublicKey remetente;
    private PublicKey destinatario;
    private String    idPokemon;
    private long      timestamp;
    private byte[]    assinaturaDigital;

    // ---------------------------------------------------------------
    // Construtor principal — para transações novas (gera timestamp agora)
    // ---------------------------------------------------------------
    public Transaction(PublicKey remetente, PublicKey destinatario, String idPokemon) {
        this.remetente    = remetente;
        this.destinatario = destinatario;
        this.idPokemon    = idPokemon;
        this.timestamp    = new Date().getTime();
        this.transactionId = calculateHash();
    }

    /**
     * Construtor de reconstrução — usado pelo BlockchainSerializer e LedgerRepository.
     * Preserva o timestamp original para que calculateHash() produza o mesmo resultado.
     */
    public Transaction(PublicKey remetente, PublicKey destinatario, String idPokemon, long timestamp) {
        this.remetente    = remetente;
        this.destinatario = destinatario;
        this.idPokemon    = idPokemon;
        this.timestamp    = timestamp; // timestamp original preservado
        this.transactionId = calculateHash();
    }

    // ---------------------------------------------------------------
    // Hashing
    // ---------------------------------------------------------------
    public String calculateHash() {
        String senderKey    = encodedKey(remetente);
        String recipientKey = encodedKey(destinatario);
        return HashUtils.applySha256(senderKey + recipientKey + idPokemon + timestamp);
    }

    private String encodedKey(PublicKey key) {
        return (key != null) ? Base64.getEncoder().encodeToString(key.getEncoded()) : "null";
    }

    // ---------------------------------------------------------------
    // Assinatura digital
    // ---------------------------------------------------------------
    public void generateSignature(PrivateKey chavePrivada) {
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(chavePrivada);
            rsa.update(this.transactionId.getBytes("UTF-8"));
            this.assinaturaDigital = rsa.sign();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao assinar a transação: " + e.getMessage(), e);
        }
    }

    public boolean verifySignature() {
        if (assinaturaDigital == null || assinaturaDigital.length == 0) {
            System.out.println("[TX] Transação sem assinatura. Rejeitada.");
            return false;
        }
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(remetente);
            rsa.update(this.transactionId.getBytes("UTF-8"));
            return rsa.verify(assinaturaDigital);
        } catch (Exception e) {
            System.out.println("[TX] Falha na verificação da assinatura: " + e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Setter para assinatura (reconstrução do BD / rede)
    // ---------------------------------------------------------------
    public void setAssinaturaFromDb(byte[] assinatura) {
        this.assinaturaDigital = assinatura;
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------
    public String    getTransactionId()     { return transactionId; }
    public PublicKey getRemetente()         { return remetente; }
    public PublicKey getDestinatario()      { return destinatario; }
    public String    getIdPokemon()         { return idPokemon; }
    public long      getTimestamp()         { return timestamp; }
    public byte[]    getAssinaturaDigital() { return assinaturaDigital; }

    // ---------------------------------------------------------------
    // toString — usado no calculateHash() do Block via transactions.toString()
    // ---------------------------------------------------------------
    @Override
    public String toString() {
        return "TX{"
             + "id='"        + transactionId + '\''
             + ", pokemon='" + idPokemon     + '\''
             + ", from='"    + encodedKey(remetente).substring(0, 12)    + "...'"
             + ", to='"      + encodedKey(destinatario).substring(0, 12) + "...'"
             + ", ts="       + timestamp
             + '}';
    }
}