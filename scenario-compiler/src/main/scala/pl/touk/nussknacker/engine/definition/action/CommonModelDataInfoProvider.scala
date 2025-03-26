package pl.touk.nussknacker.engine.definition.action

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{NodeComponentInfo, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, PartSubGraphCompiler, ProcessCompiler}
import pl.touk.nussknacker.engine.compiledgraph.part.{CustomNodePart, ProcessPart, SinkPart, SourcePart}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledNodesCollector, node => compiledNode}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.{SourceNodeData, asFragmentInputDefinition, asSource}
import pl.touk.nussknacker.engine.node.NodeComponentInfoExtractor
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.{JobRuntimeData, ModelData, RuntimeMode}

class CommonModelDataInfoProvider(modelData: ModelData) {

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  private lazy val nodeCompiler = new NodeCompiler(
    modelData.modelDefinition,
    new FragmentParametersDefinitionExtractor(
      modelData.modelClassLoader,
      modelData.modelDefinitionWithClasses.classDefinitions,
    ),
    expressionCompiler,
    modelData.modelClassLoader,
    Seq.empty,
    ProductionServiceInvocationCollector,
    RuntimeMode.Live,
    NodesDeploymentData.empty,
    nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
  )

  private lazy val subGraphCompiler = new PartSubGraphCompiler(nodeCompiler)

  private lazy val scenarioCompiler = new ProcessCompiler(
    modelData.modelClassLoader,
    subGraphCompiler,
    GlobalVariablesPreparer(modelData.modelDefinitionWithClasses.modelDefinition.expressionConfig),
    nodeCompiler,
    modelData.customProcessValidator
  )

  def collectAllSources(scenario: CanonicalProcess): List[SourceNodeData] = {
    scenario.collectAllNodes.flatMap(asSource) ++ scenario.collectAllNodes.flatMap(asFragmentInputDefinition)
  }

  def compileSourceNode(
      source: SourceNodeData
  )(implicit jobRuntimeData: JobRuntimeData, nodeId: NodeId): ValidatedNel[ProcessCompilationError, Source] = {
    // We have to wrap this block with model's class loader because it invokes node compilation
    modelData.withThisAsContextClassLoader {
      nodeCompiler.compileSource(source).compiledObject
    }
  }

  def compileAllCustomNodes(
      scenario: CanonicalProcess
  )(implicit jobRuntimeData: JobRuntimeData): ValidatedNel[ProcessCompilationError, Map[NodeComponentInfo, Any]] =
    // We have to wrap this block with model's class loader because it invokes node compilation
    modelData.withThisAsContextClassLoader {
      scenarioCompiler
        .compile(scenario)
        .result
        .map(compiledParts => extractCustomComponents(compiledParts.sources.toList))
    }

  private def extractCustomComponents(
      parts: List[ProcessPart]
  )(implicit jobRuntimeData: JobRuntimeData): Map[NodeComponentInfo, Any] =
    parts.foldLeft(Map.empty[NodeComponentInfo, Any]) { (acc, part) =>
      val nodeComponentInfo = NodeComponentInfoExtractor.fromScenarioNode(part.node.data)
      part match {
        case source: SourcePart =>
          acc + (nodeComponentInfo -> source.obj) ++
            compilePartsAllCustomNodes(source) ++
            extractCustomComponents(source.nextParts)
        case custom: CustomNodePart =>
          acc + (nodeComponentInfo -> custom.transformer) ++
            compilePartsAllCustomNodes(custom) ++
            extractCustomComponents(custom.nextParts)
        case sink: SinkPart =>
          acc + (nodeComponentInfo -> sink.obj)
      }
    }

  private def compilePartsAllCustomNodes(part: ProcessPart)(implicit jobRuntimeData: JobRuntimeData): Map[NodeComponentInfo, Any] =
    subGraphCompiler
      .compile(part.node, part.validationContext)
      .result
      .toList
      .flatMap(n => CompiledNodesCollector.collectAllNodes(n))
      .flatMap {
        case n: compiledNode.Processor => Some(NodeComponentInfoExtractor.fromCompiledNode(n) -> n.service.invoker)
        case n: compiledNode.Enricher  => Some(NodeComponentInfoExtractor.fromCompiledNode(n) -> n.service.invoker)
        case n: compiledNode.EndingProcessor =>
          Some(NodeComponentInfoExtractor.fromCompiledNode(n) -> n.service.invoker)
        case _ => None
      }
      .toMap

}
