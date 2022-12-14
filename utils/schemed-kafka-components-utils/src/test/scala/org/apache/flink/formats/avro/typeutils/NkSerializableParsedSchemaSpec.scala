package org.apache.flink.formats.avro.typeutils

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.SchemaBuilder
import org.apache.commons.io.output.ByteArrayOutputStream
import org.everit.json.schema.{ObjectSchema, StringSchema}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{AvroSchemaWithJsonPayload, OpenAPIJsonSchema}

import java.io.{ByteArrayInputStream, ObjectInputStream, ObjectOutputStream}

class NkSerializableParsedSchemaSpec extends AnyFunSuite with Matchers {

  test("should serialize large JSON schemas") {
    val schema = (1 to 5000).foldLeft(ObjectSchema.builder()) {
      case (acc, i) => acc.addPropertySchema(s"field$i", new StringSchema())
    }.build()
    val parsedSchema: ParsedSchema = OpenAPIJsonSchema(schema.toString)

    checkRoundTrip(parsedSchema)
  }

  test("should serialize large Avro schemas") {
    val schema = (1 to 5000).foldLeft(SchemaBuilder.builder().record("record").fields()) {
      case (acc, i) => acc.nullableString(s"field$i", "SomeDefault")
    }.endRecord()
    
    checkRoundTrip(new AvroSchema(schema))
    checkRoundTrip(new AvroSchemaWithJsonPayload(new AvroSchema(schema)))
  }

  private def checkRoundTrip(parsedSchema: ParsedSchema) = {
    val serializable = new NkSerializableParsedSchema(parsedSchema)
    val baos = new ByteArrayOutputStream()
    new ObjectOutputStream(baos).writeObject(serializable)
    val input = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray))
    val expected = input.readObject().asInstanceOf[NkSerializableParsedSchema[ParsedSchema]]
    expected.getParsedSchema shouldBe parsedSchema
  }
}
