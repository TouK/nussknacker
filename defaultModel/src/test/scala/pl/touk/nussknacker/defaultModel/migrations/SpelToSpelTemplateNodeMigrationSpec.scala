package pl.touk.nussknacker.defaultModel.migrations

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.migrations.SpelToSpelTemplateNodeMigration
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
import pl.touk.nussknacker.engine.graph.node.{
  CustomNode,
  Enricher,
  Join,
  Processor,
  Sink,
  Source,
  UserDefinedAdditionalNodeFields
}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

class SpelToSpelTemplateNodeMigrationSpec extends AnyFunSuite with Matchers {

  private val paramsBeforeMigration = List(
    Parameter(ParameterName("name1"), "11".spel),
    Parameter(ParameterName("name2"), "'John'".spel),
  )

  private val paramsAfterMigration = List(
    Parameter(ParameterName("name1"), "11".spel),
    Parameter(ParameterName("name2"), "John".spelTemplate),
  )

  private val additionalFields = Some(UserDefinedAdditionalNodeFields(Some("desc"), None))
  private val metaData         = MetaData("test", StreamMetaData(Some(1)))

  test("should migrate custom node") {
    val node = CustomNode("id", Some("out1"), "typ1", paramsBeforeMigration, additionalFields)

    val migrated = SpelToSpelTemplateNodeMigration.migrateNode(metaData)(node)

    migrated shouldBe CustomNode("id", Some("out1"), "typ1", paramsAfterMigration, additionalFields)
  }

  test("should migrate enricher") {
    val node = Enricher("id", ServiceRef("id", paramsBeforeMigration), "out1", additionalFields)

    val migrated = SpelToSpelTemplateNodeMigration.migrateNode(metaData)(node)

    migrated shouldBe Enricher("id", ServiceRef("id", paramsAfterMigration), "out1", additionalFields)
  }

  test("should migrate join") {
    val branchParameters = List(BranchParameters("branchId", paramsBeforeMigration))
    val node             = Join("id", Some("out1"), "typ1", paramsBeforeMigration, branchParameters, additionalFields)

    val migrated = SpelToSpelTemplateNodeMigration.migrateNode(metaData)(node)

    migrated shouldBe Join(
      id = "id",
      outputVar = Some("out1"),
      nodeType = "typ1",
      parameters = paramsAfterMigration,
      branchParameters = List(
        BranchParameters(
          branchId = "branchId",
          parameters = paramsAfterMigration
        )
      ),
      additionalFields = additionalFields
    )
  }

  test("should migrate processor") {
    val node = Processor("id", ServiceRef("id", paramsBeforeMigration), Some(false), additionalFields)

    val migrated = SpelToSpelTemplateNodeMigration.migrateNode(metaData)(node)

    migrated shouldBe Processor("id", ServiceRef("id", paramsAfterMigration), Some(false), additionalFields)
  }

  test("should migrate sink") {
    val node = Sink("id", SinkRef("id", paramsBeforeMigration), None, Some(false), additionalFields)

    val migrated = SpelToSpelTemplateNodeMigration.migrateNode(metaData)(node)

    migrated shouldBe Sink("id", SinkRef("id", paramsAfterMigration), None, Some(false), additionalFields)
  }

  test("should migrate source") {
    val node = Source("id", SourceRef("id", paramsBeforeMigration), additionalFields)

    val migrated = SpelToSpelTemplateNodeMigration.migrateNode(metaData)(node)

    migrated shouldBe Source("id", SourceRef("id", paramsAfterMigration), additionalFields)
  }

}
