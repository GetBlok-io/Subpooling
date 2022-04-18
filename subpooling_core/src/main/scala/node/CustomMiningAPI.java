package io.getblok.subpooling_core.node;

import org.ergoplatform.restapi.client.*;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface CustomMiningAPI {
    /**
     * Read miner reward address
     *
     * @return Call&lt;InlineResponse2005&gt;
     */
    @GET("mining/rewardAddress")
    Call<InlineResponse2005> miningReadMinerRewardAddress();


    /**
     * Read public key associated with miner rewards
     *
     * @return Call&lt;InlineResponse2006&gt;
     */
    @GET("mining/rewardPublicKey")
    Call<MinerPKResponse> miningReadMinerRewardPubkey();


    /**
     * Request block candidate
     *
     * @return Call&lt;WorkMessage&gt;
     */
    @GET("mining/candidate")
    Call<WorkMessage> miningRequestBlockCandidate();


    /**
     * Request block candidate
     *
     * @param body  (required)
     * @return Call&lt;WorkMessage&gt;
     */
    @Headers({
            "Content-Type:application/json"
    })
    @POST("mining/candidateWithTxs")
    Call<WorkMessage> miningRequestBlockCandidateWithMandatoryTransactions(
            @retrofit2.http.Body java.util.List<ErgoTransaction> body
    );

    /**
     * Submit solution for current candidate
     *
     * @param body  (required)
     * @return Call&lt;Void&gt;
     */
    @Headers({
            "Content-Type:application/json"
    })
    @POST("mining/solution")
    Call<Void> miningSubmitSolution(
            @retrofit2.http.Body PowSolutions body
    );

}
