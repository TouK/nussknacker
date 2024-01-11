package pl.touk.nussknacker.ui.process.migrate

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.RedundantParameters
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, FragmentInputDefinition, Source}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.restmodel.displayedgraph.ValidatedDisplayableProcess
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

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class TestModelMigrationsSpec extends AnyFunSuite with Matchers {

  private val batchingExecutionContext = ExecutionContext.global

  test("should perform test migration") {
    val testMigration = newTestModelMigrations(new TestMigrations(1, 2))
    val process       = validatedToProcess(validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    results.head.newErrors shouldBe ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty)
  }

  test("should perform test migration on multiple source scenario") {
    val testMigration = newTestModelMigrations(new TestMigrations(8))
    val process       = validatedToProcess(multipleSourcesValidProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    results.head.newErrors shouldBe ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty)
    results.head.converted.nodes.collect { case s: Source => s.ref.typ } shouldBe List(
      otherExistingSourceFactory,
      otherExistingSourceFactory
    )
  }

  test("should perform migration that should fail on new errors") {
    val testMigration = newTestModelMigrations(new TestMigrations(6))
    val process       = validatedToProcess(validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    results.head.newErrors shouldBe ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty)
    results.head.shouldFail shouldBe false
    results.head.shouldFailOnNewErrors shouldBe true
  }

  test("should detect failed migration") {
    val testMigration = newTestModelMigrations(new TestMigrations(2, 3))
    val process       = validatedToProcess(validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
    results.head.shouldFail shouldBe true
  }

  test("should detect failed migration on multiple sources scenario") {
    val testMigration = newTestModelMigrations(new TestMigrations(9))
    val process       = validatedToProcess(multipleSourcesValidProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    errorTypes(results.head.newErrors) shouldBe Map(
      "source1" -> List(classOf[RedundantParameters].getSimpleName),
      "source2" -> List(classOf[RedundantParameters].getSimpleName)
    )
    results.head.shouldFail shouldBe true
  }

  test("should ignore failed migration when it may fail") {
    val testMigration = newTestModelMigrations(new TestMigrations(2, 4))
    val process       = validatedToProcess(validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
    results.head.shouldFail shouldBe false

  }

  test("should report only new errors") {
    val testMigration = newTestModelMigrations(new TestMigrations(2, 4))

    val invalidProcess: ValidatedDisplayableProcess =
      toValidatedDisplayable(
        ScenarioBuilder
          .streaming("fooProcess")
          .source("source", existingSourceFactory)
          .processor("notExistingService", "IDONTEXIST")
          .processor("processor", existingServiceId)
          .emptySink("sink", existingSinkFactory)
      )

    val process = validatedToProcess(invalidProcess)

    val results = testMigration.testMigrations(List(process), List(), batchingExecutionContext)

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
    results.head.shouldFail shouldBe false
  }

  test("should migrate fragment and its usage within scenario") {
    val testMigration = newTestModelMigrations(new TestMigrations(7))
    val fragment      = toValidatedDisplayable(sampleFragmentOneOut)
    val process =
      toValidatedDisplayable(
        ScenarioBuilder
          .streaming("fooProcess")
          .source("source", existingSourceFactory)
          .fragmentOneOut("fragment", fragment.id, "output", "fragmentResult", "param1" -> "'foo'")
          .emptySink("sink", existingSinkFactory)
      )

    val results = testMigration.testMigrations(
      List(validatedToProcess(process)),
      List(validatedToProcess(fragment)),
      batchingExecutionContext
    )

    results should have size 2
    val (fragmentMigrationResult, processMigrationResult) =
      (results.find(_.converted.id == fragment.id).get, results.find(_.converted.id == process.id).get)
    fragmentMigrationResult.shouldFail shouldBe false
    processMigrationResult.shouldFail shouldBe false
    getFirst[FragmentInputDefinition](fragmentMigrationResult).parameters shouldBe List(
      FragmentParameter("param42", FragmentClazzRef[String])
    )
    getFirst[FragmentInput](processMigrationResult).ref.parameters shouldBe List(
      evaluatedparam.Parameter("param42", "'foo'")
    )
  }

  test("should migrate scenario with fragment which does not require any migrations") {
    val fragment = toValidatedDisplayable(sampleFragmentOneOut)

    val testMigration = new TestModelMigrations(
      mapProcessingTypeDataProvider(Streaming -> new TestMigrations(8)),
      TestFactory.flinkProcessValidation
    )

    val process =
      toValidatedDisplayable(
        ScenarioBuilder
          .streaming("fooProcess")
          .source("source", existingSourceFactory)
          .fragmentOneOut("fragment", fragment.id, "output", "fragmentResult", "param1" -> "'foo'")
          .emptySink("sink", existingSinkFactory)
      )

    val results = testMigration.testMigrations(
      List(validatedToProcess(process)),
      List(validatedToProcess(fragment).copy(modelVersion = Some(10))),
      batchingExecutionContext
    )

    val processMigrationResult = results.find(_.converted.id == process.id).get
    processMigrationResult.newErrors.hasErrors shouldBe false
    processMigrationResult.newErrors.hasWarnings shouldBe false
    processMigrationResult.converted.validationResult.hasErrors shouldBe false
    processMigrationResult.converted.validationResult.hasWarnings shouldBe false
  }

  private def getFirst[T: ClassTag](result: TestMigrationResult): T =
    result.converted.nodes.collectFirst { case t: T => t }.get

  private def errorTypes(validationResult: ValidationResult): Map[String, List[String]] =
    validationResult.errors.invalidNodes.mapValuesNow(_.map(_.typ))

  private def newTestModelMigrations(testMigrations: TestMigrations): TestModelMigrations =
    new TestModelMigrations(mapProcessingTypeDataProvider(Streaming -> testMigrations), TestFactory.processValidation)

}
