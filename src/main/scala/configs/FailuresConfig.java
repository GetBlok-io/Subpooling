package configs;

public class FailuresConfig {
    /**
     * Config for failure parameters
     */
    private long failedBlock;
    private double failedValue;

    public FailuresConfig(){
        failedBlock = 0;
        failedValue = 0.0;
    }


    public long getFailedBlock() {
        return failedBlock;
    }

    public void setFailedBlock(long failedBlock) {
        this.failedBlock = failedBlock;
    }

    public double getFailedValue() {
        return failedValue;
    }

    public void setFailedValue(double failedValue) {
        this.failedValue = failedValue;
    }
}
