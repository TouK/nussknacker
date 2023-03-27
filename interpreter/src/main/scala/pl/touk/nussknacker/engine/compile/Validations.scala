package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingParameters, RedundantParameters}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.api.NodeId

object Validations {

  import cats.data.ValidatedNel
  import cats.implicits._

  def validateParameters(parameterDefinitions: List[Parameter],
                         parameters: List[evaluatedparam.Parameter])
                        (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    val definedParamNamesSet = parameterDefinitions.map(_.name).toSet
    val usedParamNamesSet = parameters.map(_.name).toSet

    val validatedRedundant = validateRedundancy(definedParamNamesSet, usedParamNamesSet)
    val validatedMissing = validateMissingness(definedParamNamesSet, usedParamNamesSet)
    //TODO as a target, these validations should check evaluated value of expression
    val validatedCustom = validateWithCustomValidators(parameterDefinitions, parameters)

    (validatedRedundant,
      validatedMissing,
      validatedCustom
      ).mapN { (_, _, _) => () }
  }

  def validateSubProcessParameters(definedParamNamesSet: Set[String],
                                   usedParamNamesSet: Set[String])
                                  (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    val validatedRedundant = validateRedundancy(definedParamNamesSet, usedParamNamesSet)
    val validatedMissing = validateMissingness(definedParamNamesSet, usedParamNamesSet)

    (validatedRedundant,
      validatedMissing
      ).mapN { (_, _) => () }
  }

  private def validateRedundancy(definedParamNamesSet: Set[String],
                                 usedParamNamesSet: Set[String])
                                (implicit nodeId: NodeId) = {
    val redundantParams = usedParamNamesSet.diff(definedParamNamesSet)
    if (redundantParams.nonEmpty) invalid(RedundantParameters(redundantParams)).toValidatedNel else valid(())
  }

  private def validateMissingness(definedParamNamesSet: Set[String],
                                  usedParamNamesSet: Set[String])
                                 (implicit nodeId: NodeId) = {
    val notUsedParams = definedParamNamesSet.diff(usedParamNamesSet)
    if (notUsedParams.nonEmpty) invalid(MissingParameters(notUsedParams)).toValidatedNel else valid(())
  }

  private def validateWithCustomValidators(parameterDefinitions: List[Parameter],
                                           parameters: List[evaluatedparam.Parameter])
                                          (implicit nodeId: NodeId) = {
    val definitionsMap = parameterDefinitions.map(param => (param.name, param)).toMap
    val validationResults = for {
      param <- parameters
      paramDefinition <- definitionsMap.get(param.name)
      paramValidationResult = validate(paramDefinition, param)
    } yield paramValidationResult
    validationResults.sequence.map(_ => ())
  }


  def validate(paramDefinition: Parameter, parameter: evaluatedparam.Parameter)
              (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    paramDefinition.validators.map { validator =>
      validator.isValid(parameter.name, parameter.expression.expression, None).toValidatedNel
    }.sequence.map(_ => ())
  }
}
