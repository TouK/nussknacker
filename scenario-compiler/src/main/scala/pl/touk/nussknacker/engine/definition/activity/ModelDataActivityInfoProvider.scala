package pl.touk.nussknacker.engine.definition.activity

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, WithActivityParameters}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, process}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.{SourceNodeData, asFragmentInputDefinition, asSource}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

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
      val nodeToActivityToParameters = collectAllSources(scenario)
        .map(source => source.id -> getActivityParameters(source, scenario.metaData))
        .toMap
      groupByActivity(nodeToActivityToParameters)
    }
  }

  private def groupByActivity(
      nodeToActivityToParameters: Map[String, Map[String, List[Parameter]]]
  ): Map[String, Map[String, List[Parameter]]] = {
    val activityToNodeToParameters = for {
      (node, activityToParams) <- nodeToActivityToParameters.toList
      (activity, params)       <- activityToParams.toList
    } yield (activity, node -> params)
    activityToNodeToParameters
      .groupBy(_._1)
      .mapValuesNow(_.map(_._2).toMap)
  }

  private def getActivityParameters(source: SourceNodeData, metaData: MetaData): Map[String, List[Parameter]] = {
    modelData.withThisAsContextClassLoader {
      val compiledSource = prepareSourceObj(source)(metaData, NodeId(source.id))
      compiledSource match {
        case Some(s: WithActivityParameters) => s.activityParametersDefinition
        case _                               => Map.empty
      }
    }
  }

  private def collectAllSources(scenario: CanonicalProcess): List[SourceNodeData] = {
    scenario.collectAllNodes.flatMap(asSource) ++ scenario.collectAllNodes.flatMap(asFragmentInputDefinition)
  }

}
