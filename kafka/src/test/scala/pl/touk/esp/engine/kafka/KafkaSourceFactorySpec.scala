package pl.touk.esp.engine.kafka

import org.apache.flink.api.java.typeutils.GenericTypeInfo
import org.apache.flink.streaming.util.serialization.SimpleStringSchema
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.test.TestParsingUtils

class KafkaSourceFactorySpec extends FlatSpec with BeforeAndAfterAll with KafkaSpec with Matchers {

  implicit val stringTypeInfo = new GenericTypeInfo(classOf[String])

  lazy val kafkaConfig = KafkaConfig(kafkaZookeeperServer.zkAddress, kafkaZookeeperServer.kafkaAddress, None, None)

  it should "read last messages to generate data" in {
    val topic = "testTopic1"


    kafkaClient.createTopic(topic, 2)
    kafkaClient.sendMessage(topic, "", "a", Some(0))
    kafkaClient.sendMessage(topic, "", "b", Some(1))
    kafkaClient.sendMessage(topic, "", "c", Some(0))
    kafkaClient.sendMessage(topic, "", "d", Some(1))


    val sourceFactory = new KafkaSourceFactory[String](kafkaConfig, new SimpleStringSchema, None, TestParsingUtils.newLineSplit)

    val dataFor3 = sourceFactory.create(MetaData(""), topic).generateTestData(3)
    val dataFor5 = sourceFactory.create(MetaData(""), topic).generateTestData(5)


    //first partition 1, than 0
    new String(dataFor3) shouldBe "b\nd\na"
    new String(dataFor5) shouldBe "b\nd\na\nc"

  }


}
