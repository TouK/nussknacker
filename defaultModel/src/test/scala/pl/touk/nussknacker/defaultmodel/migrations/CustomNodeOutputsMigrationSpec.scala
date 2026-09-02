package pl.touk.nussknacker.defaultmodel.migrations

import cats.data.NonEmptyList
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.DefaultModelMigrations
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node.{
  BranchEndData,
  BranchEndDefinition,
  CustomNode,
  Filter,
  FragmentInput,
  Sink,
  Source,
  Split,
  Switch
}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.io.{Source => IoSource}
import scala.util.Using

class CustomNodeOutputsMigrationSpec extends AnyFreeSpecLike with Matchers {

  private val category  = "category"
  private val migration = CustomNodeOutputsMigration(nodeType = "deduplication", mainOutputName = "passed")

  private def source(id: String)      = FlatNode(Source(id, SourceRef("kafka", List.empty)))
  private def sink(id: String)        = FlatNode(Sink(id, SinkRef("kafka", List.empty)))
  private def filterData(id: String)  = Filter(id, Expression.spel("true"))
  private def dedupData(id: String)   = CustomNode(id, None, "deduplication", List.empty)
  private def otherCustom(id: String) = CustomNode(id, None, "otherTransformer", List.empty)

  private def scenario(nodes: List[CanonicalNode], additionalBranches: List[List[CanonicalNode]] = List.empty) =
    CanonicalProcess(MetaData("proc1", StreamMetaData()), nodes, additionalBranches)

  private def migrate(process: CanonicalProcess): CanonicalProcess = migration.migrateProcess(process, category)

  private def passedOutput(nodes: List[CanonicalNode]) = NonEmptyList.of(Output("passed", nodes))

