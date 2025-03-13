package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{CustomNode, Enricher, Join, Processor, Sink, Source, WithParameters}

class ParametersMigration(
    private val predicate: List[Parameter] => Boolean,
    private val mapping: Parameter => Parameter
) {

  def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = {
    case node: WithParameters if predicate(node.parameters) =>
      node match {
        case n: CustomNode => n.copy(parameters = n.parameters.map(mapping))
        case n: Enricher   => n.copy(service = n.service.copy(parameters = n.service.parameters.map(mapping)))
        case n: Join =>
          n.copy(
            parameters = n.parameters.map(mapping),
            branchParameters = n.branchParameters.map(p => p.copy(parameters = p.parameters.map(mapping)))
          )
        case n: Processor =>
          n.copy(service = n.service.copy(parameters = n.service.parameters.map(mapping)))
        case n: Sink   => n.copy(ref = n.ref.copy(parameters = n.ref.parameters.map(mapping)))
        case n: Source => n.copy(ref = n.ref.copy(parameters = n.ref.parameters.map(mapping)))
      }
  }

}
