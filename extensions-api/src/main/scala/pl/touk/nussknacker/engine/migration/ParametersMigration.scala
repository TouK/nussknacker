package pl.touk.nussknacker.engine.migration

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{CustomNode, Enricher, Join, Processor, Sink, Source, WithParameters}

class ParametersMigration(
    private val mapping: PartialFunction[Parameter, Parameter]
) {

  def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = {
    case n: CustomNode if shouldApplyMappingToParams(n) =>
      n.copy(parameters = n.parameters.map(applyMappingOrSelf))
    case n: Enricher if shouldApplyMappingToParams(n) =>
      n.copy(service = n.service.copy(parameters = n.service.parameters.map(applyMappingOrSelf)))
    case n: Join if shouldApplyMappingToParams(n) || shouldApplyMappingToBranchParams(n.branchParameters) =>
      n.copy(
        parameters = n.parameters.map(applyMappingOrSelf),
        branchParameters = n.branchParameters.map(p => p.copy(parameters = p.parameters.map(applyMappingOrSelf)))
      )
    case n: Processor if shouldApplyMappingToParams(n) =>
      n.copy(service = n.service.copy(parameters = n.service.parameters.map(applyMappingOrSelf)))
    case n: Sink if shouldApplyMappingToParams(n) =>
      n.copy(ref = n.ref.copy(parameters = n.ref.parameters.map(applyMappingOrSelf)))
    case n: Source if shouldApplyMappingToParams(n) =>
      n.copy(ref = n.ref.copy(parameters = n.ref.parameters.map(applyMappingOrSelf)))
  }

  private def shouldApplyMappingToParams(withParameters: WithParameters): Boolean =
    withParameters.parameters.exists(mapping.isDefinedAt)

  private def shouldApplyMappingToBranchParams(branchParameters: List[BranchParameters]): Boolean =
    branchParameters.exists(_.parameters.exists(mapping.isDefinedAt))

  private def applyMappingOrSelf(parameter: Parameter): Parameter =
    mapping.applyOrElse(parameter, identity[Parameter])
}
