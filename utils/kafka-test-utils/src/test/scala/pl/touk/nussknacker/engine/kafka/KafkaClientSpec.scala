package pl.touk.nussknacker.engine.kafka

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

class KafkaClientSpec extends AnyFunSuiteLike with KafkaSpec with Matchers {

  override protected def kafkaComponentsConfigPrefix: String = "dummy"

  test("should wait for topic existence after topic is created") {
    kafkaClient.createTopic("foo")
    KafkaUtils.usingAdminClient(KafkaComponentsConfig.parseConfig(modelConfig.getConfig("dummy"))) { admin =>
      admin.listTopics().names().get() should contain("foo")
    }
  }

}
