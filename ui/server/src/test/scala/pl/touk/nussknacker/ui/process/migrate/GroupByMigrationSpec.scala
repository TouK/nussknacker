package pl.touk.nussknacker.ui.process.migrate

import org.scalatest.FunSuite
import org.scalatest.Matchers.{convertToAnyShouldWrapper, have}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.api.graph.evaluatedparam
import pl.touk.nussknacker.engine.api.graph.node.CustomNode
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.{existingSinkFactory, existingSourceFactory}
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory, TestProcessingTypes}

import scala.reflect.ClassTag

class GroupByMigrationSpec extends FunSuite {

  import pl.touk.nussknacker.engine.spel.Implicits._

  private val migrations = ProcessMigrations.listOf(GroupByMigration)

  test("should migrate custom aggregation node keyBy parameter name to groupBy") {
    val testMigration = newTestModelMigrations(migrations)
    val process =
      ProcessTestData.toValidatedDisplayable(EspProcessBuilder
        .id("fooProcess")
        .exceptionHandler()
        .source("source", existingSourceFactory)
        .customNode("customNode", "groupedBy", "aggregate-sliding", "keyBy" -> "#input")
        .emptySink("sink", existingSinkFactory))

    val results = testMigration.testMigrations(List(ProcessTestData.toDetails(process)), List())

    results should have size 1
    val processMigrationResult = results.find(_.converted.id == process.id).get
    processMigrationResult.shouldFail shouldBe false
    getFirst[CustomNode](processMigrationResult).parameters shouldBe List(evaluatedparam.Parameter("groupBy", "#input"))
  }

  test("should do nothing for non aggregate custom node") {
    val testMigration = newTestModelMigrations(migrations)
    val process =
      ProcessTestData.toValidatedDisplayable(EspProcessBuilder
        .id("fooProcess")
        .exceptionHandler()
        .source("source", existingSourceFactory)
        .customNode("customNode", "groupedBy", "non-aggregate-sliding", "keyBy" -> "#input")
        .emptySink("sink", existingSinkFactory))

    val results = testMigration.testMigrations(List(ProcessTestData.toDetails(process)), List())

    results should have size 1
    val processMigrationResult = results.find(_.converted.id == process.id).get
    processMigrationResult.shouldFail shouldBe false
    getFirst[CustomNode](processMigrationResult).parameters shouldBe List(evaluatedparam.Parameter("keyBy", "#input"))
  }

  test("should do nothing when custom node is missing") {
    val testMigration = newTestModelMigrations(migrations)
    val process =
      ProcessTestData.toValidatedDisplayable(EspProcessBuilder
        .id("fooProcess")
        .exceptionHandler()
        .source("source", existingSourceFactory)
        .emptySink("sink", existingSinkFactory))

    val results = testMigration.testMigrations(List(ProcessTestData.toDetails(process)), List())

    results should have size 1
    val processMigrationResult = results.find(_.converted.id == process.id).get
    processMigrationResult.shouldFail shouldBe false
  }

  private def getFirst[T: ClassTag](result: TestMigrationResult): T = result.converted.nodes.collectFirst { case t: T => t }.get

  private def newTestModelMigrations(testMigrations: ProcessMigrations): TestModelMigrations = {
    new TestModelMigrations(TestFactory.mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> testMigrations), TestFactory.processValidation)
  }

}
