package pl.touk.nussknacker.engine.kafka

import org.scalatest.{BeforeAndAfterAll, Suite}

trait KafkaSpec extends { self: Suite with BeforeAndAfterAll =>

  var kafkaZookeeperServer: KafkaZookeeperServer = _
  var kafkaClient: KafkaClient = _
  val kafkaBrokerConfig = Map.empty[String, String]

  lazy val kafkaConfig = KafkaConfig(kafkaZookeeperServer.kafkaAddress, None, None)

  override protected def beforeAll(): Unit = {
    val List(kafkaPort, zkPort) = AvailablePortFinder.findAvailablePorts(2)
    kafkaZookeeperServer = KafkaZookeeperServer.run(
      kafkaPort = kafkaPort,
      zkPort = zkPort,
      kafkaBrokerConfig = kafkaBrokerConfig
    )
    kafkaClient = new KafkaClient(kafkaAddress = kafkaZookeeperServer.kafkaAddress, zkAddress = kafkaZookeeperServer.zkAddress, self.suiteName)
  }

  override protected def afterAll(): Unit = {
    kafkaClient.shutdown()
    kafkaZookeeperServer.shutdown()
  }

}
