package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeCompiler, OutgoingEdge}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.CompilesTo
import pl.touk.nussknacker.engine.graph.node.CompilableNodeData
import pl.touk.nussknacker.engine.lite.components.LiteBaseComponentProvider
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.test.TestNodeCompiler

class LiteNodeCompiler(
    underlyingNodeCompiler: NodeCompiler
) extends TestNodeCompiler {

  override def compileNode[NodeData <: CompilableNodeData](
      nodeData: NodeData,
      variableTypes: Map[String, typing.TypingResult],
      branchVariableTypes: Option[Map[String, Map[String, typing.TypingResult]]],
      outgoingEdges: List[OutgoingEdge]
  )(implicit compilesTo: CompilesTo[NodeData]): NodeCompiler.NodeCompilationResult[compilesTo.ReturnType] = {
    // TODO: configurable
    val dummyJobData = JobData(
      MetaData("dummy", StreamMetaData()),
      ProcessVersion.empty
    )
    implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
      new ScenarioCompilationDependencies(dummyJobData, EngineScenarioCompilationDependencies.empty)
    underlyingNodeCompiler.compileNode(nodeData, variableTypes, branchVariableTypes, outgoingEdges)
  }

}

object LiteNodeCompiler {

  implicit class LiteNodeCompilerExt(nodeCompiler: TestNodeCompiler.type) {

    def liteBased(modelConfig: Config = ConfigFactory.load()) =
      LiteNodeCompilerBuilder(modelConfig = modelConfig, components = List.empty)

  }

}

final case class LiteNodeCompilerBuilder(
    modelConfig: Config,
    components: List[ComponentDefinition]
) {

  def withExtraComponents(components: List[ComponentDefinition]): LiteNodeCompilerBuilder = {
    copy(components = components)
  }

  def build(): LiteNodeCompiler = {
    val modelDataWithExtraComponents = LocalModelData(
      inputConfig = modelConfig,
      components = components ::: LiteBaseComponentProvider.Components,
      configCreator = new DefaultConfigCreator
    )

    val underlyingNodeCompiler = NodeCompiler(modelDataWithExtraComponents)
    new LiteNodeCompiler(underlyingNodeCompiler)
  }

}
