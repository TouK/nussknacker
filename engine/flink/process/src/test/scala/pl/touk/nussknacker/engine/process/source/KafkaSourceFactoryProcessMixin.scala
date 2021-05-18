package pl.touk.nussknacker.engine.process.source

import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin
import pl.touk.nussknacker.engine.kafka.source.{InputMeta, KafkaSourceFactory}
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin.ObjToSerialize
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForStrings
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.source.KafkaSourceFactoryProcessConfigCreator.{SinkForInputMeta, SinkForSampleValue, recordingExceptionHandler}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.NussknackerAssertions

import scala.collection.JavaConverters.mapAsJavaMapConverter

trait KafkaSourceFactoryProcessMixin extends FunSuite with Matchers with KafkaSourceFactoryMixin with FlinkSpec with BeforeAndAfter with NussknackerAssertions {

  protected var registrar: FlinkProcessRegistrar = _

  private lazy val creator: KafkaSourceFactoryProcessConfigCreator = new KafkaSourceFactoryProcessConfigCreator(kafkaConfig)

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
  }

  before {
    SinkForSampleValue.clear()
    SinkForInputMeta.clear()
    SinkForStrings.clear()
  }

  after {
    recordingExceptionHandler.clear()
  }

  protected def run(process: EspProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }

  protected def fail(process: EspProcess): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    val executionResult = env.executeAndWaitForStart(process.id)
    env.waitForJobState(executionResult.getJobID, process.id, ExecutionState.FAILED, ExecutionState.CANCELED)()
  }

  protected def runAndFail(topicName: String, process: EspProcess, obj: ObjToSerialize) = {
    val topic = createTopic(topicName)
    pushMessage(objToSerializeSerializationSchema(topic), obj, topic, timestamp = constTimestamp)
    fail(process)
  }

  protected def runAndVerifyResult(topicName: String, process: EspProcess, obj: ObjToSerialize): List[InputMeta[Any]] = {
    val topic = createTopic(topicName)
    pushMessage(objToSerializeSerializationSchema(topic), obj, topic, timestamp = constTimestamp)
    run(process) {
      eventually {
        SinkForInputMeta.data shouldBe List(InputMeta(obj.key, topic, 0, 0L, constTimestamp, TimestampType.CREATE_TIME, obj.headers.asJava, 0))
        SinkForSampleValue.data shouldBe List(obj.value)
        recordingExceptionHandler.data should have size 0
      }
    }
    SinkForInputMeta.data
  }

  object SourceType extends Enumeration {
    type SourceType = Value
    val jsonKeyJsonValueWithMeta: SourceType.Value = Value("kafka-jsonKeyJsonValueWithMeta")
    val jsonValueWithMeta: SourceType.Value = Value("kafka-jsonValueWithMeta")
    val jsonValueWithMetaWithException: SourceType.Value = Value("kafka-jsonValueWithMeta-withException")
  }

  protected def createProcess(topic: String, sourceType: SourceType.Value, customVariables: Map[String, String] = Map.empty): EspProcess = {
    //should check and recognize all variables based on #input and #inputMeta
    val inputVariables = Map("id" ->" #input.id", "field" -> "#input.field")
    val metaVariables = Map(
      "topic" -> "#inputMeta.topic",
      "partition" -> "#inputMeta.partition",
      "offset" -> "#inputMeta.offset",
      "timestamp" -> "#inputMeta.timestamp",
      "timestampType" -> "#inputMeta.timestampType.name",
      "leaderEpoch" -> "#inputMeta.leaderEpoch"
    )
    val keyVariables = sourceType match {
      case SourceType.jsonKeyJsonValueWithMeta => Map("key1" -> "#inputMeta.key.partOne", "key2" -> "#inputMeta.key.partTwo")
      case SourceType.jsonValueWithMeta => Map("key" -> "#inputMeta.key")
      case _ => Map.empty[String, String]
    }
    val headerVariables = Map("headers" -> """#inputMeta.headers.get("headerOne")""")
    val checkAllVariables = inputVariables ++ metaVariables ++ keyVariables ++ headerVariables ++ customVariables

    val process = EspProcessBuilder
      .id(s"proc-${topic}")
      .exceptionHandler()
      .source("procSource", sourceType.toString, KafkaSourceFactory.TopicParamName -> s"'${topic}'")

    val processWithVariables = checkAllVariables
      .foldRight(process.asInstanceOf[GraphBuilder[EspProcess]])( (variable, builder) =>
        variable match {
          case (id, expression) => builder.buildSimpleVariable(s"id${id}", s"name${id}", expression)
        }
      )

    processWithVariables
      .split("split",
        GraphBuilder.sink("outputInput", "#input", "sinkForSimpleJsonRecord"),
        GraphBuilder.sink("outputInputMeta", "#inputMeta", "sinkForInputMeta")
      )

  }

}
