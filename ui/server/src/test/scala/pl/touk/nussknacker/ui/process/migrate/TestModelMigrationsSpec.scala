package pl.touk.nussknacker.ui.process.migrate

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.RedundantParameters
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.api.ProcessTestData.{existingServiceId, existingSinkFactory, existingSourceFactory, existingStreamTransformer}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.ui.validation.ValidationResults
import pl.touk.nussknacker.ui.validation.ValidationResults.{ValidationErrors, ValidationResult, ValidationWarnings}

class TestModelMigrationsSpec extends FlatSpec with Matchers {


  it should "perform test migration" in {

    val testMigration = new TestModelMigrations(
      Map(ProcessingType.Streaming -> new TestMigrations(1,2)),
      Map(ProcessingType.Streaming -> ProcessTestData.validator))

    val process = ProcessTestData.toDetails(ProcessTestData.validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List())

    results.head.newErrors shouldBe ValidationResult(ValidationErrors.success, ValidationWarnings.success)
  }

  it should "should detect failed migration" in {

    val testMigration = new TestModelMigrations(
      Map(ProcessingType.Streaming -> new TestMigrations(2, 3)),
      Map(ProcessingType.Streaming -> ProcessTestData.validator))


    val process = ProcessTestData.toDetails(ProcessTestData.validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List())

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
    results.head.shouldFail shouldBe true

  }

  it should "should ignore failed migration when it may fail" in {

    val testMigration = new TestModelMigrations(
      Map(ProcessingType.Streaming -> new TestMigrations(2, 4)),
      Map(ProcessingType.Streaming -> ProcessTestData.validator))


    val process = ProcessTestData.toDetails(ProcessTestData.validDisplayableProcess)

    val results = testMigration.testMigrations(List(process), List())

    errorTypes(results.head.newErrors) shouldBe Map("processor" -> List(classOf[RedundantParameters].getSimpleName))
    results.head.shouldFail shouldBe false

  }

  it should "report only new errors" in {
    val testMigration = new TestModelMigrations(
      Map(ProcessingType.Streaming -> new TestMigrations(2, 4)),
      Map(ProcessingType.Streaming -> ProcessTestData.validator))


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

  private def errorTypes(validationResult: ValidationResult) : Map[String, List[String]]
    = validationResult.errors.invalidNodes.mapValues(_.map(_.typ))
}
