package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeCompiler, NodeDataValidator}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.CompilesTo
import pl.touk.nussknacker.engine.flink.{FlinkBaseUnboundedComponentProvider, FlinkScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.graph.node.CompilableNodeData
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.test.TestNodeCompiler

class FlinkNodeCompiler(
    flinkMiniClusterWithServices: Option[FlinkMiniClusterWithServices],
    underlyingNodeCompiler: NodeCompiler
) extends TestNodeCompiler {

  override def compileNode[NodeData <: CompilableNodeData](
      nodeData: NodeData,
      variableTypes: Map[String, typing.TypingResult],
      branchVariableTypes: Option[Map[String, Map[String, typing.TypingResult]]],
      outgoingEdges: List[NodeDataValidator.OutgoingEdge]
  )(implicit compilesTo: CompilesTo[NodeData]): NodeCompiler.NodeCompilationResult[compilesTo.ReturnType] = {
    // TODO: configurable
    val dummyJobData = JobData(
      MetaData("dummy", StreamMetaData()),
      ProcessVersion.empty
    )
    flinkMiniClusterWithServices
      .map(
        _.withDetachedStreamExecutionEnvironment { env =>
          val engineScenarioCompilationDependencies = new FlinkScenarioCompilationDependencies(env)
          implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
            new ScenarioCompilationDependencies(dummyJobData, engineScenarioCompilationDependencies)
          underlyingNodeCompiler.compileNode(nodeData, variableTypes, branchVariableTypes, outgoingEdges)
        }
      )
      .getOrElse {
        implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
          new ScenarioCompilationDependencies(dummyJobData, EngineScenarioCompilationDependencies.empty)
        underlyingNodeCompiler.compileNode(nodeData, variableTypes, branchVariableTypes, outgoingEdges)
      }
  }

}

object FlinkNodeCompiler {

  implicit class FlinkNodeCompilerExt(nodeCompiler: TestNodeCompiler.type) {

    def flinkBased(modelConfig: Config) =
      FlinkNodeCompilerBuilder(modelConfig = modelConfig, flinkMiniClusterWithServices = None, components = List.empty)

  }

}

final case class FlinkNodeCompilerBuilder(
    modelConfig: Config,
    flinkMiniClusterWithServices: Option[FlinkMiniClusterWithServices],
    components: List[ComponentDefinition]
) {

  def withExtraComponents(components: List[ComponentDefinition]): FlinkNodeCompilerBuilder = {
    copy(components = components)
  }

  def withFlinkMiniCluster(flinkMiniClusterWithServices: FlinkMiniClusterWithServices): FlinkNodeCompilerBuilder = {
    copy(flinkMiniClusterWithServices = Some(flinkMiniClusterWithServices))
  }

  def build(): FlinkNodeCompiler = {
    val modelDataWithExtraComponents = LocalModelData(
      inputConfig = modelConfig,
      components =
        components ::: FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
      configCreator = new DefaultConfigCreator
    )

    val underlyingNodeCompiler = NodeCompiler(modelDataWithExtraComponents)
    new FlinkNodeCompiler(flinkMiniClusterWithServices, underlyingNodeCompiler)
  }

}
