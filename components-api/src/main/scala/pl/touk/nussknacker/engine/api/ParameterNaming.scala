package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName

object ParameterNaming {

  def getNameForBranchParameter(parameter: Parameter, branchId: String): ParameterName = {
    getNameForBranchParameter(parameter.name, branchId)
  }

  def getNameForBranchParameter(parameterName: ParameterName, branchId: String): ParameterName = {
    ParameterName(s"${parameterName.value} for branch $branchId")
  }

}
