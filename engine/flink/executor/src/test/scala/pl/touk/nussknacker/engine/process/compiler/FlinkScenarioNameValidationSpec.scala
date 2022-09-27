package pl.touk.nussknacker.engine.process.compiler

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ScenarioNameValidationError
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.testing.LocalModelData

class FlinkScenarioNameValidationSpec extends AnyFunSuite with Matchers with ProcessTestHelpers with Inside {

  test("we are checking name validation") {
    val processWithInvalidName =
      ScenarioBuilder
        .streaming("invalid+name")
        .source("sourceId", "input")
        .emptySink("emptySink", "monitor")

    val creator: ProcessConfigCreator = ProcessTestHelpers.prepareCreator(List.empty, ConfigFactory.empty())
    val modelData = LocalModelData(config, creator)
    val processValidator = modelData.prepareValidatorForCategory(None, Nil)

    val validationResult = processValidator
      .validate(processWithInvalidName)

    validationResult.result should matchPattern {
      case Invalid(NonEmptyList(ScenarioNameValidationError("invalid+name", _), _)) =>
    }
  }

}
