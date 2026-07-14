// Caminho: src/main/java/dsid/service/MiningService.java
package dsid.service;

import dsid.core.Block;
import dsid.core.Blockchain;
import dsid.core.Transaction;
import dsid.storage.LedgerRepository;

import java.security.PublicKey;
import java.util.List;
import java.util.Random;

/**
 * Serviço de Mineração da rede PokeEach.
 *
 * Responsabilidades:
 *   1. Executar o algoritmo Proof-of-Work (loop do nonce).
 *   2. Sortear um Pokémon aleatório como recompensa ("Coinbase / Spawn").
 *   3. Montar o bloco completo, adicioná-lo à cadeia e persistir no banco.
 *
 * Pacote escolhido: {@code dsid.service} — criado especificamente para esta
 * classe, separando a lógica de negócio de mineração das entidades do domínio
 * (core) e da infraestrutura (storage). Isso mantém responsabilidades bem
 * delimitadas e facilita futuras extensões (ex.: taxa de dificuldade dinâmica).
 */
public class MiningService {

    // ---------------------------------------------------------------
    // Pokédex de recompensas (Spawn Pool)
    // ---------------------------------------------------------------
    /** Pool dos Pokémon que podem ser sorteados como recompensa de mineração. */
    private static final String[] REWARD_POKEMON = {
        "Bulbasaur", "Charmander", "Squirtle", "Pikachu",  "Eevee",
        "Meowth",    "Psyduck",    "Geodude",  "Machop",   "Gastly",
        "Magikarp",  "Ditto",      "Snorlax",  "Lapras",   "Jolteon",
        "Flareon",   "Vaporeon",   "Porygon",  "Mew",      "Dragonite"
    };

    private final Random random = new Random();

    // ---------------------------------------------------------------
    // Dependências
    // ---------------------------------------------------------------
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
     *   2. Sorteia o Pokémon de recompensa para o minerador.
     *   3. Instancia o bloco apontando para o hash do último bloco confirmado.
     *   4. Executa o loop Proof-of-Work ({@link Block#mineBlock}).
     *   5. Adiciona o bloco à cadeia em memória.
     *   6. Limpa a mempool.
     *   7. Persiste o bloco no banco SQLite.
     *
     * @param mineradorPublicKey  Chave pública do nó que executou a mineração
     *                            (receberá o crédito do Pokémon sorteado).
     * @return                    O bloco recém-minerado e persistido.
     */
    public Block minerarBlocoPendente(PublicKey mineradorPublicKey) {
        List<Transaction> txPendentes = blockchain.getPendingTransactions();

        if (txPendentes.isEmpty()) {
            System.out.println("[MINING] Mempool vazia. Nenhum bloco a minerar.");
            return null;
        }

        // --- 1. Sorteia o Pokémon de recompensa ---
        String pokemon = sortearPokemon();
        System.out.println("[MINING] Pokémon de recompensa sorteado: " + pokemon + " 🎉");

        // --- 2. Monta o bloco ---
        int    nextHeight  = blockchain.getNextHeight();
        String prevHash    = blockchain.getUltimoBloco().getHash();
        int    difficulty  = blockchain.getDifficulty();

        Block novoBloco = new Block(nextHeight, prevHash, txPendentes, mineradorPublicKey, pokemon);

        // --- 3. Proof-of-Work ---
        System.out.println("[MINING] Iniciando Proof-of-Work (difficulty=" + difficulty + ")...");
        novoBloco.mineBlock(difficulty);

        // --- 4. Registra na cadeia e limpa a mempool ---
        blockchain.adicionarBlocoMinerado(novoBloco);
        blockchain.limparMempool();

        // --- 5. Persiste no banco de dados ---
        repository.saveBlock(novoBloco);
        System.out.println("[MINING] ✅ Bloco #" + nextHeight + " persistido no banco com hash: " + novoBloco.getHash());

        return novoBloco;
    }

    // ---------------------------------------------------------------
    // Utilitários internos
    // ---------------------------------------------------------------

    /** Sorteia aleatoriamente um Pokémon da pool de recompensas. */
    public String sortearPokemon() {
        return REWARD_POKEMON[random.nextInt(REWARD_POKEMON.length)];
    }
}