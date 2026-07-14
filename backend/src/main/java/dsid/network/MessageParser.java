// Caminho: src/main/java/dsid/network/MessageParser.java
package dsid.network;

import dsid.core.Block;
import dsid.core.Blockchain;
import dsid.core.Transaction;
import dsid.storage.LedgerRepository;

import java.util.List;

public class MessageParser {

    private final P2PNode          node;
    private final LedgerRepository repository;

    public MessageParser(P2PNode node, LedgerRepository repository) {
        this.node       = node;
        this.repository = repository;
    }

    public void processarMensagem(String mensagem, String ipRemetente, int portaEfemera) {
        if (mensagem == null || mensagem.trim().isEmpty()) return;

        try {
            String[] partes  = mensagem.split("\\|", 2);
            String   comando = partes[0].trim();
            String   dados   = (partes.length > 1) ? partes[1] : "";

            switch (comando) {
                case "PING"      -> handlePing(dados, ipRemetente);
                case "PONG"      -> handlePong(dados, ipRemetente);
                case "TX"        -> handleTx(dados);
                case "NEW_BLOCK" -> handleNewBlock(dados, ipRemetente);
                case "GET_CHAIN" -> handleGetChain(dados, ipRemetente);
                case "CHAIN"     -> handleChain(dados);
                default          -> System.out.println("[PARSER] Comando desconhecido: " + comando);
            }
        } catch (Exception e) {
            System.out.println("[PARSER] Erro ao processar mensagem: " + e.getMessage());
        }
    }

    private void handlePing(String dados, String ipRemetente) {
        int portaP2P = parsePingPorta(dados);
        System.out.println("[PARSER] PING de " + ipRemetente + ":" + portaP2P + " → respondendo PONG");
        SocketClient.enviarMensagem(ipRemetente, portaP2P, "PONG|" + node.getPortaLocal());
        node.confirmarVizinhoVivo(ipRemetente + ":" + portaP2P);
    }

    private void handlePong(String dados, String ipRemetente) {
        int portaP2P = parsePingPorta(dados);
        String endpoint = ipRemetente + ":" + portaP2P;
        System.out.println("[PARSER] PONG de " + endpoint + " — nó ativo.");
        node.confirmarVizinhoVivo(endpoint);
    }

    private void handleTx(String json) {
        System.out.println("[PARSER] TX recebida. Validando...");
        try {
            Transaction tx = BlockchainSerializer.desserializarTransacao(json);
            if (tx == null) { System.out.println("[PARSER] TX inválida. Ignorada."); return; }

            Blockchain chain = node.getBlockchain();
            if (chain.txJaConfirmada(tx.getTransactionId())) {
                System.out.println("[PARSER] TX já confirmada. Ignorada.");
                return;
            }
            chain.adicionarTransacao(tx);
            System.out.println("[PARSER] ✅ TX " + tx.getTransactionId() + " adicionada à mempool.");
            node.broadcast("TX|" + json);
        } catch (IllegalStateException e) {
            System.out.println("[PARSER] TX rejeitada (double-spend): " + e.getMessage());
        } catch (SecurityException e) {
            System.out.println("[PARSER] TX rejeitada (assinatura inválida): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[PARSER] Erro ao processar TX: " + e.getMessage());
        }
    }

