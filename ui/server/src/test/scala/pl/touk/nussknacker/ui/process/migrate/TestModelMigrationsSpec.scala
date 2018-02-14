package pl.touk.nussknacker.ui.process.migrate

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.RedundantParameters
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ClazzRef, Parameter}
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.node.{SubprocessInput, SubprocessInputDefinition}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.api.ProcessTestData.{existingServiceId, existingSinkFactory, existingSourceFactory}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.ValidatedDisplayableProcess
import pl.touk.nussknacker.ui.validation.ValidationResults.{ValidationErrors, ValidationResult, ValidationWarnings}
import spel.Implicits._

import scala.reflect.ClassTag

class TestModelMigrationsSpec extends FunSuite with Matchers {

  test("should perform test migration") {
    val testMigration = newTestModelMigrations(new TestMigrations(1, 2))
    val process = ProcessTestData.toDetails(ProcessTestData.validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List())

    results.head.newErrors shouldBe ValidationResult(ValidationErrors.success, ValidationWarnings.success)
  }

  test("should perform migration that should fail on new errors") {
    val testMigration = newTestModelMigrations(new TestMigrations(6))
    val process = ProcessTestData.toDetails(ProcessTestData.validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List())

    results.head.newErrors shouldBe ValidationResult(ValidationErrors.success, ValidationWarnings.success)
    results.head.shouldFail shouldBe false
    results.head.shouldFailOnNewErrors shouldBe true
  }

  test("should detect failed migration") {
    val testMigration = newTestModelMigrations(new TestMigrations(2, 3))
    val process = ProcessTestData.toDetails(ProcessTestData.validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List())

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
    results.head.shouldFail shouldBe true
  }

  test("should ignore failed migration when it may fail") {
    val testMigration = newTestModelMigrations(new TestMigrations(2, 4))
    val process = ProcessTestData.toDetails(ProcessTestData.validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List())

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
    results.head.shouldFail shouldBe false

  }

  test("should report only new errors") {
    val testMigration = newTestModelMigrations(new TestMigrations(2, 4))

    val invalidProcess : ValidatedDisplayableProcess =
      ProcessTestData.toValidatedDisplayable(EspProcessBuilder
        .id("fooProcess")
        .exceptionHandler()
        .source("source", existingSourceFactory)
        .processor("notExistingService", "IDONTEXIST")
        .processor("processor", existingServiceId)
        .emptySink("sink", existingSinkFactory))

    val process = ProcessTestData.toDetails(invalidProcess)

    val results = testMigration.testMigrations(List(process), List())

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
    results.head.shouldFail shouldBe false
  }

  test("should migrate subprocess and its usage within process") {
    val testMigration = newTestModelMigrations(new TestMigrations(7))
    val subprocess = ProcessTestData.toValidatedDisplayable(ProcessCanonizer.uncanonize(ProcessTestData.sampleSubprocessOneOut).getOrElse(null))
    val process =
      ProcessTestData.toValidatedDisplayable(EspProcessBuilder
        .id("fooProcess")
        .exceptionHandler()
        .source("source", existingSourceFactory)
        .subprocessOneOut("subprocess", subprocess.id, "output", "param1" -> "'foo'")
        .emptySink("sink", existingSinkFactory))

    val results = testMigration.testMigrations(List(ProcessTestData.toDetails(process)), List(ProcessTestData.toDetails(subprocess)))

    results should have size 2
    val (subprocessMigrationResult, processMigrationResult) = (results.find(_.converted.id == subprocess.id).get, results.find(_.converted.id == process.id).get)
    subprocessMigrationResult.shouldFail shouldBe false
    processMigrationResult.shouldFail shouldBe false
    getFirst[SubprocessInputDefinition](subprocessMigrationResult).parameters shouldBe List(Parameter("param42", ClazzRef(classOf[String])))
    getFirst[SubprocessInput](processMigrationResult).ref.parameters shouldBe List(evaluatedparam.Parameter("param42", "'foo'"))
  }

  private def getFirst[T: ClassTag](result: TestMigrationResult): T = {
    result.converted.nodes.collectFirst { case t: T => t}.get
  }

  private def errorTypes(validationResult: ValidationResult) : Map[String, List[String]]
    = validationResult.errors.invalidNodes.mapValues(_.map(_.typ))

  private def newTestModelMigrations(testMigrations: TestMigrations): TestModelMigrations = {
    new TestModelMigrations(
      Map(ProcessingType.Streaming -> testMigrations),
      Map(ProcessingType.Streaming -> ProcessTestData.validator))

  }
}
