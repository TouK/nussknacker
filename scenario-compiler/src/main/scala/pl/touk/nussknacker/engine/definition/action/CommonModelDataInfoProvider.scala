package pl.touk.nussknacker.engine.definition.action

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.{ComponentUseCase, ModelData}
import pl.touk.nussknacker.engine.api.{JobData, NodeId, ServiceInvoker}
import pl.touk.nussknacker.engine.api.component.{NodeComponentInfo, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{asFragmentInputDefinition, asSource, NodeData, SourceNodeData}
import pl.touk.nussknacker.engine.node.NodeComponentInfoExtractor
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

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
    ComponentUseCase.TestDataGeneration,
    NodesDeploymentData.empty,
    nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
  )

  def collectAllSources(scenario: CanonicalProcess): List[SourceNodeData] = {
    scenario.collectAllNodes.flatMap(asSource) ++ scenario.collectAllNodes.flatMap(asFragmentInputDefinition)
  }

  def compileSourceNode(
      source: SourceNodeData
  )(implicit jobData: JobData, nodeId: NodeId): ValidatedNel[ProcessCompilationError, Source] = {
    nodeCompiler.compileSource(source).compiledObject
  }

  def collectAndCompileAllSourcesAndServices(
      scenario: CanonicalProcess
  )(implicit jobData: JobData): Map[NodeComponentInfo, Any] = {
    compileSourcesAndService(scenario.collectAllNodes) ++
      compileSourcesAndService(scenario.collectAllNodes.flatMap(asFragmentInputDefinition))
  }

  private def compileSourcesAndService(nodes: List[NodeData])(implicit jobData: JobData): Map[NodeComponentInfo, Any] =
    nodes
      .map { n =>
        NodeComponentInfoExtractor.fromScenarioNode(n) -> n
      }
      .toMap
      .mapValuesNow {
        case source: node.Source       => compileSourceNode(source)(jobData, NodeId(source.id)).toOption
        case enricher: node.Enricher   => compileEnricher(enricher)(jobData, NodeId(enricher.id)).toOption
        case processor: node.Processor => compileProcessor(processor)(jobData, NodeId(processor.id)).toOption
        case _                         => None
      }
      .collect { case (k, Some(v)) =>
        k -> v
      }

  private def compileEnricher(
      enricher: node.Enricher
  )(implicit jobData: JobData, nodeId: NodeId): ValidatedNel[ProcessCompilationError, ServiceInvoker] =
    nodeCompiler
      .compileEnricher(enricher, ValidationContext.empty, OutputVar.enricher("output_var"))
      .compiledObject
      .map(_.invoker)

  private def compileProcessor(
      processor: node.Processor
  )(implicit jobData: JobData, nodeId: NodeId): ValidatedNel[ProcessCompilationError, ServiceInvoker] =
    nodeCompiler
      .compileProcessor(processor, ValidationContext.empty)
      .compiledObject
      .map(_.invoker)

}
