package pl.touk.nussknacker.engine.definition.activity

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, WithActivityParameters}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, process}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.{SourceNodeData, asSource}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import shapeless.syntax.typeable._

class ModelDataActivityInfoProvider(modelData: ModelData) extends ActivityInfoProvider {

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

  private def prepareSourceObj(
      source: SourceNodeData
  )(implicit metaData: MetaData, nodeId: NodeId): Option[process.Source] = {
    nodeCompiler.compileSource(source).compiledObject.toOption
  }

  override def getActivityParameters(scenario: CanonicalProcess): Map[String, Map[String, List[Parameter]]] = {
    modelData.withThisAsContextClassLoader {
      val asdf = scenario.collectAllNodes.flatMap(asSource)
      val compiledSources = for {
        source    <- scenario.collectAllNodes.flatMap(asSource)
        sourceObj <- prepareSourceObj(source)(scenario.metaData, NodeId(source.id))
      } yield sourceObj
      val stefan = compiledSources
        .flatMap(_.cast[WithActivityParameters])
        .map(_.activityParametersDefinition)

      Map.empty
    }
  }

}
