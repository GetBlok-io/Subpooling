package configs.node;

import org.ergoplatform.appkit.NetworkType;

public class SmartPoolNodeConfig {
    private SmartPoolAPIConfig nodeApi;
    private SmartPoolWalletConfig wallet;
    private NetworkType networkType;

    public SmartPoolNodeConfig(SmartPoolAPIConfig api, SmartPoolWalletConfig wall, NetworkType network){
        nodeApi = api;
        wallet = wall;
        networkType = network;
    }

    public SmartPoolAPIConfig getNodeApi() {
        return nodeApi;
    }

    public SmartPoolWalletConfig getWallet() {
        return wallet;
    }

    public NetworkType getNetworkType() {
        return networkType;
    }
}
