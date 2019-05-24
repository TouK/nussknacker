package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalid, valid}
import cats.data.ValidatedNel
import cats.syntax.apply._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingParameters, NodeId, RedundantParameters}
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError}

object Validations {

  def validateParameters[T >: PartSubGraphCompilationError <: ProcessCompilationError](definedParamNamesSet: Set[String],
                                                                                       usedParamNamesSet: Set[String])
                                                                                      (implicit nodeId: NodeId): ValidatedNel[T, Unit] = {
    val redundantParams = usedParamNamesSet.diff(definedParamNamesSet)
    val notUsedParams = definedParamNamesSet.diff(usedParamNamesSet)
    val validatedRedundant = if (redundantParams.nonEmpty) invalid(RedundantParameters(redundantParams)).toValidatedNel else valid(Unit)
    val validatedMissing = if (notUsedParams.nonEmpty) invalid(MissingParameters(notUsedParams)).toValidatedNel else valid(Unit)
    (
      validatedRedundant,
      validatedMissing
    ).mapN { (_, _) => Unit }
  }

}
