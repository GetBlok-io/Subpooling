package io.getblok.subpooling.core.configs.node;

public class SmartPoolAPIConfig {
    private String apiUrl;
    private String apiKey;


    public SmartPoolAPIConfig(String url, String key){
        apiUrl = url;
        apiKey = key;
    }
    /**
     * Url of the Ergo node API end point
     */
    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * ApiKey which is used for Ergo node API authentication.
     * This is a secrete key whose hash was used in Ergo node config.
     */
    public String getApiKey() {
        return apiKey;
    }
}
