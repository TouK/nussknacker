package pl.touk.nussknacker.engine.benchmarks.avro

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.flink.formats.avro.typeutils.LogicalTypesGenericRecordBuilder
import pl.touk.nussknacker.engine.avro.AvroUtils

object AvroSamples {

  val sampleSchemaId = 123

  private val fieldsCount = 10

  val sampleSchema: Schema = AvroUtils.parseSchema(s"""{
                                                      |  "type": "record",
                                                      |  "name": "FullName",
                                                      |  "fields": [
                                                      |    ${1.to(fieldsCount).map(fieldSchema).mkString(",\n    ")}
                                                      |  ]
                                                      |}
    """.stripMargin)

  private val sampleFieldValue = "foobar"

  val sampleRecord: GenericData.Record = {
    val builder = new LogicalTypesGenericRecordBuilder(sampleSchema)
    1.to(fieldsCount).foreach(i => builder.set(fieldName(i), sampleFieldValue))
    builder.build()
  }

  private def fieldSchema(i: Int) = s"""    { "name": "${fieldName(i)}", "type": "string" }"""

  private def fieldName(i: Int) = s"field_${i}"

}
