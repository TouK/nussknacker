package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.definition.Parameter

object ParameterNaming {

  def getNameForBranchParameter(parameter: Parameter, branchId: String): String = {
    getNameForBranchParameter(parameter.name, branchId)
  }

  def getNameForBranchParameter(parameterName: String, branchId: String): String = {
    s"$parameterName for branch $branchId"
  }

}
