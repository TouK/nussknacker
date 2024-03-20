package pl.touk.nussknacker.engine.lite.components

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter
import pl.touk.nussknacker.engine.util.test.{RunListResult, RunResult, TestScenarioRunner}

import java.util.UUID

trait FunctionalTestMixin {
  import LiteKafkaTestScenarioRunner._

  protected val runner: LiteKafkaTestScenarioRunner = TestScenarioRunner.kafkaLiteBased().build()
  protected val sourceName                          = "my-source"
  protected val sinkName                            = "my-sink"

  protected def randomTopic: String = UUID.randomUUID().toString

  protected def invalidTypes(typeErrors: String*): Invalid[NonEmptyList[ProcessCompilationError]] =
    invalid(typeErrors.toList, Nil, Nil)

  protected def invalidRanges(rangeErrors: String*): Invalid[NonEmptyList[ProcessCompilationError]] =
    invalid(Nil, Nil, Nil, rangeErrors.toList)

  protected def invalid(
      typeFieldErrors: List[String],
      missingFieldsError: List[String],
      redundantFieldsError: List[String],
      rangeFieldErrors: List[String]
  ): Invalid[NonEmptyList[ProcessCompilationError]] = {
    val finalMessage = OutputValidatorErrorsMessageFormatter.makeMessage(
      typeFieldErrors,
      missingFieldsError,
      redundantFieldsError,
      rangeFieldErrors
    )
    Invalid(
      NonEmptyList.one(
        CustomNodeError(sinkName, finalMessage, Some(KafkaUniversalComponentTransformer.sinkValueParamName))
      )
    )
  }

  protected def invalid(
      typeFieldErrors: List[String],
      missingFieldsError: List[String],
      redundantFieldsError: List[String]
  ): Invalid[NonEmptyList[ProcessCompilationError]] =
    invalid(typeFieldErrors, missingFieldsError, redundantFieldsError, Nil)

  protected def valid[T](data: T): Valid[RunListResult[T]] =
    Valid(RunResult.success(data))

}
