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
        "Bulbasaur", "Ivysaur", "Venusaur", "Charmander", "Charmeleon", "Charizard",
        "Squirtle", "Wartortle", "Blastoise", "Caterpie", "Metapod", "Butterfree",
        "Weedle", "Kakuna", "Beedrill", "Pidgey", "Pidgeotto", "Pidgeot",
        "Rattata", "Raticate", "Spearow", "Fearow", "Ekans", "Arbok",
        "Pikachu", "Raichu", "Sandshrew", "Sandslash", "NidoranF", "Nidorina",
        "Nidoqueen", "NidoranM", "Nidorino", "Nidoking", "Clefairy", "Clefable",
        "Vulpix", "Ninetales", "Jigglypuff", "Wigglytuff", "Zubat", "Golbat",
        "Oddish", "Gloom", "Vileplume", "Paras", "Parasect", "Venonat",
        "Venomoth", "Diglett", "Dugtrio", "Meowth", "Persian", "Psyduck",
        "Golduck", "Mankey", "Primeape", "Growlithe", "Arcanine", "Poliwag",
        "Poliwhirl", "Poliwrath", "Abra", "Kadabra", "Alakazam", "Machop",
        "Machoke", "Machamp", "Bellsprout", "Weepinbell", "Victreebel", "Tentacool",
        "Tentacruel", "Geodude", "Graveler", "Golem", "Ponyta", "Rapidash",
        "Slowpoke", "Slowbro", "Magnemite", "Magneton", "Farfetchd", "Doduo",
        "Dodrio", "Seel", "Dewgong", "Grimer", "Muk", "Shellder",
        "Cloyster", "Gastly", "Haunter", "Gengar", "Onix", "Drowzee",
        "Hypno", "Krabby", "Kingler", "Voltorb", "Electrode", "Exeggcute",
        "Exeggutor", "Cubone", "Marowak", "Hitmonlee", "Hitmonchan", "Lickitung",
        "Koffing", "Weezing", "Rhyhorn", "Rhydon", "Chansey", "Tangela",
        "Kangaskhan", "Horsea", "Seadra", "Goldeen", "Seaking", "Staryu",
        "Starmie", "MrMime", "Scyther", "Jynx", "Electabuzz", "Magmar",
        "Pinsir", "Tauros", "Magikarp", "Gyarados", "Lapras", "Ditto",
        "Eevee", "Vaporeon", "Jolteon", "Flareon", "Porygon", "Omanyte",
        "Omastar", "Kabuto", "Kabutops", "Aerodactyl", "Snorlax", "Articuno",
        "Zapdos", "Moltres", "Dratini", "Dragonair", "Dragonite", "Mewtwo",
        "Mew"
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
        String nomeBase = derivarPokemonDoHash(novoBloco.getHash());

        // Deriva IVs únicos a partir do hash — busca stats base na PokeAPI
        System.out.println("[MINING] Buscando stats base de " + nomeBase + " na PokeAPI...");
        String pokemonComIVs = derivarPokemonComIVsDoHash(novoBloco.getHash(), nomeBase);
        novoBloco.setRewardPokemonFromDb(pokemonComIVs);
        novoBloco.recalcularHashComReward();

        System.out.println("[MINING] Pokémon de recompensa: " + pokemonComIVs);

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
     * Determinístico — qualquer nó obtém o mesmo resultado para o mesmo hash.
     */
    public static String derivarPokemonDoHash(String blockHash) {
        long soma = 0;
        for (char c : blockHash.toCharArray()) soma += c;
        int index = (int)(Math.abs(soma) % REWARD_POKEMON.length);
        return REWARD_POKEMON[index];
    }

    /**
     * Deriva IVs únicos para o Pokémon a partir do hash do bloco.
     *
     * Cada IV vai de 0 até o valor máximo do stat base do Pokémon na PokeAPI,
     * tornando cada exemplar um NFT único dentro do seu potencial real.
     *
     * Os 6 IVs são extraídos de posições diferentes do hash (cada par de chars
     * como hex unsigned), garantindo independência entre os valores.
     *
     * Formato retornado: "NomePokemon|hp:X|atk:Y|def:Z|spa:W|spd:V|spe:U"
     */
    public static String derivarPokemonComIVsDoHash(String blockHash, String nomePokemon) {
        // Busca os stats base do Pokémon via PokeAPI de forma síncrona
        int[] baseStats = buscarBaseStats(nomePokemon);

        // Extrai 6 valores do hash usando pares de chars em posições distribuídas
        int[] ivs = new int[6];
        int hashLen = blockHash.length();
        int[] offsets = {12, 20, 28, 36, 44, 52};
        for (int i = 0; i < 6; i++) {
            int pos = offsets[i] % (hashLen - 1);
            // Lê 2 chars do hash como valor hex (0-255)
            int hexVal = Integer.parseInt(blockHash.substring(pos, pos + 2), 16);
            int maxStat = baseStats[i] > 0 ? baseStats[i] : 100;
            ivs[i] = hexVal % (maxStat + 1); // 0 até maxStat inclusive
        }

        return String.format("%s|hp:%d|atk:%d|def:%d|spa:%d|spd:%d|spe:%d",
                nomePokemon, ivs[0], ivs[1], ivs[2], ivs[3], ivs[4], ivs[5]);
    }

    /**
     * Busca os stats base de um Pokémon via PokeAPI.
     * Ordem retornada: [hp, attack, defense, special-attack, special-defense, speed]
     * Retorna valores padrão (100) em caso de falha de rede.
     */
    private static int[] buscarBaseStats(String nomePokemon) {
        int[] defaults = {100, 100, 100, 100, 100, 100};
        try {
            String nomeApi = nomePokemon.toLowerCase()
                    .replace("nidoranf", "nidoran-f")
                    .replace("nidoranm", "nidoran-m")
                    .replace("mrmime",   "mr-mime")
                    .replace("farfetchd","farfetch'd");

            java.net.URL url = new java.net.URL("https://pokeapi.co/api/v2/pokemon/" + nomeApi);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) return defaults;

            String json = new String(conn.getInputStream().readAllBytes(),
                                     java.nio.charset.StandardCharsets.UTF_8);

            // Estrutura da PokeAPI:
            // {"base_stat":45,"effort":0,"stat":{"name":"hp","url":"..."}}
            // O "base_stat" aparece ANTES do "name" do stat no mesmo objeto.
            // Estratégia: localiza o "name" do stat, depois busca "base_stat"
            // no bloco que começa ANTES desse name (dentro do mesmo objeto {}).
            String[] statNames = {"\"name\":\"hp\"", "\"name\":\"attack\"",
                                  "\"name\":\"defense\"", "\"name\":\"special-attack\"",
                                  "\"name\":\"special-defense\"", "\"name\":\"speed\""};
            int[] result = new int[6];
            for (int i = 0; i < statNames.length; i++) {
                int nameIdx = json.indexOf(statNames[i]);
                if (nameIdx < 0) { result[i] = 100; continue; }

                // Acha o início do objeto que contém este stat (última '{' antes do name)
                int objStart = json.lastIndexOf("{", nameIdx);
                if (objStart < 0) { result[i] = 100; continue; }

                // Dentro desse bloco, acha "base_stat":
                int baseIdx = json.lastIndexOf("\"base_stat\":", nameIdx);
                if (baseIdx < objStart) { result[i] = 100; continue; }

                int numStart = baseIdx + 12;
                // Valor termina em vírgula ou '}'
                int numEnd = numStart;
                while (numEnd < json.length() &&
                       json.charAt(numEnd) != ',' &&
                       json.charAt(numEnd) != '}') numEnd++;

                result[i] = Integer.parseInt(json.substring(numStart, numEnd).trim());
            }

            System.out.println("[MINING] Stats de " + nomePokemon + ": hp=" + result[0]
                    + " atk=" + result[1] + " def=" + result[2]
                    + " spa=" + result[3] + " spd=" + result[4] + " spe=" + result[5]);
            return result;

        } catch (Exception e) {
            System.out.println("[MINING] Falha ao buscar stats de " + nomePokemon + ": " + e.getMessage());
            return defaults;
        }
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

        System.out.println("[MINING] ✅ Bloco de confirmação #" + nextHeight + " persistido.");
        return novoBloco;
    }

}