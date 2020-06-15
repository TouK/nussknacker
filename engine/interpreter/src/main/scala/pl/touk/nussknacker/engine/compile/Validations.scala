package pl.touk.nussknacker.engine.compile

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingParameters, NodeId, RedundantParameters}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}
import pl.touk.nussknacker.engine.graph.evaluatedparam

import scala.annotation.tailrec

object Validations {

  import cats.data.ValidatedNel
  import cats.implicits._

  def validateParameters[T >: PartSubGraphCompilationError <: ProcessCompilationError](parameterDefinitions: List[Parameter],
                                                                                       parameters: List[evaluatedparam.Parameter])
                                                                                      (implicit nodeId: NodeId): ValidatedNel[T, Unit] = {
    val definedParamNamesSet = parameterDefinitions.map(_.name).toSet
    val usedParamNamesSet = parameters.map(_.name).toSet

    val validatedRedundant = validateRedundancy(definedParamNamesSet, usedParamNamesSet)
    val validatedMissing = validateMissingness(definedParamNamesSet, usedParamNamesSet)
    //TODO as a target, these validations should check evaluated value of expression
    val validatedCustom = validateWithCustomValidators(parameterDefinitions, parameters)

    (validatedRedundant,
      validatedMissing,
      validatedCustom
      ).mapN { (_, _, _) => Unit }
  }

  def validateSubProcessParameters[T >: PartSubGraphCompilationError <: ProcessCompilationError](definedParamNamesSet: Set[String],
                                                                                                 usedParamNamesSet: Set[String])
                                                                                                (implicit nodeId: NodeId): ValidatedNel[T, Unit] = {
    val validatedRedundant = validateRedundancy(definedParamNamesSet, usedParamNamesSet)
    val validatedMissing = validateMissingness(definedParamNamesSet, usedParamNamesSet)

    (validatedRedundant,
      validatedMissing
      ).mapN { (_, _) => Unit }
  }

  private def validateRedundancy[T >: PartSubGraphCompilationError <: ProcessCompilationError](definedParamNamesSet: Set[String],
                                                                                               usedParamNamesSet: Set[String])
                                                                                              (implicit nodeId: NodeId) = {
    val redundantParams = usedParamNamesSet.diff(definedParamNamesSet)
    if (redundantParams.nonEmpty) invalid(RedundantParameters(redundantParams)).toValidatedNel else valid(Unit)
  }

  private def validateMissingness[T >: PartSubGraphCompilationError <: ProcessCompilationError](definedParamNamesSet: Set[String],
                                                                                                usedParamNamesSet: Set[String])
                                                                                               (implicit nodeId: NodeId) = {
    val notUsedParams = definedParamNamesSet.diff(usedParamNamesSet)
    if (notUsedParams.nonEmpty) invalid(MissingParameters(notUsedParams)).toValidatedNel else valid(Unit)
  }

  private def validateWithCustomValidators[T >: PartSubGraphCompilationError <: ProcessCompilationError](parameterDefinitions: List[Parameter],
                                                                                                         parameters: List[evaluatedparam.Parameter])
                                                                                                        (implicit nodeId: NodeId) = {
    def validateValidatorsList(param: evaluatedparam.Parameter, validatorList: List[ParameterValidator]) = {
      validatorList.map(_.isValid(param.name, param.expression.expression, None).toValidatedNel).sequence.map(_ => Unit)
    }

    @tailrec
    def validatePriorityGroups(param: evaluatedparam.Parameter, priorityGroups: List[List[ParameterValidator]]):
                                            Validated[NonEmptyList[PartSubGraphCompilationError], Unit.type] = {
      priorityGroups match {
        case group :: Nil => validateValidatorsList(param, group)
        case group :: rest =>
          val validationResult = validateValidatorsList(param, group)
          if (validationResult.isValid) validatePriorityGroups(param, rest)
          else validationResult
      }
    }

    val validators = parameterDefinitions.map(param => (param.name, param.validators)).toMap
    parameters.collect {
      case param if validators.getOrElse(param.name, Nil).nonEmpty =>
        val validatorList = validators.getOrElse(param.name, Nil)
        // if at least one of validators has undefined priority, ignore priorities
        val isPriorityDefined = validatorList.forall(_.priority.isDefined)
        if (isPriorityDefined) {
          val validatorsPerPriority = validatorList.groupBy(_.priority).toList.map{
            case (key, l) => (key.getOrElse(0L), l)
          }.sortBy(-_._1).map(_._2)
          validatePriorityGroups(param, validatorsPerPriority)
        } else {
          validatePriorityGroups(param, List(validatorList))
        }
    }.sequence.map(_ => Unit)
  }
}
