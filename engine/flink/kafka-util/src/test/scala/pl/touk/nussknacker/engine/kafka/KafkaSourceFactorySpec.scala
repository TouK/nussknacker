package pl.touk.nussknacker.engine.kafka

import java.nio.charset.StandardCharsets

import org.apache.flink.api.java.typeutils.GenericTypeInfo
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils

class KafkaSourceFactorySpec extends FlatSpec with BeforeAndAfterAll with KafkaSpec with Matchers {

  private implicit val stringTypeInfo: GenericTypeInfo[String] = new GenericTypeInfo(classOf[String])

  private val part0 = List("a", "c")
  private val part1 = List("b", "d")

  it should "read last messages to generate data" in {
    val topic = "testTopic1"

    kafkaClient.createTopic(topic, 2)
    kafkaClient.sendMessage(topic, "", part0(0), Some(0))
    kafkaClient.sendMessage(topic, "", part1(0), Some(1))
    kafkaClient.sendMessage(topic, "", part0(1), Some(0))
    kafkaClient.sendMessage(topic, "", part1(1), Some(1))


    val sourceFactory = new KafkaSourceFactory[String](kafkaConfig, new SimpleStringSchema, None, TestParsingUtils.newLineSplit)

    val dataFor3 = sourceFactory.create(MetaData("", StreamMetaData()), topic).generateTestData(3)
    val dataFor5 = sourceFactory.create(MetaData("", StreamMetaData()), topic).generateTestData(5)

    checkOutput(dataFor3, 3)
    checkOutput(dataFor5, 4)

  }

  //we want to check partitions are read sequentially, but we cannot/don't want to control which partition comes first...
  private def checkOutput(bytes: Array[Byte], expSize: Int): Unit = {
    val lines = new String(bytes, StandardCharsets.UTF_8).split("\n").toList
    lines should have length expSize
    lines.foreach { el =>
      part0++part1 should contain (el)
    }
    checkPartitionDataInOrder(lines)
  }

  private def checkPartitionDataInOrder(lines: List[String]): Unit = {
    List(part0, part1).foreach { partition =>
      val fromPartition = lines.filter(partition.contains)
      partition.take(fromPartition.size) shouldBe fromPartition
    }
  }
}
