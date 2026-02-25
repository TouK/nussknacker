package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.migration.ProcessMigration

import java.util.UUID

object NodeIdToUuidMigration extends ProcessMigration {

  override def description: String = "Migrate node IDs to node name, introduce nodeId as static uuid"

  override def migrateProcess(canonicalProcess: CanonicalProcess, category: String): CanonicalProcess = {
    // BranchEndData nodes have synthetic IDs ($edge-...), not real node IDs — skip them
    val idMapping: Map[String, String] = canonicalProcess.collectAllNodes
      .filterNot(_.isInstanceOf[BranchEndData])
      .map(n => n.id.value -> UUID.randomUUID().toString)
      .toMap

    def mapId(oldId: String): String = idMapping.getOrElse(oldId, oldId)

    canonicalProcess.mapAllNodes(rewriteNodes(_, mapId))
  }

  private def rewriteNodes(nodes: List[CanonicalNode], mapId: String => String): List[CanonicalNode] =
    nodes.map(rewriteNode(_, mapId))

  private def rewriteNode(node: CanonicalNode, mapId: String => String): CanonicalNode = node match {
    case canonicalnode.FlatNode(data) =>
      canonicalnode.FlatNode(rewriteData(data, mapId))
    case canonicalnode.FilterNode(data, nextFalse) =>
      canonicalnode.FilterNode(rewriteData(data, mapId).asInstanceOf[Filter], rewriteNodes(nextFalse, mapId))
    case canonicalnode.SwitchNode(data, nexts, defaultNext) =>
      canonicalnode.SwitchNode(
        rewriteData(data, mapId).asInstanceOf[Switch],
        nexts.map(c => c.copy(nodes = rewriteNodes(c.nodes, mapId))),
        rewriteNodes(defaultNext, mapId)
      )
    case canonicalnode.SplitNode(data, nexts) =>
      canonicalnode.SplitNode(rewriteData(data, mapId).asInstanceOf[Split], nexts.map(rewriteNodes(_, mapId)))
    case canonicalnode.Fragment(data, outputs) =>
      canonicalnode.Fragment(
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
