package pl.touk.nussknacker.engine.kafka.signal

import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment, _}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.signal.SignalTransformer
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.signal.KafkaSignalStreamConnector
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.signal.CustomSignalReader.signalTopic
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec, KafkaUtils}
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, RecordingExceptionHandler, SimpleRecord, TransformerWithTime}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.nio.charset.StandardCharsets
import java.util.Date

class KafkaSignalsSpec extends FunSuite with Matchers with FlinkSpec with KafkaSpec with VeryPatientScalaFutures {

  test("signals don't cause watermarks to stop") {
    kafkaClient.createTopic(signalTopic)
    MockService.clear()

    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .customNodeNoOutput("signal", "signalReader")
        .customNode("cid", "count", "transformWithTime", "seconds" -> "1")
        .processorEnd("out", "logService", "all" -> "#count")

    def record(time: Long) = SimpleRecord(time.toString, 0, "", new Date(time))

    val data = List(
      record(1000),
      record(1200),
      record(2000)
    )
    val creator: EmptyProcessConfigCreator = new EmptyProcessConfigCreator {

      override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
        "signalReader" -> WithCategories(CustomSignalReader),
        "transformWithTime" -> WithCategories(TransformerWithTime)
      )

      override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
        "input" -> WithCategories(SampleNodes.simpleRecordSource(data))
      )

      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
        "logService" -> WithCategories(new MockService)
      )

      override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[TestProcessSignalFactory]] = {
        val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config)
        Map("sig1" ->
          WithCategories(new TestProcessSignalFactory(kafkaConfig, signalTopic)))
      }

      override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
        ExceptionHandlerFactory.noParams(_ => RecordingExceptionHandler)
    }

    val env = flinkMiniCluster.createExecutionEnvironment()
    val modelData = LocalModelData(config, creator)
    FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
      .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)

    env.withJobRunning(process.id) {
      eventually {
        MockService.data shouldBe List(2, 1)
      }
    }
  }

}

object CustomSignalReader extends CustomStreamTransformer {

  val signalTopic = "signals1"

  @SignalTransformer(signalClass = classOf[TestProcessSignalFactory])
  @MethodToInvoke(returnType = classOf[Void])
  def execute() =
    FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
      context.signalSenderProvider.get[TestProcessSignalFactory]
        .connectWithSignals(start, context.metaData.id, context.nodeId, new EspDeserializationSchema(identity))
        .map((a:Context) => ValueWithContext("", a),
              (_:Array[Byte]) => ValueWithContext[AnyRef]("", Context("id")))
  })
}

class TestProcessSignalFactory(val kafkaConfig: KafkaConfig, val signalsTopic: String)
  extends FlinkProcessSignalSender with KafkaSignalStreamConnector {

  @MethodToInvoke
  def sendSignal()(processId: String): Unit = {
    KafkaUtils.sendToKafkaWithTempProducer(signalsTopic, Array.empty[Byte], "".getBytes(StandardCharsets.UTF_8))(kafkaConfig)
  }

}