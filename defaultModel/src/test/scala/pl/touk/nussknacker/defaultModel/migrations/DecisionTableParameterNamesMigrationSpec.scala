package pl.touk.nussknacker.defaultModel.migrations

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.migrations.DecisionTableParameterNamesMigration
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.node.Enricher
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension._

class DecisionTableParameterNamesMigrationSpec extends AnyFreeSpecLike with Matchers {

  "DecisionTableParameterNamesMigration should be applied" in {
    val metaData = MetaData("test", StreamMetaData(Some(1)))
    val beforeMigration = Enricher(
      id = "decision-table",
      service = ServiceRef(
        id = "decision-table-service",
        parameters = List(
          Parameter(ParameterName("Basic Decision Table"), exampleDecisionTableJson),
          Parameter(ParameterName("Expression"), "#ROW['age'] > #input.minAge && #ROW['DoB'] != null".spel),
        )
      ),
      output = "Output",
    )
    val expectedAfterMigration = Enricher(
      id = "decision-table",
      service = ServiceRef(
        id = "decision-table-service",
        parameters = List(
          Parameter(ParameterName("Decision Table"), exampleDecisionTableJson),
          Parameter(ParameterName("Match condition"), "#ROW['age'] > #input.minAge && #ROW['DoB'] != null".spel),
        )
      ),
      output = "Output",
    )

    val migrated = DecisionTableParameterNamesMigration.migrateNode(metaData)(beforeMigration)

    migrated shouldBe expectedAfterMigration
  }

  private lazy val exampleDecisionTableJson = Expression.tabularDataDefinition {
    s"""{
       |  "columns": [
       |    { "name": "name", "type": "java.lang.String" },
       |    { "name": "age", "type": "java.lang.Integer" },
       |    { "name": "DoB", "type": "java.time.LocalDate" }
       |  ],
       |  "rows": [
       |    [ "John", "39", null ],
       |    [ "Lisa", "21", "2003-01-13" ],
       |    [ "Mark", "54", "1970-12-30" ]
       |  ]
       |}""".stripMargin
  }

}
