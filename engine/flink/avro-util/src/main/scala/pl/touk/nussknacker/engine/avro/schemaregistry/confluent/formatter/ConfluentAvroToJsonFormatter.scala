package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import java.io._
import java.nio.charset.StandardCharsets

import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClient, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter._
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter}

private[confluent] class ConfluentAvroToJsonFormatter(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                                      kafkaConfig: KafkaConfig,
                                                      createFormatter: ConfluentSchemaRegistryClient => ConfluentAvroMessageFormatter,
                                                      createReader: ConfluentSchemaRegistryClient => ConfluentAvroMessageReader,
                                                      formatKey: Boolean) extends RecordFormatter {

  // it should be created lazy because RecordFormatter is created eager during every process validation
  private lazy val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)

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
      formatter.writeTo(record.key(), printStream)
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

  override def parseRecord(formatted: Array[Byte]): ProducerRecord[Array[Byte], Array[Byte]] = {
    val str = new String(formatted, StandardCharsets.UTF_8)
    val (keySchema, valueSchema, remainingString) = if (formatKey) {
      val (ks, valueSchemaIdAndRest) = readSchemaId(str)
      val (vs, rs) = readSchemaId(valueSchemaIdAndRest)
      (ks, vs, rs)
    } else {
      val (vs, rs) = readSchemaId(str)
      (null, vs, rs)
    }
    reader.readMessage(remainingString, keySchema, valueSchema)
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
}

object ConfluentAvroToJsonFormatter {

  private val Separator = "|"

  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, kafkaConfig: KafkaConfig, topic: String, formatKey: Boolean): ConfluentAvroToJsonFormatter = {
    new ConfluentAvroToJsonFormatter(
      schemaRegistryClientFactory,
      kafkaConfig,
      schemaRegistryClient => new ConfluentAvroMessageFormatter(schemaRegistryClient.client),
      schemaRegistryClient => new ConfluentAvroMessageReader(schemaRegistryClient.client, topic, formatKey, Separator),
      formatKey
    )
  }
}
