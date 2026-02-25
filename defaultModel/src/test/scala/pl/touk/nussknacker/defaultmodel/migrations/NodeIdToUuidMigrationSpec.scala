package pl.touk.nussknacker.defaultmodel.migrations

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.io.{Source => IoSource}

class NodeIdToUuidMigrationSpec extends AnyFreeSpecLike with Matchers {
  // JSONs in tests have no 'name' fields, fallback withNameFromIdFallback fills name from id on parse

  private val UuidRegex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

  private def loadAndMigrate(resourcePath: String): CanonicalProcess = {
    val json     = IoSource.fromResource(resourcePath).mkString
    val scenario = ProcessMarshaller.fromJson(json).valueOr(err => fail(s"Failed to parse JSON: $err"))
    NodeIdToUuidMigration.migrateProcess(scenario, "category")
  }

  "NodeIdToUuidMigration" - {

    "simple scenario" - {
      lazy val migrated = loadAndMigrate("migrations/simple-scenario.json")

      "should assign a UUID to every node id" in {
        migrated.collectAllNodes.foreach { node =>
          node.id.value should fullyMatch regex UuidRegex
        }
      }

      "should assign a distinct UUID to each node" in {
        val ids = migrated.collectAllNodes.map(_.id.value)
        ids.distinct.size shouldBe ids.size
      }

      "should move old string ids into node names" in {
        migrated.collectAllNodes.map(_.name.value) should contain theSameElementsAs List(
          "source1",
          "filter1",
          "sink1"
        )
      }
    }

    "scenario with a fragment reference" - {
      lazy val migrated = loadAndMigrate("migrations/scenario-with-fragment.json")

      "should assign a UUID to every node id" in {
        migrated.collectAllNodes.foreach { node =>
          node.id.value should fullyMatch regex UuidRegex
        }
      }

      "should move old string ids into node names" in {
        migrated.collectAllNodes.map(_.name.value) should contain theSameElementsAs List(
          "source1",
          "myFragment",
          "sink1"
        )
      }
    }

    "scenario with Join (branch parameters)" - {
      lazy val migrated = loadAndMigrate("migrations/scenario-with-join.json")

      "should assign a UUID to every node id" in {
        migrated.collectAllNodes.filterNot(_.isInstanceOf[BranchEndData]).foreach { node =>
          node.id.value should fullyMatch regex UuidRegex
        }
      }

      "should move old string ids into node names" in {
        val realNodes = migrated.collectAllNodes.filterNot(_.isInstanceOf[BranchEndData])
        realNodes.map(_.name.value) should contain theSameElementsAs List("join1", "sink1", "source1", "source2")
      }

      "should update Join branchParameters branchId to new source UUIDs" in {
        val join = migrated.collectAllNodes.collectFirst { case j: Join => j }.get
        val source1NewId =
          migrated.collectAllNodes.collectFirst { case s: Source if s.name.value == "source1" => s.id.value }.get
        val source2NewId =
          migrated.collectAllNodes.collectFirst { case s: Source if s.name.value == "source2" => s.id.value }.get

        join.branchParameters.map(_.branchId) should contain theSameElementsAs List(source1NewId, source2NewId)
      }

      "should update BranchEndData definition to reference new UUIDs" in {
        val join1NewId =
          migrated.collectAllNodes.collectFirst { case j: Join if j.name.value == "join1" => j.id.value }.get
        val branchEnds = migrated.collectAllNodes.collect { case b: BranchEndData => b }

        branchEnds should have size 2
        branchEnds.foreach { branchEnd =>
          branchEnd.definition.joinId shouldBe join1NewId
          branchEnd.definition.id should fullyMatch regex UuidRegex
        }
      }
    }

  }

}
