package pl.touk.nussknacker.engine.definition.activity

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, process}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.{SourceNodeData, asFragmentInputDefinition, asSource}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector

class CommonModelDataInfoProvider(modelData: ModelData) {

  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  private lazy val nodeCompiler = new NodeCompiler(
    modelData.modelDefinition,
    new FragmentParametersDefinitionExtractor(modelData.modelClassLoader.classLoader),
    expressionCompiler,
    modelData.modelClassLoader.classLoader,
    Seq.empty,
    ProductionServiceInvocationCollector,
    ComponentUseCase.TestDataGeneration,
    nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
  )

  protected def prepareSourceObj(
      source: SourceNodeData
  )(implicit metaData: MetaData, nodeId: NodeId): Option[process.Source] = {
    nodeCompiler.compileSource(source).compiledObject.toOption
  }

  protected def collectAllSources(scenario: CanonicalProcess): List[SourceNodeData] = {
    scenario.collectAllNodes.flatMap(asSource) ++ scenario.collectAllNodes.flatMap(asFragmentInputDefinition)
  }

}
