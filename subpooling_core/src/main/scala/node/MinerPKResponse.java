package io.getblok.subpooling_core.node;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

public class MinerPKResponse {
    @SerializedName("rewardPubKey")
    private String rewardPubKey = null;

    public MinerPKResponse rewardAddress(String rewardPubKey) {
        this.rewardPubKey = rewardPubKey;
        return this;
    }

    /**
     * Get rewardAddress
     * @return rewardAddress
     **/
    @Schema(example = "02a7955281885bf0f0ca4a48678848cad8dc5b328ce8bc1d4481d041c98e891ff3", description = "")
    public String getRewardPubKey() {
        return rewardPubKey;
    }

    public void setRewardAddress(String rewardAddress) {
        this.rewardPubKey = rewardAddress;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MinerPKResponse minerPKResponse = (MinerPKResponse) o;
        return Objects.equals(this.rewardPubKey, minerPKResponse.rewardPubKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rewardPubKey);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class InlineResponse2006 {\n");

        sb.append("    rewardAddress: ").append(toIndentedString(rewardPubKey)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }

}

