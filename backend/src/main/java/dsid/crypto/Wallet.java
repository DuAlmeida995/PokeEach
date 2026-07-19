package dsid.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * carteira criptografica do treinador.
 *
 * na primeira execucao gera um par RSA-2048 e persiste no disco via
 * {@link KeyPairManager}
 * nas execucoes seguintes carrega o par salvo,
 * garantindo identidade persistente entre sessoes
 *
 * a chave publica funciona como o "endereco" publico do treinador na rede
 * a chave privada assina as transacoes de troca de Pokemon
 */
public class Wallet {

    private PrivateKey chavePrivada;
    private PublicKey  chavePublica;

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

    public String getEnderecoPublico() {
        return Base64.getEncoder().encodeToString(chavePublica.getEncoded());
    }

    public PrivateKey getChavePrivada() { return chavePrivada; }
    public PublicKey  getChavePublica() { return chavePublica; }
}