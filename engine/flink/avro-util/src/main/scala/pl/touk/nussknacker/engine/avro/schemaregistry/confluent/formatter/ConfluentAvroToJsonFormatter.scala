package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import java.io._
import java.nio.charset.StandardCharsets

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatter._
import pl.touk.nussknacker.engine.kafka.RecordFormatter

private[confluent] class ConfluentAvroToJsonFormatter(schemaRegistryClient: SchemaRegistryClient,
                                                      formatter: ConfluentAvroMessageFormatter,
                                                      reader: ConfluentAvroMessageReader,
                                                      formatKey: Boolean) extends RecordFormatter {

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
    val id = readId(bytes)
    printStream.print(id)
    printStream.print(Separator)
  }

  private def readId(bytes: Array[Byte]): Int =
    ConfluentUtils
      .parsePayloadToByteBuffer(bytes)
      .valueOr(exc => throw new SerializationException(exc.getMessage, exc))
      .getInt

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
    (schemaRegistryClient.getById(id), remaining)
  }
}

object ConfluentAvroToJsonFormatter {

  private val Separator = "|"

  def apply(schemaRegistryClient: ConfluentSchemaRegistryClient, topic: String, formatKey: Boolean): ConfluentAvroToJsonFormatter = {
    new ConfluentAvroToJsonFormatter(
      schemaRegistryClient.client,
      new ConfluentAvroMessageFormatter(schemaRegistryClient.client),
      new ConfluentAvroMessageReader(schemaRegistryClient.client, topic, formatKey, Separator),
      formatKey
    )
  }
}
