package pl.touk.nussknacker.engine.flink.table.utils

import io.circe.Json
import org.apache.flink.types.Row
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.util.json.{EncodeOutput, ToJsonSchemaBasedEncoderCustomisation, ToJsonEncoderCustomisation}

import scala.jdk.CollectionConverters._

class RowToJsonEncoderCustomisation extends ToJsonEncoderCustomisation {

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = { case row: Row =>
    val fieldNames = row.getFieldNames(true).asScala
    encode(fieldNames.map(n => n -> encode(row.getField(n))).toMap)
  }

}

class RowToJsonSchemaBasedEncoderCustomisation extends ToJsonSchemaBasedEncoderCustomisation {

  override def encoder(
      encode: ((Any, Schema, Option[String])) => EncodeOutput
  ): PartialFunction[(Any, Schema, Option[String]), EncodeOutput] = { case (row: Row, schema, fieldName) =>
    val fieldNames = row.getFieldNames(true).asScala
    encode(fieldNames.map(n => n -> row.getField(n)).toMap, schema, fieldName)
  }

}
