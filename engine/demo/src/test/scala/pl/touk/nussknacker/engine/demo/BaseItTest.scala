package pl.touk.nussknacker.engine.demo

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}
import pl.touk.nussknacker.engine.api.conversion.ProcessConfigCreatorMapping
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.javademo
import pl.touk.nussknacker.engine.kafka.{KafkaSpec, KafkaZookeeperServer}
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.testing.LocalModelData

trait BaseITest extends KafkaSpec {
  self: Suite with BeforeAndAfterAll with Matchers =>

  val creatorLang: CreatorLang.Value

  lazy val creator = {
    creatorLang match {
      case CreatorLang.Java =>
        ProcessConfigCreatorMapping.toProcessConfigCreator(new javademo.DemoProcessConfigCreator)
      case CreatorLang.Scala =>
        new DemoProcessConfigCreator
    }
  }

  val stoppableEnv = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())
  val env = new StreamExecutionEnvironment(stoppableEnv)
  var registrar: FlinkStreamingProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = TestConfig(kafkaZookeeperServer)
    registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config)
  }

  override protected def afterAll(): Unit = {
    stoppableEnv.stop()
    super.afterAll()
  }
}

object TestConfig {
  def apply(kafkaZookeeperServer: KafkaZookeeperServer) = {
    ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))

  }
}

object CreatorLang extends Enumeration {
  type CreatorLang = Value
  val Java, Scala = Value
}

trait BaseJavaITest extends BaseITest { self: Suite with BeforeAndAfterAll with Matchers =>
  override val creatorLang = CreatorLang.Java
}

trait BaseScalaITest extends BaseITest { self: Suite with BeforeAndAfterAll with Matchers =>
  override val creatorLang = CreatorLang.Scala
}