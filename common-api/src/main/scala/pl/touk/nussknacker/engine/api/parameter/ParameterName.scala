package pl.touk.nussknacker.engine.api.parameter

final case class ParameterName(value: String) {
  def withBranchId(branchId: String): ParameterName = ParameterName(s"$value for branch $branchId")
}
