package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import java.io._
import java.nio.charset.StandardCharsets

import org.apache.avro.Schema
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter.Separator
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory}

import scala.reflect.ClassTag

private[confluent] class ConfluentAvroToJsonFormatter(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                      kafkaConfig: KafkaConfig,
                                                      createFormatter: ConfluentSchemaRegistryClient => ConfluentAvroMessageFormatter,
                                                      createReader: ConfluentSchemaRegistryClient => String => ConfluentAvroMessageReader) extends RecordFormatter {

  // it should be created lazy because RecordFormatter is created eager during every process validation
  private lazy val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

  private lazy val formatter = createFormatter(schemaRegistryClient)

  private lazy val reader = createReader(schemaRegistryClient)

  override def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val printStream = new PrintStream(bos, true, StandardCharsets.UTF_8.toString)
    printId(record.key(), printStream)
    printId(record.value(), printStream)
    if (record.key().isEmpty) {
      printStream.print(Separator)
    } else {
      if (kafkaConfig.useStringForKey) {
        printStream.print(new String(record.key(), StandardCharsets.UTF_8))
      } else {
        formatter.writeTo(record.key(), printStream)
      }
      printStream.print(Separator)
    }
    formatter.writeTo(record.value(), printStream)
    bos.toByteArray
  }

  private def printId(bytes: Array[Byte], printStream: PrintStream): Unit = {
    if (bytes.isEmpty) {
      printStream.print(Separator)
    } else {
      val id = ConfluentUtils.readId(bytes)
      printStream.print(id)
      printStream.print(Separator)
    }
  }

  override def parseRecord(topic: String, formatted: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val str = new String(formatted, StandardCharsets.UTF_8)
    val (keySchema, valueSchemaIdAndRest) = readSchemaId(str)
    val (valueSchema, remainingString) = readSchemaId(valueSchemaIdAndRest)
    reader(topic).readMessage(remainingString, keySchema, valueSchema)
  }

  private def readSchemaId(str: String): (Option[Schema], String) = {
    val separatorIndx = str.indexOf(Separator)
    if (separatorIndx < 0)
      throw new IllegalStateException(s"Cannot find schema id separtor: $Separator in text: $str")
    val idStr = str.substring(0, separatorIndx)
    val remaining = if (separatorIndx + 1 > str.length) "" else str.substring(separatorIndx + 1)
    if (idStr.length > 0) {
      val id = Integer.parseInt(idStr)
      val parsedSchema = schemaRegistryClient.client.getSchemaById(id)
      val schema = ConfluentUtils.extractSchema(parsedSchema)
      (Some(schema), remaining)
    } else {
      (None, remaining)
    }
  }

  override def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit
}

object ConfluentAvroToJsonFormatter {

  val Separator = "|"

}

class ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {
    new ConfluentAvroToJsonFormatter(
      schemaRegistryClientFactory,
      kafkaConfig,
      schemaRegistryClient => new ConfluentAvroMessageFormatter(schemaRegistryClient.client),
      schemaRegistryClient => topic => new ConfluentAvroMessageReader(schemaRegistryClient.client, topic, kafkaConfig.useStringForKey, Separator)
    )
  }
}
