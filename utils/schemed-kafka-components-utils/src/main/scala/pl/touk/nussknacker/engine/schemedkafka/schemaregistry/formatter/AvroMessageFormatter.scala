package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.formatter

import io.circe.Json
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.errors.SerializationException
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schema.DatumReaderWriterMixin

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

private[schemaregistry] object AvroMessageFormatter extends DatumReaderWriterMixin {

  private val encoderFactory = EncoderFactory.get

  def asJson(obj: Any): Json = {
    val schema = AvroUtils.getSchema(obj)
    val bos    = new ByteArrayOutputStream()
    val output = new PrintStream(bos, true, StandardCharsets.UTF_8)

    try {
      // pretty = false is important, as we rely on the fact that there are no new lines in message parsing
      val encoder = encoderFactory.jsonEncoder(schema, output, false)
      val record = obj match {
        case bytes: Array[Byte] => ByteBuffer.wrap(bytes)
        case other              => other
      }
      val writer = getDatumWriter(record, schema, useSchemaReflection = false)
      writer.write(record, encoder)
      encoder.flush()
      val str = bos.toString(StandardCharsets.UTF_8)
      // assume the output of encoder is correct or throw Exception trying
      io.circe.parser.parse(str).toOption.get
    } catch {
      case ex: Exception =>
        throw new SerializationException(String.format("Error serializing Avro data of schema %s to json", schema), ex)
    }
  }

}
