package pl.touk.nussknacker.engine.management.periodic.cron

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.definition.{CustomParameterValidator, CustomParameterValidatorDelegate, ParameterValidator}
import pl.touk.nussknacker.engine.management.periodic.CronScheduleProperty

import java.time.Clock
object CronParameterValidator extends CronParameterValidator {

  def delegate: ParameterValidator = CustomParameterValidatorDelegate(name)

}

// Valid expression is e.g.: * * * * * ? *
class CronParameterValidator extends CustomParameterValidator {
  override def isValid(paramName: String, value: String, label: Option[String])
                      (implicit nodeId: api.NodeId): Validated[PartSubGraphCompilationError, Unit] = {
    def createValidationError: CustomParameterValidationError = {
      CustomParameterValidationError(
        message = "Expression is not valid cron expression",
        description = s"Expression '$value' is not valid cron expression",
        paramName = paramName,
        nodeId = nodeId.id
      )
    }

    CronScheduleProperty(value).nextRunAt(Clock.systemDefaultZone()) match {
      case Left(_) => invalid(createValidationError)
      case Right(_) => valid(())
    }
  }

  override def name: String = "cron_validator"

}
