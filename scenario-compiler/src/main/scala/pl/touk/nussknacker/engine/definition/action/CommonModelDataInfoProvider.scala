package pl.touk.nussknacker.engine.definition.action

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.{PartSubGraphCompiler, ProcessCompiler}
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeCompiler, SingleInputNodeInputValidationContext}
import pl.touk.nussknacker.engine.compiledgraph.{node => compiledNode, CompiledNodesCollector}
import pl.touk.nussknacker.engine.compiledgraph.part.{CustomNodePart, ProcessPart, SinkPart, SourcePart}
import pl.touk.nussknacker.engine.node.NodeComponentInfoExtractor
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

class CommonModelDataInfoProvider(modelData: ModelData) {

  lazy val nodeCompiler: NodeCompiler = NodeCompiler(modelData).withLabelsDictTyper

  private lazy val subGraphCompiler = new PartSubGraphCompiler(nodeCompiler)

  private lazy val scenarioCompiler = new ProcessCompiler(
    classLoader = modelData.modelClassLoader,
    sub = subGraphCompiler,
    globalVariablesPreparer =
      GlobalVariablesPreparer(modelData.modelDefinitionWithClasses.modelDefinition.expressionConfig),
    nodeCompiler = nodeCompiler,
    customProcessValidator = modelData.customProcessValidator,
    allowEndingScenarioWithoutSink = modelData.modelDefinition.allowEndingScenarioWithoutSink
  )

  def compileAllCustomNodes(
      scenario: CanonicalProcess
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): ValidatedNel[ProcessCompilationError, Map[NodeComponentInfo, Any]] =
    // We have to wrap this block with model's class loader because it invokes node compilation
    modelData.withModelClassloaderAsContextClassLoader {
      scenarioCompiler
        .compile(scenario)
        .result
        .map(compiledParts => extractCustomComponents(compiledParts.sources.toList))
    }

  private def extractCustomComponents(
      parts: List[ProcessPart]
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): Map[NodeComponentInfo, Any] =
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
            custom.outputs.tail.flatMap(compiledOutput =>
              compileNodeAllCustomNodes(compiledOutput.node, custom.validationContext)
            ) ++
            extractCustomComponents(custom.nextParts)
        case sink: SinkPart =>
          acc + (nodeComponentInfo -> sink.obj)
      }
    }

  private def compilePartsAllCustomNodes(
      part: ProcessPart
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): Map[NodeComponentInfo, Any] =
    compileNodeAllCustomNodes(part.node, part.validationContext)

  private def compileNodeAllCustomNodes(
      node: SplittedNode[_],
      validationContext: ValidationContext
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): Map[NodeComponentInfo, Any] =
    subGraphCompiler
      .compile(node, SingleInputNodeInputValidationContext(validationContext))
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
