package pl.touk.nussknacker.defaultmodel.migrations

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.io.{Source => IoSource}
import scala.util.Using

class NodeIdToUuidMigrationSpec extends AnyFreeSpecLike with Matchers {

  private def uuidOf(s: String) = UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8)).toString

  private def loadScenario(resourcePath: String): CanonicalProcess = {
    val json = Using.resource(IoSource.fromResource(resourcePath))(_.mkString)
    ProcessMarshaller.fromJson(json).valueOr(err => fail(s"Failed to parse JSON: $err"))
  }

  private def loadAndMigrate(resourcePath: String): CanonicalProcess =
    NodeIdToUuidMigration.migrateProcess(loadScenario(resourcePath), "category")

  private def migrate(process: CanonicalProcess): CanonicalProcess =
    NodeIdToUuidMigration.migrateProcess(process, "category")

  private def assertIdempotent(resourcePath: String): Unit = {
    val firstMigration  = loadAndMigrate(resourcePath)
    val secondMigration = migrate(firstMigration)
    secondMigration shouldBe firstMigration
  }

  "NodeIdToUuidMigration" - {

    "simple scenario" - {
      lazy val migrated = loadAndMigrate("migrations/simple-scenario.json")

      "should assign a deterministic UUID to every node id" in {
        migrated.collectAllNodes.foreach { node =>
          node.id.value shouldBe uuidOf(node.name.value)
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

      "should be idempotent" in {
        assertIdempotent("migrations/simple-scenario.json")
      }
    }

    "scenario with a fragment reference" - {
      lazy val migrated = loadAndMigrate("migrations/scenario-with-fragment.json")

      "should assign a deterministic UUID to every node id" in {
        migrated.collectAllNodes.foreach { node =>
          node.id.value shouldBe uuidOf(node.name.value)
        }
      }

      "should move old string ids into node names" in {
        migrated.collectAllNodes.map(_.name.value) should contain theSameElementsAs List(
          "source1",
          "myFragment",
          "sink1"
        )
      }

      "should be idempotent" in {
        assertIdempotent("migrations/scenario-with-fragment.json")
      }
    }

    "scenario with Join (branch parameters)" - {
      lazy val migrated = loadAndMigrate("migrations/scenario-with-join.json")

      "should assign a deterministic UUID to every node id" in {
        migrated.collectAllNodes.filterNot(_.isInstanceOf[BranchEndData]).foreach { node =>
          node.id.value shouldBe uuidOf(node.name.value)
        }
      }

      "should move old string ids into node names" in {
        val realNodes = migrated.collectAllNodes.filterNot(_.isInstanceOf[BranchEndData])
        realNodes.map(_.name.value) should contain theSameElementsAs List("join1", "source1", "source2")
      }

      "should update Join branchParameters branchId to new source UUIDs" in {
        val join = migrated.collectAllNodes.collectFirst { case j: Join => j }.get

        join.branchParameters.map(_.branchId) should contain theSameElementsAs List(
          uuidOf("source1"),
          uuidOf("source2")
        )
      }

      "should update BranchEndData definition to reference new UUIDs" in {
        val branchEnds = migrated.collectAllNodes.collect { case b: BranchEndData => b }

        branchEnds should have size 2
        branchEnds.foreach { branchEnd =>
          branchEnd.definition.joinId shouldBe uuidOf("join1")
          branchEnd.definition.id should (equal(uuidOf("source1")) or equal(uuidOf("source2")))
        }
      }

      "should be idempotent" in {
        assertIdempotent("migrations/scenario-with-join.json")
      }
    }

  }

}
