package pl.touk.nussknacker.engine.example

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}
import pl.touk.nussknacker.engine.api.conversion.ProcessConfigCreatorMapping
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.kafka.{KafkaSpec, KafkaZookeeperServer}
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.StandardFlinkProcessCompiler

trait BaseITest extends KafkaSpec {
  self: Suite with BeforeAndAfterAll with Matchers =>

  val creatorLang: CreatorLang.Value

  lazy val creator = {
    creatorLang match {
      case CreatorLang.Java =>
        ProcessConfigCreatorMapping.toProcessConfigCreator(new pl.touk.nussknacker.engine.javaexample.ExampleProcessConfigCreator)
      case CreatorLang.Scala =>
        new ExampleProcessConfigCreator
    }
  }

  val stoppableEnv = new StoppableExecutionEnvironment(FlinkTestConfiguration.configuration)
  val env = new StreamExecutionEnvironment(stoppableEnv)
  var registrar: FlinkProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = TestConfig(kafkaZookeeperServer)
    env.getConfig.disableSysoutLogging()
    registrar = new StandardFlinkProcessCompiler(creator, config).createFlinkProcessRegistrar()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stoppableEnv.stop()
  }
}

object TestConfig {
  def apply(kafkaZookeeperServer: KafkaZookeeperServer) = {
    ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.zkAddress", fromAnyRef(kafkaZookeeperServer.zkAddress))
      
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