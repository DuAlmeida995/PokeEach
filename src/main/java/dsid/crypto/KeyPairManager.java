package crypto;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class KeyPairManager {

    // aqui salvamos as chaves no computador local em arquivos separados
    public static void salvarChaves(PrivateKey chavePrivada, PublicKey chavePublica, String nomeArquivoBase) {
        try {
            // salva a Chave Pública (padrao X.509)
            X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(chavePublica.getEncoded());
            FileOutputStream fosPublic = new FileOutputStream(nomeArquivoBase + "_public.key");
            fosPublic.write(x509EncodedKeySpec.getEncoded());
            fosPublic.close();

            // salva a Chave Privada (padrao PKCS#8)
            PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(chavePrivada.getEncoded());
            FileOutputStream fosPrivate = new FileOutputStream(nomeArquivoBase + "_private.key");
            fosPrivate.write(pkcs8EncodedKeySpec.getEncoded());
            fosPrivate.close();
            
            System.out.println("Chaves salvas com sucesso no disco local.");
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar as chaves no disco", e);
        }
    }

    public static PublicKey carregarChavePublica(String caminhoArquivo) {
        try {
            byte[] keyBytes = Files.readAllBytes(new File(caminhoArquivo).toPath());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar a Chave Pública. Arquivo corrompido ou inexistente.", e);
        }
    }

    public static PrivateKey carregarChavePrivada(String caminhoArquivo) {
        try {
            byte[] keyBytes = Files.readAllBytes(new File(caminhoArquivo).toPath());
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar a Chave Privada. Arquivo corrompido ou inexistente.", e);
        }
    }

    // metodo auxiliar para checar se o treinador ja tem um "save" no computador
    public static boolean chavesExistem(String nomeArquivoBase) {
        File pubKey = new File(nomeArquivoBase + "_public.key");
        File privKey = new File(nomeArquivoBase + "_private.key");
        return pubKey.exists() && privKey.exists();
    }
}