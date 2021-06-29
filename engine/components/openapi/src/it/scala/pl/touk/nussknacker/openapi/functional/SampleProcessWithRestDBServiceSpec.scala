package pl.touk.nussknacker.openapi.functional

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, EitherValues, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.modelconfig.DefaultModelConfigLoader
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils._
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.nio.charset.StandardCharsets

class SampleProcessWithRestDBServiceSpec extends FunSuite with BeforeAndAfterAll with Matchers with FlinkSpec with KafkaSpec with EitherValues with LazyLogging {

  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  override lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
    .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("not_used"))

  private lazy val resolvedConfig = new DefaultModelConfigLoader().resolveInputConfigDuringExecution(config, getClass.getClassLoader).config

  lazy val mockProcessObjectDependencies: ProcessObjectDependencies = ProcessObjectDependencies(resolvedConfig, DefaultNamespacedObjectNaming)

  lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config, "kafka")

  test("should read a json object from kafka, extracts id field from it, call getPetById service and save obtained pet's name to kafka") {
    val JsonInTopic: String = "name.json.input"
    val JsonOutTopic: String = "name.json.output"

    val kafkaInput =
      """{
        |  "query": "{\"zip\":\"AX6 7KK\"}"
        |}""".stripMargin

    def jsonProcess() =
      EspProcessBuilder
        .id("opeanapi-test")
        .parallelism(1)
        .exceptionHandler()
        .source("start", "kafka-typed-json",
          "topic" -> s"'$JsonInTopic'",
          "type" ->
            """{
              |  "query": "String"
              |}""".stripMargin
        )
        .enricher("comapnies-call", "company", "GET-companies", ("q", "#input.query"), ("h", ""))
        .sink("end", """#company[0].name""", "kafka-json", "topic" -> s"'$JsonOutTopic'")

    kafkaClient.sendMessage(JsonInTopic, kafkaInput)

    run(jsonProcess()) {
      val consumer = kafkaClient.createConsumer()
      val processed = consumer.consume(JsonOutTopic).map(_.message()).map(new String(_, StandardCharsets.UTF_8)).take(1).head
      processed shouldEqual "\"Nullam Suscipit Est Limited\""
    }
  }

  private lazy val creator = new EmptyProcessConfigCreator

  private var registrar: FlinkProcessRegistrar = _

  def run(process: EspProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(resolvedConfig, creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
  }
}