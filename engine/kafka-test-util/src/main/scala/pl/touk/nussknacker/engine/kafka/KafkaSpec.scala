package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.test.AvailablePortFinder

trait KafkaSpec extends BeforeAndAfterAll { self: Suite =>

  var kafkaZookeeperServer: KafkaZookeeperServer = _
  var kafkaClient: KafkaClient = _
  val kafkaBrokerConfig = Map.empty[String, String]

  lazy val config: Config = ConfigFactory.load()
    .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    AvailablePortFinder.withAvailablePortsBlocked(2) {
      case List(kafkaPort, zkPort) =>
        kafkaZookeeperServer = KafkaZookeeperServer.run(
          kafkaPort = kafkaPort,
          zkPort = zkPort,
          kafkaBrokerConfig = kafkaBrokerConfig
        )
    }
    kafkaClient = new KafkaClient(kafkaAddress = kafkaZookeeperServer.kafkaAddress, zkAddress = kafkaZookeeperServer.zkAddress, self.suiteName)
  }

  override protected def afterAll(): Unit = {
    try {
      kafkaClient.shutdown()
      kafkaZookeeperServer.shutdown()
    } finally {
      super.afterAll()
    }
  }

}