  "CustomNodeOutputsMigration" - {

    "wraps the unnamed continuation as the named main output" in {
      val old = scenario(
        List(source("source1"), FlatNode(dedupData("dedup1")), FlatNode(filterData("filter1")), sink("sink1"))
      )
      val expected = scenario(
        List(
          source("source1"),
          CustomNodeWithOutputs(
            dedupData("dedup1"),
            passedOutput(List(FlatNode(filterData("filter1")), sink("sink1")))
          )
        )
      )
      migrate(old) shouldBe expected
    }

    "leaves an ending node in the flat shape" in {
      val old = scenario(List(source("source1"), FlatNode(dedupData("dedup1"))))
      migrate(old) shouldBe old
    }

    "leaves other custom nodes untouched" in {
      val old = scenario(List(source("source1"), FlatNode(otherCustom("custom1")), sink("sink1")))
      migrate(old) shouldBe old
    }

    "wraps a node nested inside switch and split branches" in {
      val switchData = Switch("switch1", None, None)
      val splitData  = Split("split1")
      def old(inner: List[CanonicalNode], innerSwitch: List[CanonicalNode]) = scenario(
        List(
          source("source1"),
          SplitNode(
            splitData,
            List(
              inner,
              List(SwitchNode(switchData, List(Case(Expression.spel("true"), innerSwitch)), defaultNext = List.empty))
            )
          )
        )
      )
      val expected = old(
        inner = List(CustomNodeWithOutputs(dedupData("dedup1"), passedOutput(List(sink("sink1"))))),
        innerSwitch = List(CustomNodeWithOutputs(dedupData("dedup2"), passedOutput(List(sink("sink2")))))
      )
      migrate(
        old(
          inner = List(FlatNode(dedupData("dedup1")), sink("sink1")),
          innerSwitch = List(FlatNode(dedupData("dedup2")), sink("sink2"))
        )
      ) shouldBe expected
    }

    "wraps a node nested inside a filter's false branch" in {
      val old = scenario(
        List(
          source("source1"),
          FilterNode(filterData("filter1"), List(FlatNode(dedupData("dedup1")), sink("sinkFalse"))),
          sink("sink1")
        )
      )
      val expected = scenario(
        List(
          source("source1"),
          FilterNode(
            filterData("filter1"),
            List(CustomNodeWithOutputs(dedupData("dedup1"), passedOutput(List(sink("sinkFalse")))))
          ),
          sink("sink1")
        )
      )
      migrate(old) shouldBe expected
    }

    "wraps a chain of two nodes one inside the other" in {
      val old = scenario(
        List(source("source1"), FlatNode(dedupData("dedup1")), FlatNode(dedupData("dedup2")), sink("sink1"))
      )
      val expected = scenario(
        List(
          source("source1"),
          CustomNodeWithOutputs(
            dedupData("dedup1"),
            passedOutput(List(CustomNodeWithOutputs(dedupData("dedup2"), passedOutput(List(sink("sink1"))))))
          )
        )
      )
      migrate(old) shouldBe expected
    }

    "wraps a node flowing into a join and migrates additional branches" in {
      val branchEnd = FlatNode(BranchEndData(BranchEndDefinition("dedup1", "join1")))
      val old = scenario(
        List(source("source1"), FlatNode(dedupData("dedup1")), branchEnd),
        additionalBranches = List(List(source("source2"), FlatNode(dedupData("dedup2")), sink("sink2")))
      )
      val expected = scenario(
        List(source("source1"), CustomNodeWithOutputs(dedupData("dedup1"), passedOutput(List(branchEnd)))),
        additionalBranches = List(
          List(source("source2"), CustomNodeWithOutputs(dedupData("dedup2"), passedOutput(List(sink("sink2")))))
        )
      )
      migrate(old) shouldBe expected
    }

    "wraps a node behind a fragment output" in {
      val fragmentData = FragmentInput("frag1", FragmentRef("myFragment", List.empty))
      val old = scenario(
        List(
          source("source1"),
          Fragment(fragmentData, Map("output1" -> List(FlatNode(dedupData("dedup1")), sink("sink1"))))
        )
      )
      val expected = scenario(
        List(
          source("source1"),
          Fragment(
            fragmentData,
            Map("output1" -> List(CustomNodeWithOutputs(dedupData("dedup1"), passedOutput(List(sink("sink1"))))))
          )
        )
      )
      migrate(old) shouldBe expected
    }

    "recurses into already named outputs without rewrapping them" in {
      val old = scenario(
        List(
          source("source1"),
          CustomNodeWithOutputs(
            dedupData("dedup1"),
            NonEmptyList.of(
              Output("passed", List(FlatNode(dedupData("dedup2")), sink("sink1"))),
              Output("rejected", List(sink("rejectedSink")))
            )
          )
        )
      )
      val expected = scenario(
        List(
          source("source1"),
          CustomNodeWithOutputs(
            dedupData("dedup1"),
            NonEmptyList.of(
              Output("passed", List(CustomNodeWithOutputs(dedupData("dedup2"), passedOutput(List(sink("sink1")))))),
              Output("rejected", List(sink("rejectedSink")))
            )
          )
        )
      )
      migrate(old) shouldBe expected
    }

    "is idempotent" in {
      val old = scenario(
        List(source("source1"), FlatNode(dedupData("dedup1")), FlatNode(dedupData("dedup2")), sink("sink1"))
      )
      val once = migrate(old)
      migrate(once) shouldBe once
    }

    "is registered in DefaultModelMigrations under 400" in {
      new DefaultModelMigrations().processMigrations(400) shouldBe migration
    }

    "scenario with an unnamed output loaded from JSON" - {
      def loadScenario(resourcePath: String): CanonicalProcess = {
        val json = Using.resource(IoSource.fromResource(resourcePath))(_.mkString)
        ProcessMarshaller.fromJson(json).valueOr(err => fail(s"Failed to parse JSON: $err"))
      }

      lazy val migrated = migrate(loadScenario("migrations/deduplication-with-unnamed-output.json"))

      "rewires the node to the named main output" in {
        val custom = migrated.nodes.collectFirst { case c: CustomNodeWithOutputs => c }.get
        custom.data.nodeType shouldBe "deduplication"
        custom.outputs.map(_.name).toList shouldBe List("passed")
        custom.outputs.head.nodes.map(_.id) shouldBe List("sink1")
      }

      "is idempotent on the loaded scenario" in {
        migrate(migrated) shouldBe migrated
      }
    }

  }

}