    private void handleNewBlock(String json, String ipRemetente) {
        System.out.println("[PARSER] NEW_BLOCK recebido. Validando...");
        try {
            Block blocoRecebido = BlockchainSerializer.desserializarBloco(json);
            if (blocoRecebido == null) {
                System.out.println("[PARSER] Bloco nulo após desserialização. Ignorado.");
                return;
            }

            Blockchain chain     = node.getBlockchain();
            String hashTarget    = "0".repeat(chain.getDifficulty());
            String hashRecebido  = blocoRecebido.getHash();
            String hashCalculado = blocoRecebido.calculateHash();

            // DEBUG — mostra exatamente o que está divergindo
            if (!hashRecebido.equals(hashCalculado)) {
                System.out.println("[PARSER] ❌ Hash inválido.");
                System.out.println("  Recebido:   " + hashRecebido);
                System.out.println("  Calculado:  " + hashCalculado);
                System.out.println("  height="     + blocoRecebido.getHeight()
                        + " prevHash="  + blocoRecebido.getPreviousHash().substring(0,8)
                        + " ts="        + blocoRecebido.getTimestamp()
                        + " nonce="     + blocoRecebido.getNonce()
                        + " minerKey="  + blocoRecebido.getMinerKey().substring(0,8)
                        + " reward="    + blocoRecebido.getRewardPokemon()
                        + " txs="       + blocoRecebido.getTransactions().size());
                // Mostra toString() de cada TX para identificar divergência
                for (Transaction tx : blocoRecebido.getTransactions()) {
                    System.out.println("  TX.toString: " + tx.toString());
                }
                return;
            }

            if (!hashRecebido.startsWith(hashTarget)) {
                System.out.println("[PARSER] ❌ PoW insuficiente. Rejeitado.");
                return;
            }

            Block ultimoLocal = chain.getUltimoBloco();

            if (blocoRecebido.getPreviousHash().equals(ultimoLocal.getHash())
                    && blocoRecebido.getHeight() == chain.getNextHeight()) {
                chain.adicionarBlocoMinerado(blocoRecebido);
                repository.saveBlock(blocoRecebido);
                chain.limparMempool(blocoRecebido);
                System.out.println("[PARSER] ✅ Bloco #" + blocoRecebido.getHeight() + " aceito.");
                node.broadcast("NEW_BLOCK|" + json);
            } else if (blocoRecebido.getHeight() > ultimoLocal.getHeight()) {
                System.out.println("[PARSER] Bloco à frente da cadeia local. Solicitando sync...");
                node.solicitarSyncDe(ipRemetente);
            } else {
                System.out.println("[PARSER] Bloco já superado. Ignorado.");
            }
        } catch (Exception e) {
            System.out.println("[PARSER] Erro ao processar NEW_BLOCK: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleGetChain(String json, String ipSolicitante) {
        try {
            GetChainRequest req = parseGetChain(json);
            List<Block> cadeia  = node.getBlockchain().getChain();

            System.out.println("[PARSER] GET_CHAIN de " + ipSolicitante + ":" + req.portaP2P
                    + " (height deles: " + req.heightLocal + ", nosso: " + (cadeia.size()-1) + ")");

            if (cadeia.size() - 1 <= req.heightLocal) {
                System.out.println("[PARSER] Nossa cadeia não é maior. Nada a enviar.");
                return;
            }

            String payload = BlockchainSerializer.serializarCadeia(cadeia);
            SocketClient.enviarMensagem(ipSolicitante, req.portaP2P, "CHAIN|" + payload);
            System.out.println("[PARSER] CHAIN enviada para " + ipSolicitante + ":" + req.portaP2P);
        } catch (Exception e) {
            System.out.println("[PARSER] Erro ao processar GET_CHAIN: " + e.getMessage());
        }
    }

    private void handleChain(String json) {
        System.out.println("[PARSER] CHAIN recebida. Aplicando Longest Chain Wins...");
        try {
            List<Block> novaCadeia = BlockchainSerializer.desserializarCadeia(json);
            boolean substituiu = node.getBlockchain().substituirCadeiaSeForMaior(novaCadeia);
            if (substituiu) {
                for (Block b : novaCadeia) repository.saveBlock(b);
                System.out.println("[PARSER] ✅ Cadeia local atualizada com " + novaCadeia.size() + " blocos.");
            }
        } catch (Exception e) {
            System.out.println("[PARSER] Erro ao processar CHAIN: " + e.getMessage());
        }
    }

    private int parsePingPorta(String dados) {
        try { return Integer.parseInt(dados.trim()); }
        catch (Exception e) { return 0; }
    }

    private static class GetChainRequest {
        int heightLocal;
        int portaP2P;
    }

    private GetChainRequest parseGetChain(String json) {
        GetChainRequest req = new GetChainRequest();
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            req.heightLocal = obj.has("heightLocal") ? obj.get("heightLocal").getAsInt() : 0;
            req.portaP2P    = obj.has("portaP2P")    ? obj.get("portaP2P").getAsInt()    : 0;
        } catch (Exception e) {
            req.heightLocal = 0;
            req.portaP2P    = 0;
        }
        return req;
    }
}