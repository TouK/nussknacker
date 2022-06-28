package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.definition.Parameter

object ParameterNaming {
  def getNameForBranchParameter(parameter: Parameter, branchId: String): String = {
    s"${parameter.name} for branch $branchId"
  }
}
