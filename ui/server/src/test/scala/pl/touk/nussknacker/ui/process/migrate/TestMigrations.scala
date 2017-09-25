package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.Processor
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.migration.{FlatNodeMigration, ProcessMigration, ProcessMigrations}
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.ProcessAdditionalFields

class TestMigrations(migrationsToAdd:Int*) extends ProcessMigrations {

  import pl.touk.nussknacker.engine.spel.Implicits._

  override def processMigrations: Map[Int, ProcessMigration] = Map(
    1 -> Migration1,
    2 -> Migration2,
    3 -> Migration3,
    4 -> Migration4
  ).filter(m => migrationsToAdd.contains(m._1))

  object Migration1 extends FlatNodeMigration {

    override val description = "testMigration1"

    override def failOnNewValidationError: Boolean = false

    override def migrateNode: PartialFunction[node.NodeData, node.NodeData] = {
      case n@Processor(_, ServiceRef(ProcessTestData.existingServiceId, parameters), _, _) =>
        n.copy(service = ServiceRef(ProcessTestData.otherExistingServiceId, parameters))
    }
  }

  object Migration2 extends ProcessMigration {

    override val description = "testMigration2"

    override def failOnNewValidationError: Boolean = false

    override def migrateProcess(canonicalProcess: CanonicalProcess): CanonicalProcess =
      canonicalProcess.copy(metaData = canonicalProcess.metaData.copy(typeSpecificData =
        StreamMetaData(Some(11))))
  }

  object Migration3 extends FlatNodeMigration {

    override val description = "testMigration3"

    override def failOnNewValidationError: Boolean = true

    override def migrateNode: PartialFunction[node.NodeData, node.NodeData] = {
      case n@Processor(_, ServiceRef(ProcessTestData.existingServiceId, parameters), _, _) =>
        n.copy(service = ServiceRef(ProcessTestData.existingServiceId, Parameter("newParam", "'abc'") :: parameters))
    }
  }

  object Migration4 extends FlatNodeMigration {

    override val description = "testMigration4"

    override def failOnNewValidationError: Boolean = false

    override def migrateNode: PartialFunction[node.NodeData, node.NodeData] = {
      case n@Processor(_, ServiceRef(ProcessTestData.existingServiceId, parameters), _, _) =>
        n.copy(service = ServiceRef(ProcessTestData.existingServiceId, Parameter("newParam", "'abc'") :: parameters))
    }
  }


}
