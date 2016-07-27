package pl.touk.esp.engine.kafka

import org.scalatest.{BeforeAndAfterAll, Suite}

trait KafkaSpec extends { self: Suite with BeforeAndAfterAll =>

  var kafkaZookeeperServer: KafkaZookeeperServer = _
  var kafkaClient: KafkaClient = _

  override protected def beforeAll(): Unit = {
    kafkaZookeeperServer = KafkaZookeeperServer.run(
      kafkaPort = AvailablePortFinder.findAvailablePort(),
      zkPort = AvailablePortFinder.findAvailablePort()
    )
    kafkaClient = new KafkaClient(kafkaAddress = kafkaZookeeperServer.kafkaAddress, zkAddress = kafkaZookeeperServer.zkAddress)
  }

  override protected def afterAll(): Unit = {
    kafkaClient.shutdown()
    kafkaZookeeperServer.shutdown()
  }

}
