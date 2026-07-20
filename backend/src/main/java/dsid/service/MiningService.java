package dsid.service;

import dsid.core.Block;
import dsid.core.Blockchain;
import dsid.core.Transaction;
import dsid.storage.LedgerRepository;

import java.security.PublicKey;
import java.util.List;


public class MiningService {

    // pool de Pokemons disponiveis como recompensa de mineracao.
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

    /**
     * minera as transacoes pendentes da mempool e fecha um novo bloco.
     *
     * segue o seguinte fluxo:
     *   1. verifica se ha transacoes pendentes
     *   2. monta o bloco com rewardPokemon vazio (ainda nao sabemos o hash)
     *   3. executa o loop Proof-of-Work
     *   4. deriva o Pokemon de recompensa a partir do hash encontrado
     *   5. recalcula o hash incluindo o rewardPokemon, e torna-o parte do bloco
     *   6. persiste e propaga.
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
        Block novoBloco = new Block(nextHeight, prevHash, txPendentes, mineradorPublicKey, "");
        System.out.println("[MINING] Iniciando Proof-of-Work (difficulty=" + difficulty + ")...");
        novoBloco.mineBlock(difficulty);
        String nomeBase = derivarPokemonDoHash(novoBloco.getHash());
        System.out.println("[MINING] Buscando stats base de " + nomeBase + " na PokeAPI...");
        String pokemonComIVs = derivarPokemonComIVsDoHash(novoBloco.getHash(), nomeBase);
        novoBloco.setRewardPokemonFromDb(pokemonComIVs);
        novoBloco.recalcularHashComReward();

        System.out.println("[MINING] Pokémon de recompensa: " + pokemonComIVs);

        blockchain.adicionarBlocoMinerado(novoBloco);
        blockchain.limparMempool(novoBloco);
        repository.saveBlock(novoBloco);
        System.out.println("[MINING] ✅ Bloco #" + nextHeight
                + " persistido. Hash: " + novoBloco.getHash());

        return novoBloco;
    }

    /**
     * deriva o Pokemon de recompensa a partir do hash do bloco, alem de ser
     * deterministico, qualquer no obtem o mesmo resultado para o mesmo hash.
     */
    public static String derivarPokemonDoHash(String blockHash) {
        long soma = 0;
        for (char c : blockHash.toCharArray()) soma += c;
        int index = (int)(Math.abs(soma) % REWARD_POKEMON.length);
        return REWARD_POKEMON[index];
    }

    /**
     * deriva IVs unicos para o Pokemon a partir do hash do bloco
     *
     * cada IV vai de 0 até o valor max do stat base do Pokemon na PokeAPI,
     * tornando cada exemplar um NFT unico
     *
     * os 6 IVs sao extraidos de posicoes diferentes do hash (cada par de chars
     * como hex unsigned), garantindo independencia entre os valores
     */
    public static String derivarPokemonComIVsDoHash(String blockHash, String nomePokemon) {
        int[] baseStats = buscarBaseStats(nomePokemon);
        int[] ivs = new int[6];
        int hashLen = blockHash.length();
        int[] offsets = {12, 20, 28, 36, 44, 52};
        for (int i = 0; i < 6; i++) {
            int pos = offsets[i] % (hashLen - 1);
            int hexVal = Integer.parseInt(blockHash.substring(pos, pos + 2), 16);
            int maxStat = baseStats[i] > 0 ? baseStats[i] : 100;
            ivs[i] = hexVal % (maxStat + 1); 
        }

        return String.format("%s|hp:%d|atk:%d|def:%d|spa:%d|spd:%d|spe:%d",
                nomePokemon, ivs[0], ivs[1], ivs[2], ivs[3], ivs[4], ivs[5]);
    }

    /**
     * busca os stats base de um Pokemon via PokeAPI
     * ordem retornada: [hp, attack, defense, special-attack, special-defense, speed]
     * retorna valores padrao (100) em caso de falha de rede
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
            String[] statNames = {"\"name\":\"hp\"", "\"name\":\"attack\"",
                                  "\"name\":\"defense\"", "\"name\":\"special-attack\"",
                                  "\"name\":\"special-defense\"", "\"name\":\"speed\""};
            int[] result = new int[6];
            for (int i = 0; i < statNames.length; i++) {
                int nameIdx = json.indexOf(statNames[i]);
                if (nameIdx < 0) { result[i] = 100; continue; }
                int objStart = json.lastIndexOf("{", nameIdx);
                if (objStart < 0) { result[i] = 100; continue; }
                int baseIdx = json.lastIndexOf("\"base_stat\":", nameIdx);
                if (baseIdx < objStart) { result[i] = 100; continue; }
                int numStart = baseIdx + 12;
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

    public Block minerarBlocoConfirmacao(PublicKey mineradorPublicKey) {
        List<Transaction> txPendentes = blockchain.getPendingTransactions();

        if (txPendentes.isEmpty()) {
            System.out.println("[MINING] Mempool vazia. Nenhum bloco a minerar.");
            return null;
        }

        int    nextHeight = blockchain.getNextHeight();
        String prevHash   = blockchain.getUltimoBloco().getHash();
        int    difficulty = blockchain.getDifficulty();
        Block novoBloco = new Block(nextHeight, prevHash, txPendentes, mineradorPublicKey, "__NONE__");

        System.out.println("[MINING] Minerando bloco de confirmação (sem recompensa)...");
        novoBloco.mineBlock(difficulty);
        novoBloco.recalcularHashComReward();

        blockchain.adicionarBlocoMinerado(novoBloco);
        blockchain.limparMempool(novoBloco);
        repository.saveBlock(novoBloco);

        System.out.println("[MINING] ✅ Bloco de confirmação #" + nextHeight + " persistido.");
        return novoBloco;
    }

}