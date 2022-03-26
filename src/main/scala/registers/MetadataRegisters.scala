package registers

case class MetadataRegisters(shareDist: ShareDistribution, feeMap: PoolFees, poolInfo: PoolInfo,
                             poolOps: PoolOperators
                            ) {

  override def toString: String = s"REGS(" +
    s"\n$shareDist " +
    s"\n$feeMap" +
    s"\n$poolInfo" +
    s"\n$poolOps" +
    s"\n)"
}
