package dsid.app;

import dsid.core.Block;
import dsid.core.Transaction;
import dsid.storage.LedgerRepository;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- Teste de Bloco (DSID) ---");

        try {
            // 1. Setup de Criptografia (Obrigatório para o construtor da Transaction)
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();
            PublicKey pubKey = pair.getPublic();

            // 2. Criar Transações
            List<Transaction> transactions = new ArrayList<>();
            transactions.add(new Transaction(pubKey, pubKey, "025"));

            // 3. Instanciar o Bloco (APENAS 2 PARÂMETROS conforme sua definição)
            // O seu construtor é: public Block(String previousHash, List<Transaction> transactions)
            String prevHash = "0000000000000000";
            Block testBlock = new Block(prevHash, transactions);

            // 4. Testar Persistência
            LedgerRepository repository = new LedgerRepository();
            System.out.println("Salvando bloco gerado automaticamente em: " + new java.util.Date(testBlock.getTimestamp()));
            
            repository.saveBlock(testBlock);

            // 5. Verificação final
            Block retrieved = repository.getLatestBlock();
            if (retrieved != null) {
                System.out.println("✅ Sucesso! Bloco salvo com hash: " + retrieved.getHash());
            }

        } catch (Exception e) {
            System.err.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}