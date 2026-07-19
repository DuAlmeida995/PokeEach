// Caminho: src/main/java/dsid/service/MiningService.java
package dsid.service;

import dsid.core.Block;
import dsid.core.Blockchain;
import dsid.core.Transaction;
import dsid.storage.LedgerRepository;

import java.security.PublicKey;
import java.util.List;

/**
 * Serviço de Mineração da rede PokeEach.
 *
 * Correção principal: o Pokémon de recompensa agora é determinístico —
 * derivado do hash do bloco minerado em vez de um Random local.
 * Isso garante que qualquer nó da rede que receba o mesmo bloco
 * calcule o mesmo Pokémon de recompensa, mantendo consistência global.
 */
public class MiningService {

    // Pool de Pokémon disponíveis como recompensa de mineração.
    // A ordem importa — é indexada deterministicamente pelo hash do bloco.
    private static final String[] REWARD_POKEMON = {
        "Bulbasaur", "Charmander", "Squirtle",  "Pikachu",   "Eevee",
        "Meowth",    "Psyduck",    "Geodude",   "Machop",    "Gastly",
        "Magikarp",  "Ditto",      "Snorlax",   "Lapras",    "Jolteon",
        "Flareon",   "Vaporeon",   "Porygon",   "Mew",       "Dragonite"
    };

    private final Blockchain       blockchain;
    private final LedgerRepository repository;

    public MiningService(Blockchain blockchain, LedgerRepository repository) {
        this.blockchain = blockchain;
        this.repository = repository;
    }

    // ---------------------------------------------------------------
    // API pública
    // ---------------------------------------------------------------

    /**
     * Minera as transações pendentes da mempool e fecha um novo bloco.
     *
     * Fluxo:
     *   1. Verifica se há transações pendentes.
     *   2. Monta o bloco com rewardPokemon vazio (ainda não sabemos o hash).
     *   3. Executa o loop Proof-of-Work.
     *   4. Deriva o Pokémon de recompensa a partir do hash encontrado.
     *   5. Recalcula o hash incluindo o rewardPokemon (torna-o parte do bloco).
     *   6. Persiste e propaga.
     */
    public Block minerarBlocoPendente(PublicKey mineradorPublicKey) {
        List<Transaction> txPendentes = blockchain.getPendingTransactions();

        if (txPendentes.isEmpty()) {
            System.out.println("[MINING] Mempool vazia. Nenhum bloco a minerar.");
            return null;
        }

        int    nextHeight = blockchain.getNextHeight();
        String prevHash   = blockchain.getUltimoBloco().getHash();
        int    difficulty = blockchain.getDifficulty();

        // Monta o bloco sem rewardPokemon ainda
        Block novoBloco = new Block(nextHeight, prevHash, txPendentes, mineradorPublicKey, "");

        // Executa o Proof-of-Work
        System.out.println("[MINING] Iniciando Proof-of-Work (difficulty=" + difficulty + ")...");
        novoBloco.mineBlock(difficulty);

        // Deriva o Pokémon deterministicamente do hash final
        String pokemon = derivarPokemonDoHash(novoBloco.getHash());
        novoBloco.setRewardPokemonFromDb(pokemon);
        // Recalcula o hash incluindo o rewardPokemon
        novoBloco.recalcularHashComReward();

        System.out.println("[MINING] Pokémon de recompensa: " + pokemon
                + " (derivado do hash — consistente em toda a rede)");

        // Registra na cadeia e limpa mempool
        blockchain.adicionarBlocoMinerado(novoBloco);
        blockchain.limparMempool(novoBloco);

        // Persiste
        repository.saveBlock(novoBloco);
        System.out.println("[MINING] ✅ Bloco #" + nextHeight
                + " persistido. Hash: " + novoBloco.getHash());

        return novoBloco;
    }

    // ---------------------------------------------------------------
    // Spawn determinístico
    // ---------------------------------------------------------------

    /**
     * Deriva o Pokémon de recompensa a partir do hash do bloco.
     *
     * Qualquer nó que receba o mesmo bloco vai chamar este método
     * e obter o mesmo resultado — sem estado local, sem Random.
     *
     * Algoritmo: soma os bytes do hash (interpretados como unsigned)
     * e usa módulo pelo tamanho do pool.
     */
    public static String derivarPokemonDoHash(String blockHash) {
        long soma = 0;
        for (char c : blockHash.toCharArray()) {
            soma += c;
        }
        int index = (int)(Math.abs(soma) % REWARD_POKEMON.length);
        return REWARD_POKEMON[index];
    }

    /**
     * Minera um bloco apenas para confirmar TXs pendentes, sem gerar recompensa de Pokémon.
     * O rewardPokemon é marcado como "__NONE__" e filtrado pelo LedgerRepository.
     */
    public Block minerarBlocoConfirmacao(PublicKey mineradorPublicKey) {
        List<Transaction> txPendentes = blockchain.getPendingTransactions();

        if (txPendentes.isEmpty()) {
            System.out.println("[MINING] Mempool vazia. Nenhum bloco a minerar.");
            return null;
        }

        int    nextHeight = blockchain.getNextHeight();
        String prevHash   = blockchain.getUltimoBloco().getHash();
        int    difficulty = blockchain.getDifficulty();

        // Bloco com rewardPokemon marcado como __NONE__ — não gera spawn
        Block novoBloco = new Block(nextHeight, prevHash, txPendentes, mineradorPublicKey, "__NONE__");

        System.out.println("[MINING] Minerando bloco de confirmação (sem recompensa)...");
        novoBloco.mineBlock(difficulty);

        // Mantém __NONE__ como reward — não deriva Pokémon do hash
        novoBloco.recalcularHashComReward();

        blockchain.adicionarBlocoMinerado(novoBloco);
        blockchain.limparMempool(novoBloco);
        repository.saveBlock(novoBloco);

        System.out.println("[MINING] Bloco de confirmação #" + nextHeight + " persistido.");
        return novoBloco;
    }

}