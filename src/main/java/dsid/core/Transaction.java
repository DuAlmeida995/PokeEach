package dsid.core;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Date;
import dsid.crypto.HashUtils;

public class Transaction {
    
    private String transactionId; // O identificador unico, ou seja, o hash da transação
    private PublicKey remetente;  // a "conta" de quem envia o Pokémon
    private PublicKey destinatario; // a "conta" de quem recebe
    private String idPokemon;     // O ativo digital a ser transferido
    private long timestamp;
    private byte[] assinaturaDigital; // vai servir como a prova criptografica de que o remetente autorizou a acao

    public Transaction(PublicKey remetente, PublicKey destinatario, String idPokemon) {
        this.remetente = remetente;
        this.destinatario = destinatario;
        this.idPokemon = idPokemon;
        this.timestamp = new Date().getTime();
        this.transactionId = calculateHash();
    }

    // aqui calculamos o hash da transacao (usado como id unico)
    public String calculateHash() {
        String senderKey = (remetente != null) ? java.util.Base64.getEncoder().encodeToString(remetente.getEncoded()) : "null";
        String recipientKey = (destinatario != null) ? java.util.Base64.getEncoder().encodeToString(destinatario.getEncoded()) : "null";
        String dataToHash = senderKey + recipientKey + idPokemon + Long.toString(timestamp);
        return dsid.crypto.HashUtils.applySha256(dataToHash);
    }

    // gera a assinatura digital utilizando a Chave Privada do remetente
    public void generateSignature(PrivateKey chavePrivada) {
        try {
            // por enquanto vamos uilizar o algoritmo RSA padrao do Java com SHA-256 pra ver se vai dar bom
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(chavePrivada);
            
            // ai so assinamos o texto que representa esta transacao
            rsa.update(this.transactionId.getBytes());
            this.assinaturaDigital = rsa.sign();
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao assinar a transação", e);
        }
    }

    // a rede (os outros nos no caso) vao utilizar este metodo para confirmar se a transacao eh legitima ou num eh 
    public boolean verifySignature() {
        if (this.assinaturaDigital == null) {
            System.out.println("Transação sem assinatura. Rejeitada.");
            return false;
        }
        
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(remetente);
            
            rsa.update(this.transactionId.getBytes());
            return rsa.verify(this.assinaturaDigital);
            
        } catch (Exception e) {
            System.out.println("Erro na verificação da assinatura: " + e.getMessage());
            return false;
        }
    }

    public String getTransactionId() {
        return transactionId;
    }

    public PublicKey getRemetente() {
        return remetente;
    }

    public PublicKey getDestinatario() {
        return destinatario;
    }

    public String getIdPokemon() {
        return idPokemon;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getAssinaturaDigital() {
        return assinaturaDigital;
    }
}