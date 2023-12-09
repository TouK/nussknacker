package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.kafka.common.record.TimestampType
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.{FlinkSpec, RecordingExceptionConsumer}
import pl.touk.nussknacker.engine.kafka.KafkaFactory.{SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin.ObjToSerialize
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator.SinkForSampleValue
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{SinkForLongs, SinkForStrings}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
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

  protected var registrar: FlinkProcessRegistrar = _

  protected lazy val modelData = LocalModelData(config, new KafkaSourceFactoryProcessConfigCreator, List.empty)

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    registrar =
      FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
  }

  before {
    SinkForSampleValue.clear()
    SinkForInputMeta.clear()
    SinkForStrings.clear()
    SinkForLongs.clear()
  }

  protected def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(env, process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }

  protected def runAndVerifyResult(
      topicName: String,
      process: CanonicalProcess,
      obj: ObjToSerialize
  ): List[InputMeta[Any]] = {
    val topic = createTopic(topicName)
    pushMessage(objToSerializeSerializationSchema(topic), obj, topic, timestamp = constTimestamp)
    run(process) {
      eventually {
        SinkForInputMeta.data shouldBe List(
          InputMeta(obj.key, topic, 0, 0L, constTimestamp, TimestampType.CREATE_TIME, obj.headers.asJava, 0)
        )
        SinkForSampleValue.data shouldBe List(obj.value)
        RecordingExceptionConsumer.dataFor(runId) should have size 0
      }
    }
    SinkForInputMeta.data
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
      .source("procSource", sourceType.toString, TopicParamName -> topicParamValue(topic))

    val processWithVariables = checkAllVariables
      .foldRight(process.asInstanceOf[GraphBuilder[CanonicalProcess]])((variable, builder) =>
        variable match {
          case (id, expression) => builder.buildSimpleVariable(s"id$id", s"name$id", expression)
        }
      )

    processWithVariables
      .split(
        "split",
        GraphBuilder.emptySink("outputInput", "sinkForSimpleJsonRecord", SinkValueParamName -> "#input"),
        GraphBuilder.emptySink("outputInputMeta", "sinkForInputMeta", SinkValueParamName    -> "#inputMeta")
      )

  }

}
