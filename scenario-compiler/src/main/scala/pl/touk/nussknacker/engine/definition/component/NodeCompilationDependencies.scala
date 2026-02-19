package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeInputValidationContext
import pl.touk.nussknacker.engine.graph.node.NodeData

final class NodeCompilationDependencies(
    scenarioCompilationDependencies: ScenarioCompilationDependencies,
    val nodeData: NodeData,
    val componentUseContext: ComponentUseContext,
    val inputValidationContext: NodeInputValidationContext
) {
  implicit def nodeId: NodeId     = nodeData.id
  implicit def metaData: MetaData = scenarioCompilationDependencies.metaData
  implicit def jobData: JobData   = scenarioCompilationDependencies.jobData
  def engineScenarioCompilationDependencies: EngineScenarioCompilationDependencies =
    scenarioCompilationDependencies.engineScenarioCompilationDependencies
}
