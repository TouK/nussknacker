package pl.touk.esp.engine.example

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}
import pl.touk.esp.engine.flink.test.StoppableExecutionEnvironment
import pl.touk.esp.engine.kafka.{KafkaSpec, KafkaZookeeperServer}
import pl.touk.esp.engine.process.FlinkProcessRegistrar
import pl.touk.esp.engine.process.compiler.StandardFlinkProcessCompiler

trait BaseITest extends KafkaSpec {
  self: Suite with BeforeAndAfterAll with Matchers =>

  val creator = new ExampleProcessConfigCreator
  val flinkConf = new Configuration()
  val stoppableEnv = new StoppableExecutionEnvironment(flinkConf)
  val env = new StreamExecutionEnvironment(stoppableEnv)
  var registrar: FlinkProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = TestConfig(kafkaZookeeperServer)
    registrar = new StandardFlinkProcessCompiler(creator, config).createFlinkProcessRegistrar()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stoppableEnv.stop()
  }
}

object TestConfig {
  def apply(kafkaZookeeperServer: KafkaZookeeperServer) = {
    ConfigFactory.empty()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.zkAddress", fromAnyRef(kafkaZookeeperServer.zkAddress))
      .withValue("checkpointInterval", fromAnyRef("10s"))
      .withValue("timeout", fromAnyRef("10s"))
  }
}