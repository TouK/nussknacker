package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.kafka.common.record.TimestampType
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.{FlinkSpec, RecordingExceptionConsumer}
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.kafka.KafkaFactory.{SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin.ObjToSerialize
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator.ResultsHolders
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.NuScalaTestAssertions

import scala.jdk.CollectionConverters._

trait KafkaSourceFactoryProcessMixin
    extends AnyFunSuite
    with Matchers
    with KafkaSourceFactoryMixin
    with FlinkSpec
    with BeforeAndAfter
    with NuScalaTestAssertions {

  protected def resultHolders: () => ResultsHolders

  protected lazy val modelData: LocalModelData = LocalModelData(
    inputConfig = config,
    components = List.empty,
    configCreator = new KafkaSourceFactoryProcessConfigCreator(resultHolders)
  )

  protected override def beforeAll(): Unit = {
    super.beforeAll()
  }

  before {
    resultHolders().clear()
  }

  protected def run(process: CanonicalProcess, deploymentData: DeploymentData = DeploymentData.empty)(
      action: => Unit
  ): Unit = {
    flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
      val executionResult = new FlinkScenarioUnitTestJob(modelData).run(process, env, deploymentData)
      flinkMiniCluster.withRunningJob(executionResult.getJobID)(action)
    }
  }

  protected def runAndVerifyResult(
      topicName: String,
      process: CanonicalProcess,
      obj: ObjToSerialize,
      deploymentData: DeploymentData = DeploymentData.empty
  ): Unit = {
    val topic = createTopic(topicName)
    pushMessage(objToSerializeSerializationSchema(topic), obj, timestamp = constTimestamp)
    run(process, deploymentData) {
      eventually {
        RecordingExceptionConsumer.exceptionsFor(runId) should have size 0
        resultHolders().sinkForSimpleJsonRecordResultsHolder.results shouldBe List(obj.value)
        resultHolders().sinkForInputMetaResultsHolder.results shouldBe List(
          InputMeta(obj.key, topic, 0, 0L, constTimestamp, TimestampType.CREATE_TIME, obj.headers.asJava, 0)
        )
      }
    }
  }

  object SourceType extends Enumeration {
    type SourceType = Value
    val jsonKeyJsonValueWithMeta: SourceType.Value       = Value("kafka-jsonKeyJsonValueWithMeta")
    val jsonValueWithMeta: SourceType.Value              = Value("kafka-jsonValueWithMeta")
    val jsonValueWithMetaWithException: SourceType.Value = Value("kafka-jsonValueWithMeta-withException")
  }

  protected def createProcess(
      topic: String,
      sourceType: SourceType.Value,
      customVariables: Map[String, String] = Map.empty,
      topicParamValue: String => String = topic => s"'$topic'"
  ): CanonicalProcess = {
    // should check and recognize all variables based on #input and #inputMeta
    val inputVariables = Map("id" -> " #input.id", "field" -> "#input.field")
    val metaVariables = Map(
      "topic"         -> "#inputMeta.topic",
      "partition"     -> "#inputMeta.partition",
      "offset"        -> "#inputMeta.offset",
      "timestamp"     -> "#inputMeta.timestamp",
      "timestampType" -> "#inputMeta.timestampType.name",
      "leaderEpoch"   -> "#inputMeta.leaderEpoch"
    )
    val keyVariables = sourceType match {
      case SourceType.jsonKeyJsonValueWithMeta =>
        Map("key1" -> "#inputMeta.key.partOne", "key2" -> "#inputMeta.key.partTwo")
      case SourceType.jsonValueWithMeta => Map("key" -> "#inputMeta.key")
      case _                            => Map.empty[String, String]
    }
    val headerVariables   = Map("headers" -> """#inputMeta.headers.get("headerOne")""")
    val checkAllVariables = inputVariables ++ metaVariables ++ keyVariables ++ headerVariables ++ customVariables

    val process = ScenarioBuilder
      .streaming(s"proc-$topic")
      .source("procSource", sourceType.toString, TopicParamName.value -> topicParamValue(topic).spel)

    val processWithVariables = checkAllVariables
      .foldRight(process.asInstanceOf[GraphBuilder[CanonicalProcess]])((variable, builder) =>
        variable match {
          case (id, expression) => builder.buildSimpleVariable(s"id$id", s"name$id", expression.spel)
        }
      )

    processWithVariables
      .split(
        "split",
        GraphBuilder.emptySink("outputInput", "sinkForSimpleJsonRecord", SinkValueParamName.value -> "#input".spel),
        GraphBuilder.emptySink("outputInputMeta", "sinkForInputMeta", SinkValueParamName.value    -> "#inputMeta".spel)
      )

  }

}
