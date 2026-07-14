// Caminho: src/main/java/dsid/network/BlockchainSerializer.java
package dsid.network;

import com.google.gson.*;
import dsid.core.Block;
import dsid.core.Transaction;
import dsid.utils.KeyUtils;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Serialização/deserialização JSON do protocolo P2P.
 *
 * Correção principal: fromDto() agora usa o construtor de reconstrução do Block
 * que preserva timestamp, nonce e hash originais — sem recalcular.
 * Isso corrigia o bug "Hash do bloco inválido" ao receber NEW_BLOCK.
 */
public class BlockchainSerializer {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    // ---------------------------------------------------------------
    // DTOs
    // ---------------------------------------------------------------
    public static class TransactionDto {
        public String transactionId;
        public String remetente;
        public String destinatario;
        public String idPokemon;
        public long   timestamp;
        public String assinatura;
    }

    public static class BlockDto {
        public int    height;
        public String hash;
        public String previousHash;
        public long   timestamp;
        public int    nonce;
        public String minerKey;
        public String rewardPokemon;
        public List<TransactionDto> transactions = new ArrayList<>();
    }

    public static class GetChainPayload {
        public int heightLocal;
        public int portaP2P;
        public GetChainPayload(int h, int p) { this.heightLocal = h; this.portaP2P = p; }
    }

    public static class ChainPayload {
        public List<BlockDto> blocks;
        public ChainPayload(List<BlockDto> b) { this.blocks = b; }
    }

    // ---------------------------------------------------------------
    // Block ↔ BlockDto
    // ---------------------------------------------------------------
    public static BlockDto toDto(Block block) {
        BlockDto dto = new BlockDto();
        dto.height        = block.getHeight();
        dto.hash          = block.getHash();
        dto.previousHash  = block.getPreviousHash();
        dto.timestamp     = block.getTimestamp();
        dto.nonce         = block.getNonce();
        dto.minerKey      = block.getMinerKey();
        dto.rewardPokemon = block.getRewardPokemon();
        for (Transaction tx : block.getTransactions()) dto.transactions.add(toDto(tx));
        return dto;
    }

    public static Block fromDto(BlockDto dto) {
        List<Transaction> txs = new ArrayList<>();
        for (TransactionDto txDto : dto.transactions) {
            Transaction tx = fromDto(txDto);
            if (tx != null) txs.add(tx);
        }

        // Usa o construtor de reconstrução — preserva timestamp, nonce e hash originais.
        // Isso é fundamental: calculateHash() depende do timestamp exato do bloco original.
        return new Block(
            dto.height,
            dto.previousHash,
            txs,
            dto.minerKey,
            dto.rewardPokemon != null ? dto.rewardPokemon : "",
            dto.timestamp,
            dto.nonce,
            dto.hash
        );
    }

    // ---------------------------------------------------------------
    // Transaction ↔ TransactionDto
    // ---------------------------------------------------------------
    public static TransactionDto toDto(Transaction tx) {
        TransactionDto dto = new TransactionDto();
        dto.transactionId = tx.getTransactionId();
        dto.remetente     = KeyUtils.publicKeyToString(tx.getRemetente());
        dto.destinatario  = KeyUtils.publicKeyToString(tx.getDestinatario());
        dto.idPokemon     = tx.getIdPokemon();
        dto.timestamp     = tx.getTimestamp();
        dto.assinatura    = tx.getAssinaturaDigital() != null
                            ? Base64.getEncoder().encodeToString(tx.getAssinaturaDigital()) : null;
        return dto;
    }

    public static Transaction fromDto(TransactionDto dto) {
        try {
            PublicKey remetente    = KeyUtils.stringToPublicKey(dto.remetente);
            PublicKey destinatario = KeyUtils.stringToPublicKey(dto.destinatario);
            // Usa o construtor de reconstrução — preserva timestamp original
            // para que calculateHash() produza o mesmo transactionId e o
            // toString() da TX seja idêntico ao do nó que criou o bloco.
            Transaction tx = new Transaction(remetente, destinatario, dto.idPokemon, dto.timestamp);
            if (dto.assinatura != null)
                tx.setAssinaturaFromDb(Base64.getDecoder().decode(dto.assinatura));
            return tx;
        } catch (Exception e) {
            System.err.println("[SERIALIZER] Erro ao desserializar TX: " + e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Serialização de mensagens
    // ---------------------------------------------------------------
    public static String serializarCadeia(List<Block> chain) {
        List<BlockDto> dtos = new ArrayList<>();
        for (Block b : chain) dtos.add(toDto(b));
        return GSON.toJson(new ChainPayload(dtos));
    }

    public static List<Block> desserializarCadeia(String json) {
        ChainPayload payload = GSON.fromJson(json, ChainPayload.class);
        List<Block> blocks = new ArrayList<>();
        if (payload == null || payload.blocks == null) return blocks;
        for (BlockDto dto : payload.blocks) blocks.add(fromDto(dto));
        return blocks;
    }

    public static String serializarBloco(Block block) {
        return GSON.toJson(toDto(block));
    }

    public static Block desserializarBloco(String json) {
        return fromDto(GSON.fromJson(json, BlockDto.class));
    }

    public static String serializarTransacao(Transaction tx) {
        return GSON.toJson(toDto(tx));
    }

    public static Transaction desserializarTransacao(String json) {
        return fromDto(GSON.fromJson(json, TransactionDto.class));
    }

    public static String serializarGetChain(int heightLocal, int portaLocal) {
        return GSON.toJson(new GetChainPayload(heightLocal, portaLocal));
    }
}