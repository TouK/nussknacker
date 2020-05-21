package pl.touk.nussknacker.genericmodel.schema

import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroUtils

object FullName {
  val stringSchema: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.genericmodel.schema",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val schema: Schema = AvroUtils.parseSchema(stringSchema)
}
