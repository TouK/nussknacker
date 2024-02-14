package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.BlockingQueueSource
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.time.Duration
import java.{util => jul}
import scala.jdk.CollectionConverters._

class UnionWithMemoTransformerSpec extends AnyFunSuite with FlinkSpec with Matchers with VeryPatientScalaFutures {

  import UnionWithMemoTransformerSpec._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val UnionNodeId = "joined-node-id"

  private val EndNodeId = "end-node-id"

  private val OutVariableName = "outVar"

  private implicit val oneRecordTypeInformation: TypeInformation[OneRecord] = TypeInformation.of(classOf[OneRecord])

  test("union with memo") {
    val BranchFooId = "foo"
    val BranchBarId = "bar"

    val process = ScenarioBuilder
      .streaming("sample-union-memo")
      .sources(
        GraphBuilder
          .source("start-foo", "start-foo")
          .branchEnd(BranchFooId, UnionNodeId),
        GraphBuilder
          .source("start-bar", "start-bar")
          .branchEnd(BranchBarId, UnionNodeId),
        GraphBuilder
          .join(
            UnionNodeId,
            "union-memo",
            Some(OutVariableName),
            List(
              BranchFooId -> List(
                "key"   -> "#input.key",
                "value" -> "#input.value"
              ),
              BranchBarId -> List(
                "key"   -> "#input.key",
                "value" -> "#input.value"
              )
            ),
            "stateTimeout" -> s"T(${classOf[Duration].getName}).parse('PT2H')"
          )
          .emptySink(EndNodeId, "dead-end")
      )

    val key       = "fooKey"
    val sourceFoo = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val sourceBar = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))

    val collectingListener = ResultsCollectingListenerHolder.registerRun

    def outValues = {
      collectingListener.results
        .nodeResults(EndNodeId)
        .map(_.get[jul.Map[String @unchecked, AnyRef @unchecked]](OutVariableName).get.asScala)
    }

    withProcess(process, sourceFoo, sourceBar, collectingListener) {
      sourceFoo.add(OneRecord(key, 0, 123))
      eventually {
        outValues shouldEqual List(
          Map("key" -> key, BranchFooId -> 123)
        )
      }
      sourceBar.add(OneRecord(key, 1, 234))
      eventually {
        outValues shouldEqual List(
          Map("key" -> key, BranchFooId -> 123),
          Map("key" -> key, BranchFooId -> 123, BranchBarId -> 234)
        )
      }
    }
  }

  test("union with memo should handle input nodes named \"key\"") {
    val BranchFooId = UnionWithMemoTransformer.KeyField
    val BranchBarId = "bar"

    val process = ScenarioBuilder
      .streaming("sample-union-memo")
      .sources(
        GraphBuilder
          .source("start-foo", "start-foo")
          .branchEnd(BranchFooId, UnionNodeId),
        GraphBuilder
          .source("start-bar", "start-bar")
          .branchEnd(BranchBarId, UnionNodeId),
        GraphBuilder
          .join(
            UnionNodeId,
            "union-memo",
            Some(OutVariableName),
            List(
              BranchFooId -> List(
                "key"   -> "#input.key",
                "value" -> "#input.value"
              ),
              BranchBarId -> List(
                "key"   -> "#input.key",
                "value" -> "#input.value"
              )
            ),
            "stateTimeout" -> s"T(${classOf[Duration].getName}).parse('PT2H')"
          )
          .emptySink(EndNodeId, "dead-end")
      )

    val sourceFoo = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val sourceBar = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))

    val collectingListener = ResultsCollectingListenerHolder.registerRun

    val model = LocalModelData(
      ConfigFactory.empty(),
      prepareComponents(sourceFoo, sourceBar),
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
    )
    val processValidator = ProcessValidator.default(model)
    val validationResult = processValidator.validate(process, isFragment = false).result

    val expectedMessage = s"""Input node can not be named "${UnionWithMemoTransformer.KeyField}""""
    validationResult should matchPattern {
      case Invalid(NonEmptyList(CustomNodeError(UnionNodeId, `expectedMessage`, None), Nil)) =>
    }
  }

  test("union with memo should handle input nodes with similar names") {
    val BranchFooId = "underscore_or_space"
    val BranchBarId = "underscore or space"

    val process = ScenarioBuilder
      .streaming("sample-union-memo")
      .sources(
        GraphBuilder
          .source("start-foo", "start-foo")
          .branchEnd(BranchFooId, UnionNodeId),
        GraphBuilder
          .source("start-bar", "start-bar")
          .branchEnd(BranchBarId, UnionNodeId),
        GraphBuilder
          .join(
            UnionNodeId,
            "union-memo",
            Some(OutVariableName),
            List(
              BranchFooId -> List(
                "key"   -> "#input.key",
                "value" -> "#input.value"
              ),
              BranchBarId -> List(
                "key"   -> "#input.key",
                "value" -> "#input.value"
              )
            ),
            "stateTimeout" -> s"T(${classOf[Duration].getName}).parse('PT2H')"
          )
          .emptySink(EndNodeId, "dead-end")
      )

    val sourceFoo = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val sourceBar = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))

    val collectingListener = ResultsCollectingListenerHolder.registerRun

    val model = LocalModelData(
      ConfigFactory.empty(),
      prepareComponents(sourceFoo, sourceBar),
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
    )
    val processValidator = ProcessValidator.default(model)
    val validationResult = processValidator.validate(process, isFragment = false).result

    val expectedMessage = s"""Nodes "$BranchFooId", "$BranchBarId" have too similar names"""
    validationResult should matchPattern {
      case Invalid(NonEmptyList(CustomNodeError(UnionNodeId, `expectedMessage`, None), Nil)) =>
    }
  }

  private def withProcess(
      testProcess: CanonicalProcess,
      sourceFoo: BlockingQueueSource[OneRecord],
      sourceBar: BlockingQueueSource[OneRecord],
      collectingListener: ResultsCollectingListener
  )(action: => Unit): Unit = {
    val model = LocalModelData(
      ConfigFactory.empty(),
      prepareComponents(sourceFoo, sourceBar),
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
    )
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(stoppableEnv, model)(testProcess)
    stoppableEnv.withJobRunning(testProcess.name.value)(action)
  }

  def prepareComponents(
      fooRecordsSource: BlockingQueueSource[OneRecord],
      barRecordsSource: BlockingQueueSource[OneRecord]
  ): List[ComponentDefinition] = {
    ComponentDefinition("start-foo", SourceFactory.noParamUnboundedStreamFactory[OneRecord](fooRecordsSource)) ::
      ComponentDefinition("start-bar", SourceFactory.noParamUnboundedStreamFactory[OneRecord](barRecordsSource)) ::
      FlinkBaseComponentProvider.Components
  }

}

object UnionWithMemoTransformerSpec {

  case class OneRecord(key: String, timeHours: Int, value: Int) {
    def timestamp: Long = timeHours * 3600L * 1000
  }

}
