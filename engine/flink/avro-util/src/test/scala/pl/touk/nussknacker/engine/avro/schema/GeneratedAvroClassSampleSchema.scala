package pl.touk.nussknacker.engine.avro.schema
import org.apache.avro.Schema
import org.apache.avro.specific.SpecificRecordBase

import java.util

object GeneratedAvroClassSampleSchema extends TestSchemaWithSpecificRecord {

  override lazy val schema: Schema = GeneratedAvroClassSample.getClassSchema

  override def specificRecord: SpecificRecordBase = new GeneratedAvroClassSample(
    123L,
    "The Catcher in the Rye",
    12.34f,
    "16-year-old Holden's experiences in New York City after his fourth expulsion and departure from an elite college preparatory school.",
    SampleStatus.OK,
    new SampleLocation(234L, 345L),
    util.Arrays.asList(
      new SampleCustomer("John", "Smith"),
      new SampleCustomer("John", null),
      new SampleCustomer(null, "Smith")
    )
  )

  override def exampleData: Map[String, Any] = Map(
    "id" -> 123L,
    "title" -> "The Catcher in the Rye",
    "ratio" -> 12.34f,
    "description" -> "16-year-old Holden's experiences in New York City after his fourth expulsion and departure from an elite college preparatory school.",
    "status" -> SampleStatus.OK,
    "location" -> new SampleLocation(234L, 345L),
    "customers" -> util.Arrays.asList(
      new SampleCustomer("John", "Smith"),
      new SampleCustomer("John", null),
      new SampleCustomer(null, "Smith")
    )
  )

  override def stringSchema: String = "" // see hardcoded definition in GeneratedAvroClassSample
}
