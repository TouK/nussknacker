package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalid, valid}
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.{MissingParameters, NodeId, RedundantParameters}

object Validations {

  def validateParameters[T>:PartSubGraphCompilationError](definedParamNames: List[String], usedParamNames: List[String])(implicit nodeId: NodeId)
  : ValidatedNel[T, Unit]= {
    val usedParamNamesSet = usedParamNames.toSet
    val definedParamNamesSet = definedParamNames.toSet
    val redundantParams = usedParamNamesSet.diff(definedParamNamesSet)
    val notUsedParams = definedParamNamesSet.diff(usedParamNamesSet)
    if (redundantParams.nonEmpty) {
      invalid(RedundantParameters(redundantParams)).toValidatedNel
    } else if (notUsedParams.nonEmpty) {
      invalid(MissingParameters(notUsedParams)).toValidatedNel
    } else {
      valid(Unit)
    }
  }

}
