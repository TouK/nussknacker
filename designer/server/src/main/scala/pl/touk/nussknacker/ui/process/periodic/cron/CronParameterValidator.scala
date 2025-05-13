package pl.touk.nussknacker.ui.process.periodic.cron

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.definition.CustomParameterValidatorDelegate
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.ui.process.periodic.utils.SchedulePropertyExtractorUtils

// Valid expression is e.g.: 0 * * * * ? * which means run every minute at 0 second
object CronParameterValidator extends CustomParameterValidatorDelegate("cron_validator") {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: api.NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    def createValidationError: CustomParameterValidationError = {
      CustomParameterValidationError(
        message = "Expression is not valid cron expression",
        description = s"Expression '$value' is not valid cron expression",
        paramName = paramName,
        nodeId = nodeId.id
      )
    }
    value match {
      case Some(s: String) =>
        SchedulePropertyExtractorUtils
          .parseAndValidateProperty(s)
          .fold(_ => invalid(createValidationError), _ => valid(()))
      case _ => invalid(createValidationError)
    }

  }

}
