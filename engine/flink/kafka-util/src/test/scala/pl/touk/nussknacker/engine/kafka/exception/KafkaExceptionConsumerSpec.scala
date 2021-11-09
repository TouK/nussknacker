package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SimpleRecord
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils._

import java.util.Date

class KafkaExceptionConsumerSpec extends FunSuite with FlinkSpec with KafkaSpec with Matchers {

  private val topicName = "testingErrors"

  protected var registrar: FlinkProcessRegistrar = _

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    val configWithExceptionHandler = config
      .withValue("exceptionHandler.type", fromAnyRef("Kafka"))
      .withValue("exceptionHandler.topic", fromAnyRef(topicName))
      .withValue("exceptionHandler.additionalParams.configurableKey", fromAnyRef("sampleValue"))
      .withValue("exceptionHandler.kafka", config.getConfig("kafka").root())

    val modelData = LocalModelData(configWithExceptionHandler, new ExceptionTestConfigCreator())
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
  }

  test("should record errors on topic") {
    val process = EspProcessBuilder
      .id("testProcess")
      .source("source", "source")
      .filter("shouldFail", "1/0 != 10")
      .emptySink("end", "sink")

    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id) {

      val consumer = kafkaClient.createConsumer()
      val consumed = consumer.consume(topicName).head
      new String(consumed.key()) shouldBe "testProcess-shouldFail"

      val decoded = CirceUtil.decodeJsonUnsafe[KafkaExceptionInfo](consumed.message())
      decoded.nodeId shouldBe Some("shouldFail")
      decoded.processName shouldBe "testProcess"
      decoded.message shouldBe Some("Expression [1/0 != 10] evaluation failed, message: / by zero")
      decoded.exceptionInput shouldBe Some("1/0 != 10")
      decoded.additionalData shouldBe Map("configurableKey" -> "sampleValue")

    }
    

  }


}

class ExceptionTestConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "source" -> WithCategories(SampleNodes.simpleRecordSource(SimpleRecord("id1", 1, "value1", new Date())::Nil))
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    "sink" -> WithCategories(SinkFactory.noParam(SampleNodes.MonitorEmptySink))
  )
}