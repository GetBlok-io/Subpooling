package plasma_utils

class UntrackedPoolStateException(box: String, poolTag: String)
  extends RuntimeException(s"Failed to grab pool state for box ${box} and pool ${poolTag} from blockchain due to a tracking error!"){

}
