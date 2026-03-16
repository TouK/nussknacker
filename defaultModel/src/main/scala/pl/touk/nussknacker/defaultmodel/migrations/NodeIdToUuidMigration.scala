package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.migration.ProcessMigration

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.util.Try

object NodeIdToUuidMigration extends ProcessMigration {

  override def description: String = "Migrate node IDs to node name, introduce nodeId as static uuid"

  override def migrateProcess(canonicalProcess: CanonicalProcess, category: String): CanonicalProcess = {
    val realNodes    = canonicalProcess.collectAllNodes.filterNot(_.isInstanceOf[BranchEndData])
    val migratedById = realNodes.map(node => node.id.value -> migratedNodeId(node.id.value)).toMap
    val idMapping = realNodes.foldLeft(migratedById) { case (acc, node) =>
      // In legacy scenarios decoded without `name`, `name` is filled from old id while `id` can already be UUID.
      // Keep id-mapping authoritative and add name aliases to resolve join/branch references.
      if (acc.contains(node.name.value)) acc else acc + (node.name.value -> migratedById(node.id.value))
    }

    def mapId(oldId: String): String = idMapping.getOrElse(oldId, oldId)

    canonicalProcess.mapAllNodes(rewriteNodes(_, mapId, migratedNodeId))
  }

  private def rewriteNodes(
      nodes: List[CanonicalNode],
      mapId: String => String,
      migratedNodeId: String => String
  ): List[CanonicalNode] =
    nodes.map(rewriteNode(_, mapId, migratedNodeId))

  private def rewriteNode(
      node: CanonicalNode,
      mapId: String => String,
      migratedNodeId: String => String
  ): CanonicalNode = {
    def id(n: NodeData): NodeId = NodeId(migratedNodeId(n.id.value))

    node match {
      case canonicalnode.FlatNode(data) =>
        val newData = data match {
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
        canonicalnode.FlatNode(newData)
      case canonicalnode.FilterNode(data, nextFalse) =>
        canonicalnode.FilterNode(data.copy(id = id(data)), rewriteNodes(nextFalse, mapId, migratedNodeId))
      case canonicalnode.SwitchNode(data, nexts, defaultNext) =>
        canonicalnode.SwitchNode(
          data.copy(id = id(data)),
          nexts.map(c => c.copy(nodes = rewriteNodes(c.nodes, mapId, migratedNodeId))),
          rewriteNodes(defaultNext, mapId, migratedNodeId)
        )
      case canonicalnode.SplitNode(data, nexts) =>
        canonicalnode.SplitNode(data.copy(id = id(data)), nexts.map(rewriteNodes(_, mapId, migratedNodeId)))
      case canonicalnode.Fragment(data, outputs) =>
        canonicalnode.Fragment(
          data.copy(id = id(data)),
          outputs.map { case (k, v) => k -> rewriteNodes(v, mapId, migratedNodeId) }
        )
    }
  }

  private def migratedNodeId(id: String): String =
    if (isUuid(id)) id else UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString

  private def isUuid(value: String): Boolean =
    Try(UUID.fromString(value)).isSuccess

}
