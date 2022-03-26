package configs.params;

public class MetadataConfig {
    private String metadataId;
    private String metadataAddress;
    private String metadataType;
    private long metadataValue;

    public MetadataConfig(String id, String add, String type, long val){
        metadataId = id;
        metadataAddress = add;
        metadataType = type;
        metadataValue = val;
    }

    public String getMetadataId() {
        return metadataId;
    }

    public void setMetadataId(String metadataId) {
        this.metadataId = metadataId;
    }

    public String getMetadataAddress() {
        return metadataAddress;
    }

    public void setMetadataAddress(String metadataAddress) {
        this.metadataAddress = metadataAddress;
    }

    public String getMetadataType() {
        return metadataType;
    }

    public void setMetadataType(String metadataType) {
        this.metadataType = metadataType;
    }

    public long getMetadataValue() {
        return metadataValue;
    }

    public void setMetadataValue(long metadataValue) {
        this.metadataValue = metadataValue;
    }
}
