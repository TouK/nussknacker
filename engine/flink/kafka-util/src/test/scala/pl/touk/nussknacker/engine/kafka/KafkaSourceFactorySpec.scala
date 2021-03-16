package pl.touk.nussknacker.engine.kafka

import java.nio.charset.{Charset, StandardCharsets}

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.typeutils.GenericTypeInfo
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.kafka.source.{KafkaSource, KafkaSourceFactory}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

class KafkaSourceFactorySpec extends FlatSpec with KafkaSpec with Matchers {

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


    val source: Source[String] with TestDataGenerator = createSource(topic)
    val dataFor3 = source.generateTestData(3)
    val dataFor5 = source.generateTestData(5)

    checkOutput(dataFor3, 3)
    checkOutput(dataFor5, 4)

  }

  it should "parse test data correctly" in {
    val topic = "testTopic1"
    val testData = "first\nsecond".getBytes(StandardCharsets.UTF_8)

    val source: KafkaSource[String] = createSource(topic)
    source.testDataParser.parseTestData(testData) shouldBe List("first", "second")
  }


  private def createSource(topic: String): KafkaSource[String] = {
    val sourceFactory = new KafkaSourceFactory[String](new SimpleStringSchema, None, BasicFormatter, ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader)))
    sourceFactory.create(MetaData("", StreamMetaData()), topic)(NodeId(""))
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
