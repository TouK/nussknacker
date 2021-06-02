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

private[confluent] class ConfluentAvroToJsonFormatter(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                      kafkaConfig: KafkaConfig,
                                                      createFormatter: ConfluentSchemaRegistryClient => ConfluentAvroMessageFormatter,
                                                      createReader: ConfluentSchemaRegistryClient => String => ConfluentAvroMessageReader,
                                                      formatKey: Boolean) extends RecordFormatter {

  // it should be created lazy because RecordFormatter is created eager during every process validation
  private lazy val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)

  private lazy val formatter = createFormatter(schemaRegistryClient)

  private lazy val reader = createReader(schemaRegistryClient)

  override def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val printStream = new PrintStream(bos, true, StandardCharsets.UTF_8.toString)
    if (formatKey) {
      printId(record.key(), printStream)
    }
    printId(record.value(), printStream)
    if (formatKey) {
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
    val id = ConfluentUtils.readId(bytes)
    printStream.print(id)
    printStream.print(Separator)
  }

  override def parseRecord(topic: String, formatted: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val str = new String(formatted, StandardCharsets.UTF_8)
    val (keySchema, valueSchema, remainingString) = if (formatKey) {
      val (ks, valueSchemaIdAndRest) = readSchemaId(str)
      val (vs, rs) = readSchemaId(valueSchemaIdAndRest)
      (ks, vs, rs)
    } else {
      val (vs, rs) = readSchemaId(str)
      (null, vs, rs)
    }
    reader(topic).readMessage(remainingString, keySchema, valueSchema)
  }

  private def readSchemaId(str: String): (Schema, String) = {
    val separatorIndx = str.indexOf(Separator)
    if (separatorIndx < 1)
      throw new IllegalStateException(s"Cannot find schema id separtor: $Separator in text: $str")
    val id = Integer.parseInt(str.substring(0, separatorIndx))
    val remaining = if (separatorIndx + 1 > str.length) "" else str.substring(separatorIndx + 1)
    val parsedSchema = schemaRegistryClient.client.getSchemaById(id)
    val schema = ConfluentUtils.extractSchema(parsedSchema)
    (schema, remaining)
  }

  override def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit
}

object ConfluentAvroToJsonFormatter {

  val Separator = "|"

}

class ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, formatKey: Boolean) extends RecordFormatterFactory {

  override def create[T](kafkaConfig: KafkaConfig, deserializationSchema: KafkaDeserializationSchema[T]): RecordFormatter = {
    new ConfluentAvroToJsonFormatter(
      schemaRegistryClientFactory,
      kafkaConfig,
      schemaRegistryClient => new ConfluentAvroMessageFormatter(schemaRegistryClient.client),
      schemaRegistryClient => topic => new ConfluentAvroMessageReader(schemaRegistryClient.client, topic, kafkaConfig.useStringForKey, formatKey, Separator),
      formatKey
    )
  }
}
