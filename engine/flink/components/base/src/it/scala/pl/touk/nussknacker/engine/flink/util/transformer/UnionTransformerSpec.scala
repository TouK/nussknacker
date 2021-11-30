package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.modelconfig.DefaultModelConfigLoader
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.BaseSampleConfigCreator
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.util

class UnionTransformerSpec extends FunSuite with BeforeAndAfterAll with Matchers with FlinkSpec with EitherValues with LazyLogging with VeryPatientScalaFutures {

  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  private val BranchFooId = "foo"

  private val BranchBarId = "bar"

  private val UnionNodeId = "joined-node-id"

  private val OutVariableName = "outVar"

  test("should unify streams with union-memo") {
    val scenario = EspProcess(MetaData("sample-union-memo", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("start-foo", "source")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "noopSource")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .branch(UnionNodeId, "union-memo", Some(OutVariableName),
          List(
            BranchFooId -> List(
              "key" -> "'fooKey'",
              "value" -> "#input"
            ),
            BranchBarId -> List(
              "key" -> "'fooKey'",
              "value" -> "#input"
            )
          ), "stateTimeout" -> "T(java.time.Duration).parse('PT1M')"
        )
        .processorEnd("end", "mockService", "all" -> s"#$OutVariableName.$BranchFooId")
    ))

    val data = List("10")
    run(scenario, data)
    MockService.data shouldBe data
  }

  test("should unify streams with union") {
    val scenario = EspProcess(MetaData("sample-union", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("start-foo", "source")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "noopSource")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .branch(UnionNodeId, "union", Some(OutVariableName),
          List(
            BranchFooId -> List(
              "value" -> "{a: '#input'}"
            ),
            BranchBarId -> List(
              "value" -> "{a: '123'}"
            )
          )
        )
        .processorEnd("end", "mockService", "all" -> s"#$OutVariableName.a")
    ))

    val data = List("10")
    run(scenario, data)
    MockService.data shouldBe data
  }

  def run(process: EspProcess, data: List[String]): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    val finalConfig = ConfigFactory.load().withValue("components.base", ConfigValueFactory.fromMap(new util.HashMap[String, AnyRef]()))
    val resolvedConfig = new DefaultModelConfigLoader().resolveInputConfigDuringExecution(finalConfig, getClass.getClassLoader).config
    val modelData = LocalModelData(resolvedConfig, new BaseSampleConfigCreator(data))
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.executeAndWaitForFinished(process.id)()
  }

}
