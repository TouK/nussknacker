package pl.touk.nussknacker.defaultmodel.migrations

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, NodeName, StreamMetaData}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node.CustomNode
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

class PreviousValueParameterNamesMigrationTest extends AnyFreeSpecLike with Matchers {

  "PreviousValueParameterNamesMigration should be applied" in {
    val metaData = MetaData("test", StreamMetaData(Some(1)))
    val beforeMigration = CustomNode(
      id = NodeId("Customer Previous Value"),
      name = NodeName("Customer Previous Value"),
      outputVar = Some("previousInput"),
      nodeType = "previousValue",
      parameters = List(
        Parameter(ParameterName("groupBy"), "#input.customerId".spel),
        Parameter(ParameterName("value"), "#input".spel),
      ),
    )
    val expectedAfterMigration = CustomNode(
      id = NodeId("Customer Previous Value"),
      name = NodeName("Customer Previous Value"),
      outputVar = Some("previousInput"),
      nodeType = "previousValue",
      parameters = List(
        Parameter(ParameterName("Key"), "#input.customerId".spel),
        Parameter(ParameterName("Value"), "#input".spel),
      ),
    )

    val migrated = PreviousValueParameterNamesMigration.migrateNode(metaData)(beforeMigration)

    migrated shouldBe expectedAfterMigration
  }

}
