package io.getblok.subpooling.core.configs.node;

public class SmartPoolWalletConfig {
    private String secretStoragePath;;
    private String walletPass;

    public SmartPoolWalletConfig(String name, String pass){
        secretStoragePath = name;
        walletPass = pass;
    }



    public String getWalletPass() {
        return walletPass;
    }

    public String getSecretStoragePath() {
        return secretStoragePath;
    }
}
