package pl.touk.nussknacker.engine.compile

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.valid
import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.definition.ParameterValidator

import scala.annotation.tailrec

object ValidationUtils {
  import cats.implicits._

  def validateWithPriorityGroups(paramName: String, expression: String, isParameterRequired: Boolean, validatorList: List[ParameterValidator], label: Option[String])(implicit nodeId: NodeId):
  Validated[NonEmptyList[PartSubGraphCompilationError], Unit.type] = {

    @tailrec
    def validatePriorityGroups(paramName: String, expression: String, priorityGroups: List[List[ParameterValidator]], isRequiredParameter: Boolean, label: Option[String]):  Validated[NonEmptyList[PartSubGraphCompilationError], Unit.type] = {

      def isValidWithBlankAndOptional(paramName: String, expression: String, validator: ParameterValidator, isRequiredParameter: Boolean, label: Option[String]): Validated[PartSubGraphCompilationError, Unit] = {
        if (StringUtils.isBlank(expression) && !isRequiredParameter && validator.skipOnBlankIfNotRequired.getOrElse(false)) valid(Unit)
        else validator.isValid(paramName, expression, label)
      }

      def validateValidatorsList(paramName: String, expression: String, validatorList: List[ParameterValidator], isRequiredParameter: Boolean, label: Option[String]) = {
        validatorList.map(v => isValidWithBlankAndOptional(paramName, expression, v, isRequiredParameter, label).toValidatedNel).sequence.map(_ => Unit)
      }

      priorityGroups match {
        case group :: Nil => validateValidatorsList(paramName, expression, group, isRequiredParameter, label)
        case group :: rest =>
          val validationResult = validateValidatorsList(paramName, expression, group, isRequiredParameter, label)
          if (validationResult.isValid) validatePriorityGroups(paramName, expression, rest, isRequiredParameter, label)
          else validationResult
      }
    }

    // if at least one of validators has undefined priority, ignore priorities
    val isPriorityDefined = validatorList.forall(_.priority.isDefined)
    if (isPriorityDefined) {
      val validatorsPerPriority = validatorList.groupBy(_.priority).toList.map{
        case (key, l) => (key.getOrElse(0L), l)
      }.sortBy(-_._1).map(_._2)
      validatePriorityGroups(paramName, expression, validatorsPerPriority, isParameterRequired, label)
    } else {
      validatePriorityGroups(paramName, expression, List(validatorList),isParameterRequired, label)
    }
  }
}
