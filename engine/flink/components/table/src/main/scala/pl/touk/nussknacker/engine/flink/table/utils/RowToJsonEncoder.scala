package pl.touk.nussknacker.engine.flink.table.utils

import io.circe.Json
import org.apache.flink.types.Row
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.util.json.{EncodeOutput, ToJsonBasedOnSchemaEncoder, ToJsonEncoder}

import scala.jdk.CollectionConverters._

class RowToJsonEncoder extends ToJsonEncoder {

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = { case row: Row =>
    val fieldNames = row.getFieldNames(true).asScala
    encode(fieldNames.map(n => n -> encode(row.getField(n))).toMap)
  }

}

class RowToJsonBasedOnSchemaEncoder extends ToJsonBasedOnSchemaEncoder {

  override def encoder(
      encode: ((Any, Schema, Option[String])) => EncodeOutput
  ): PartialFunction[(Any, Schema, Option[String]), EncodeOutput] = { case (row: Row, schema, fieldName) =>
    val fieldNames = row.getFieldNames(true).asScala
    encode(fieldNames.map(n => n -> row.getField(n)).toMap, schema, fieldName)
  }

}
