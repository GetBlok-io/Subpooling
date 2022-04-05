package io.getblok.subpooling.core.configs.params;

public class HoldingConfig {
    private String holdingAddress;
    private String holdingType;
    private long holdingValue;

    public HoldingConfig(String add, String type, long val){
        holdingAddress = add;
        holdingType = type;
        holdingValue = val;
    }


    public String getHoldingAddress() {
        return holdingAddress;
    }

    public void setHoldingAddress(String holdingAddress) {
        this.holdingAddress = holdingAddress;
    }

    public String getHoldingType() {
        return holdingType;
    }

    public void setHoldingType(String holdingType) {
        this.holdingType = holdingType;
    }

    public long getHoldingValue() {
        return holdingValue;
    }

    public void setHoldingValue(long holdingValue) {
        this.holdingValue = holdingValue;
    }
}
