package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.migration.ProcessMigration

import java.util.UUID
import java.util.regex.Pattern

object NodeIdToUuidMigration extends ProcessMigration {

  override def description: String = "Migrate node IDs from human-readable names to UUIDs"

  private val UuidPattern: Pattern =
    Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

  private def isUuid(s: String): Boolean = UuidPattern.matcher(s).matches()

  override def migrateProcess(canonicalProcess: CanonicalProcess, category: String): CanonicalProcess = {
    // BranchEndData nodes have synthetic IDs ($edge-...), not real node IDs
    val realNodes = canonicalProcess.collectAllNodes.filterNot(_.isInstanceOf[BranchEndData])

    if (realNodes.forall(n => isUuid(n.id.value))) return canonicalProcess

    val idMapping: Map[String, String] = realNodes
      .filterNot(n => isUuid(n.id.value))
      .map(n => n.id.value -> UUID.randomUUID().toString)
      .toMap

    def mapId(oldId: String): String = idMapping.getOrElse(oldId, oldId)

    canonicalProcess.mapAllNodes(rewriteNodes(_, mapId))
  }

  private def rewriteNodes(nodes: List[CanonicalNode], mapId: String => String): List[CanonicalNode] =
    nodes.map(rewriteNode(_, mapId))

  private def rewriteNode(node: CanonicalNode, mapId: String => String): CanonicalNode = node match {
    case FlatNode(data) =>
      FlatNode(rewriteData(data, mapId))
    case FilterNode(data, nextFalse) =>
      FilterNode(rewriteData(data, mapId).asInstanceOf[Filter], rewriteNodes(nextFalse, mapId))
    case SwitchNode(data, nexts, defaultNext) =>
      SwitchNode(
        rewriteData(data, mapId).asInstanceOf[Switch],
        nexts.map(c => c.copy(nodes = rewriteNodes(c.nodes, mapId))),
        rewriteNodes(defaultNext, mapId)
      )
    case SplitNode(data, nexts) =>
      SplitNode(rewriteData(data, mapId).asInstanceOf[Split], nexts.map(rewriteNodes(_, mapId)))
    case Fragment(data, outputs) =>
      Fragment(
        rewriteData(data, mapId).asInstanceOf[FragmentInput],
        outputs.map { case (k, v) => k -> rewriteNodes(v, mapId) }
      )
  }

  private def rewriteData(data: NodeData, mapId: String => String): NodeData = {
    def id(n: NodeData): NodeId = NodeId(mapId(n.id.value))

    data match {
      case n: Source => n.copy(id = id(n))
      case n: Join =>
        n.copy(id = id(n), branchParameters = n.branchParameters.map(bp => bp.copy(branchId = mapId(bp.branchId))))
      case n: Filter                   => n.copy(id = id(n))
      case n: Switch                   => n.copy(id = id(n))
      case n: VariableBuilder          => n.copy(id = id(n))
      case n: Variable                 => n.copy(id = id(n))
      case n: Split                    => n.copy(id = id(n))
      case n: Enricher                 => n.copy(id = id(n))
      case n: CustomNode               => n.copy(id = id(n))
      case n: Processor                => n.copy(id = id(n))
      case n: Sink                     => n.copy(id = id(n))
      case n: FragmentInput            => n.copy(id = id(n))
      case n: FragmentUsageOutput      => n.copy(id = id(n))
      case n: FragmentInputDefinition  => n.copy(id = id(n))
      case n: FragmentOutputDefinition => n.copy(id = id(n))
      case n: BranchEndData =>
        BranchEndData(BranchEndDefinition(mapId(n.definition.id), mapId(n.definition.joinId)))
    }
  }

}
