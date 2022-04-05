package io.getblok.subpooling.core.configs.params;

public class VotingConfig {
    private String voteTokenId;
    private String povTokenId;
    private long voteTokensToMint;
    private long povTokensToMint;
    private double povIncentive;
    private int voteEndHeight;


    public VotingConfig(){
        voteTokenId = "";
        povTokenId = "";
        voteTokensToMint = 0;
        povTokensToMint = 0;
        povIncentive = 0.0;
        voteEndHeight = 0;
    }

    public String getVoteTokenId() {
        return voteTokenId;
    }

    public void setVoteTokenId(String voteTokenId) {
        this.voteTokenId = voteTokenId;
    }


    public String getPovTokenId() {
        return povTokenId;
    }

    public void setPovTokenId(String povTokenId) {
        this.povTokenId = povTokenId;
    }

    public long getVoteTokensToMint() {
        return voteTokensToMint;
    }

    public void setVoteTokensToMint(long voteTokensToMint) {
        this.voteTokensToMint = voteTokensToMint;
    }

    public long getPovTokensToMint() {
        return povTokensToMint;
    }

    public void setPovTokensToMint(long povTokensToMint) {
        this.povTokensToMint = povTokensToMint;
    }

    public double getPovIncentive() {
        return povIncentive;
    }

    public void setPovIncentive(double povIncentive) {
        this.povIncentive = povIncentive;
    }

    public int getVoteEndHeight() {
        return voteEndHeight;
    }

    public void setVoteEndHeight(int voteEndHeight) {
        this.voteEndHeight = voteEndHeight;
    }
}
