package dsid.core;

import java.util.ArrayList;
import java.util.List;

public class Blockchain {
    
    private List<Block> chain; 
    private List<Transaction> pendingTransactions; // essa vai ser a mempool (transacoes a aguardar mineracao)
    private int difficulty; 

    public Blockchain(int difficulty) {
        this.chain = new ArrayList<>();
        this.pendingTransactions = new ArrayList<>();
        this.difficulty = difficulty;
        
        // assim que a rede ja nasce, temos de criar o "Bloco Gênesis"
        criarBlocoGenesis();
    }

    // O Bloco Gênesis é o primeiro bloco da cadeia, por isso ele nao possui hash anterior
    private void criarBlocoGenesis() {
        System.out.println("A criar o Bloco Gênesis...");
        Block genesis = new Block("0", new ArrayList<>());
        genesis.mineBlock(difficulty);
        chain.add(genesis);
    }

    // retornamos o ultimo bloco fechado na cadeia, oque eh necessario para criar o proximo bloco
    public Block getUltimoBloco() {
        return chain.get(chain.size() - 1);
    }

    // ==========================================
    // FLUXO DE TRANSAÇÕES E MINERAÇÃO
    // ==========================================

    // adiciona uma troca/captura a sala de espera (Mempool)
    public void adicionarTransacao(Transaction transacao) {
        if (transacao.getRemetente() == null || transacao.getDestinatario() == null) {
            throw new RuntimeException("A transação tem de incluir o remetente e o destinatário.");
        }
        
        if (!transacao.verifySignature()) {
            throw new RuntimeException("Tentativa de fraude detetada! Assinatura inválida.");
        }
        
        pendingTransactions.add(transacao);
        System.out.println("Transação adicionada à Mempool com sucesso.");
    }


    public void minerarTransacoesPendentes() {
        if (pendingTransactions.isEmpty()) {
            System.out.println("Não há transações pendentes para minerar.");
            return;
        }

        System.out.println("A iniciar a mineração do bloco...");
        
        // instanciamos o novo bloco apontando para o hash do bloco anterior
        Block novoBloco = new Block(getUltimoBloco().getHash(), new ArrayList<>(pendingTransactions));
        novoBloco.mineBlock(difficulty);
        
        System.out.println("Bloco fechado e adicionado à cadeia!");
        chain.add(novoBloco);
        
        // limpamos a mempool pois as transacoes ja foram gravados no livro razao
        pendingTransactions.clear();
    }

    // ==========================================
    // VALIDAÇÃO DE CONSENSO (A VERDADE DA REDE)
    // ==========================================

    // esse eh o metodo que os outros nos p2p vao chamar quando receberem a sua copia da blockchain
    public boolean isCadeiaValida() {
        Block blocoAtual;
        Block blocoAnterior;
        String hashTarget = new String(new char[difficulty]).replace('\0', '0');
   
        // ignoramos o genese no indice 0 ao percorrer a cadeia
        for (int i = 1; i < chain.size(); i++) {
            blocoAtual = chain.get(i);
            blocoAnterior = chain.get(i - 1);

            // 1. o hash do bloco foi recalculado e eh diferente? (alguem alterou dados no passado)
            if (!blocoAtual.getHash().equals(blocoAtual.calculateHash())) {
                System.out.println("Cadeia corrompida: O Hash do bloco " + i + " não coincide.");
                return false;
            }

            // 2. o encadeamento quebrou? (o bloco atual nao aponta para o anterior corretamente)
            if (!blocoAnterior.getHash().equals(blocoAtual.getPreviousHash())) {
                System.out.println("Cadeia corrompida: Encadeamento quebrado no bloco " + i);
                return false;
            }

            // 3. o Proof of Work foi realmente feito? (alguem tentou injetar um bloco sem minerar)
            if (!blocoAtual.getHash().substring(0, difficulty).equals(hashTarget)) {
                System.out.println("Cadeia corrompida: O bloco " + i + " não resolveu o Proof of Work.");
                return false;
            }

            // 4. verificacao braba e profunda: todas as transacoes dentro deste bloco historico ainda possuem assinaturas validas?
            for (Transaction t : blocoAtual.getTransactions()) {
                if (!t.verifySignature()) {
                    System.out.println("Cadeia corrompida: Transação fraudulenta detetada no bloco " + i);
                    return false;
                }
            }
        }
        
        return true; // se passar em todas as armadilhas, o livro razao eh 100% legitimo
    }

    public List<Block> getChain() {
        return chain;
    }
}