package pl.touk.nussknacker.engine.process.compiler

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ScenarioNameValidationError
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.process.helpers.{ProcessTestHelpers, ProcessTestHelpersConfigCreator}
import pl.touk.nussknacker.engine.testing.LocalModelData

class FlinkScenarioNameValidationSpec extends AnyFunSuite with Matchers with ProcessTestHelpers with Inside {

  test("we are checking name validation") {
    val processWithInvalidName =
      ScenarioBuilder
        .streaming("invalid+name")
        .source("sourceId", "input")
        .emptySink("emptySink", "monitor")

    val components = ProcessTestHelpers.prepareComponents(List.empty)
    val modelData  = LocalModelData(config, components, configCreator = ProcessTestHelpersConfigCreator)
    val jobData: JobData =
      JobData(
        processWithInvalidName.metaData,
        ProcessVersion.empty.copy(processName = processWithInvalidName.metaData.name)
      )
    val processValidator = ProcessValidator.default(modelData)

    val validationResult = processValidator.validate(processWithInvalidName, isFragment = false)(jobData)

    validationResult.result should matchPattern { case Invalid(NonEmptyList(ScenarioNameValidationError(_, _), _)) =>
    }
  }

}
