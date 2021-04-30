package pl.touk.nussknacker.engine.demo

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{Matchers, Suite}
import pl.touk.nussknacker.engine.api.conversion.ProcessConfigCreatorMapping
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.{javademo, process}
import pl.touk.nussknacker.engine.kafka.{KafkaSpec, KafkaZookeeperServer}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData

trait BaseITest extends FlinkSpec with KafkaSpec { self: Suite with Matchers =>

  val creatorLang: CreatorLang.Value

  lazy val creator = {
    creatorLang match {
      case CreatorLang.Java =>
        ProcessConfigCreatorMapping.toProcessConfigCreator(new javademo.DemoProcessConfigCreator)
      case CreatorLang.Scala =>
        new DemoProcessConfigCreator
    }
  }

  var registrar: FlinkProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = TestConfig(kafkaZookeeperServer)
    val modelData = LocalModelData(config, creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
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

trait BaseJavaITest { self: BaseITest =>
  override val creatorLang = CreatorLang.Java
}

trait BaseScalaITest { self: BaseITest =>
  override val creatorLang = CreatorLang.Scala
}