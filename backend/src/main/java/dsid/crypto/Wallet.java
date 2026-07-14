// Caminho: src/main/java/dsid/crypto/Wallet.java
package dsid.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Carteira criptográfica do Treinador.
 *
 * Na primeira execução gera um par RSA-2048 e persiste no disco via
 * {@link KeyPairManager}. Nas execuções seguintes carrega o par salvo,
 * garantindo identidade persistente entre sessões.
 *
 * A chave pública funciona como o "endereço" público do treinador na rede.
 * A chave privada assina as transações de troca de Pokémon.
 */
public class Wallet {

    private PrivateKey chavePrivada;
    private PublicKey  chavePublica;

    // ---------------------------------------------------------------
    // Construtor
    // ---------------------------------------------------------------
    public Wallet(String nomeTreinador) {
        if (KeyPairManager.chavesExistem(nomeTreinador)) {
            System.out.println("[WALLET] Save encontrado! Carregando identidade de: " + nomeTreinador);
            this.chavePrivada = KeyPairManager.carregarChavePrivada(nomeTreinador + "_private.key");
            this.chavePublica = KeyPairManager.carregarChavePublica(nomeTreinador + "_public.key");
        } else {
            System.out.println("[WALLET] Nenhum save encontrado. Gerando nova identidade para: " + nomeTreinador);
            gerarParDeChaves();
            KeyPairManager.salvarChaves(this.chavePrivada, this.chavePublica, nomeTreinador);
        }
    }

    // ---------------------------------------------------------------
    // Geração do par de chaves
    // ---------------------------------------------------------------
    private void gerarParDeChaves() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            SecureRandom random     = SecureRandom.getInstanceStrong();
            keyGen.initialize(2048, random);

            KeyPair pair    = keyGen.generateKeyPair();
            this.chavePrivada = pair.getPrivate();
            this.chavePublica = pair.getPublic();
        } catch (Exception e) {
            throw new RuntimeException("[WALLET] Falha ao gerar chaves RSA: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // Utilitário
    // ---------------------------------------------------------------
    /**
     * Retorna a chave pública codificada em Base64.
     * Equivalente ao "ID de Treinador" exibido na tela do jogo.
     */
    public String getEnderecoPublico() {
        return Base64.getEncoder().encodeToString(chavePublica.getEncoded());
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------
    public PrivateKey getChavePrivada() { return chavePrivada; }
    public PublicKey  getChavePublica() { return chavePublica; }
}