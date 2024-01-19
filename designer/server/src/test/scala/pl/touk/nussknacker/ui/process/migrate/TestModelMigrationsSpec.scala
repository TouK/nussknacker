package pl.touk.nussknacker.ui.process.migrate

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.RedundantParameters
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  ValidationErrors,
  ValidationResult,
  ValidationWarnings
}
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData._
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes._
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser}

import scala.concurrent.ExecutionContext

class TestModelMigrationsSpec extends AnyFunSuite with Matchers {

  // TODO: tests for user privileges
  private implicit val user: LoggedUser = AdminUser("admin", "admin")

  private val batchingExecutionContext: ExecutionContext = ExecutionContext.global

  test("should perform test migration") {
    val testMigration = newTestModelMigrations(new TestMigrations(1, 2))
    val process       = wrapWithDetails(validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    results.head.newErrors shouldBe ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty)
  }

  test("should perform test migration on multiple source scenario") {
    val testMigration = newTestModelMigrations(new TestMigrations(8))
    val process       = wrapWithDetails(multipleSourcesValidProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    results.head.newErrors shouldBe ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty)
  }

  test("should perform migration that should fail on new errors") {
    val testMigration = newTestModelMigrations(new TestMigrations(6))
    val process       = wrapWithDetails(validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    results.head.newErrors shouldBe ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty)
  }

  test("should detect failed migration") {
    val testMigration = newTestModelMigrations(new TestMigrations(2, 3))
    val process       = wrapWithDetails(validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
  }

  test("should detect failed migration on multiple sources scenario") {
    val testMigration = newTestModelMigrations(new TestMigrations(9))
    val process       = wrapWithDetails(multipleSourcesValidProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    errorTypes(results.head.newErrors) shouldBe Map(
      "source1" -> List(classOf[RedundantParameters].getSimpleName),
      "source2" -> List(classOf[RedundantParameters].getSimpleName)
    )
  }

  test("should ignore failed migration when it may fail") {
    val testMigration = newTestModelMigrations(new TestMigrations(2, 4))
    val process       = wrapWithDetails(validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
  }

  test("should report only new errors") {
    val testMigration = newTestModelMigrations(new TestMigrations(2, 4))

    val invalidProcess: DisplayableProcess =
      toDisplayable(
        ScenarioBuilder
          .streaming("fooProcess")
          .source("source", existingSourceFactory)
          .processor("notExistingService", "IDONTEXIST")
          .processor("processor", existingServiceId)
          .emptySink("sink", existingSinkFactory)
      )

    val validationResult = flinkProcessValidator.validate(invalidProcess)
    val process          = wrapWithDetails(invalidProcess, validationResult)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
  }

  test("should migrate fragment and its usage within scenario") {
    val testMigration = newTestModelMigrations(new TestMigrations(7))
    val fragment      = toDisplayable(sampleFragmentOneOut)
    val process =
      toDisplayable(
        ScenarioBuilder
          .streaming("fooProcess")
          .source("source", existingSourceFactory)
          .fragmentOneOut("fragment", fragment.name.value, "output", "fragmentResult", "param1" -> "'foo'")
          .emptySink("sink", existingSinkFactory)
      )

    val results = testMigration.testMigrations(
      List(wrapWithDetails(process)),
      List(wrapWithDetails(fragment)),
      batchingExecutionContext
    )

    results should have size 2
  }

  test("should migrate scenario with fragment which does not require any migrations") {
    val fragment = toDisplayable(sampleFragmentOneOut)

    val testMigration = new TestModelMigrations(
      mapProcessingTypeDataProvider(Streaming -> new TestMigrations(8)),
      mapProcessingTypeDataProvider(Streaming -> TestFactory.flinkProcessValidator)
    )

    val process =
      toDisplayable(
        ScenarioBuilder
          .streaming("fooProcess")
          .source("source", existingSourceFactory)
          .fragmentOneOut("fragment", fragment.name.value, "output", "fragmentResult", "param1" -> "'foo'")
          .emptySink("sink", existingSinkFactory)
      )

    val results = testMigration.testMigrations(
      List(wrapWithDetails(process)),
      List(wrapWithDetails(fragment).copy(modelVersion = Some(10))),
      batchingExecutionContext
    )

    val processMigrationResult = results.find(_.processName == process.name).get
    processMigrationResult.newErrors.hasErrors shouldBe false
    processMigrationResult.newErrors.hasWarnings shouldBe false
  }

  private def errorTypes(validationResult: ValidationResult): Map[String, List[String]] =
    validationResult.errors.invalidNodes.mapValuesNow(_.map(_.typ))

  private def newTestModelMigrations(testMigrations: TestMigrations): TestModelMigrations =
    new TestModelMigrations(
      mapProcessingTypeDataProvider(Streaming -> testMigrations),
      TestFactory.processValidatorByProcessingType
    )

}
