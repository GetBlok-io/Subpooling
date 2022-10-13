package plasma_utils

import io.getblok.subpooling_core.global.AppParameters
import play.api.libs.mailer.Email

object MailGenerator {

  def emailSyncError(poolTag: String, realDigest: String, localDigest: String): Email = {
    Email(
      "CRITICAL Error While Checking Pool Syncing!",
      "subpooling@getblok.io",
      AppParameters.emailReceivers,
      Some(s"Pool ${poolTag} has become unsynced." +
        s" \n The on-chain digest was ${realDigest} but the local digest was $localDigest"
      )
    )
  }

  def emailStateError(poolTag: String, localUTXO: String): Email = {
    Email(
      "CRITICAL Error While Checking Pool States!",
      "subpooling@getblok.io",
      AppParameters.emailReceivers,
      Some(s"Pool ${poolTag} has an untracked pool state." +
        s"\n The UTXO id ${localUTXO} that we have in the database does not exist on-chain!"
      )
    )
  }
}
