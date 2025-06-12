package pl.touk.nussknacker.engine.schemedkafka.encode

import io.circe.{Encoder, Json}
import org.apache.avro.{JsonProperties, LogicalTypes, Schema}
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

object JsonTemplateFromAvroSchemaDeterminer {

  def jsonTemplateFor(schema: Schema): Expression = {
    val expressionJson = defaultJsonFor(schema)
    Expression.jsonTemplate(expressionJson.getOrElse(Json.obj()).spaces2)
  }

  private def defaultJsonFor(schema: Schema): Option[Json] = {
    schema.getType match {
      case Schema.Type.RECORD =>
        val recordFields = schema.getFields.asScala.toList
        val jsonFields = recordFields.map { field =>
          field
            .name() -> defaultJsonForRecordField(field)
            .orElse(jsonBasedOnSchema(field.schema()))
            .getOrElse(Json.Null)
        }
        Some(Json.fromFields(jsonFields.sortBy(_._1)))
      case other => // only record fields can have defaults
        jsonBasedOnSchema(schema)
    }
  }

  private def defaultJsonForRecordField(fieldSchema: Schema.Field): Option[Json] = {
    val schema = fieldSchema.schema()
    schema.getType match {
      case Schema.Type.RECORD =>
        val fields = schema.getFields.asScala.flatMap { field =>
          defaultJsonFor(field.schema()).map(fieldJson => field.name() -> fieldJson)
        }.toMap
        Some(Json.obj(fields.toSeq: _*))
      case Schema.Type.ENUM =>
        withDefaultValueAs[String](fieldSchema)
      case Schema.Type.ARRAY =>
        None
      case Schema.Type.MAP =>
        Some(Json.obj())
      case Schema.Type.UNION =>
        // For unions Avro supports default to be the type of the first type in the union. See: https://issues.apache.org/jira/browse/AVRO-1118
        schema.getTypes.asScala.headOption match {
          case Some(firstUnionSchema) => defaultJsonFor(firstUnionSchema)
          case None                   => Some(Json.Null)
        }
      case Schema.Type.STRING => withDefaultValueAs[String](fieldSchema)
      case Schema.Type.BYTES  => None
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.date() =>
        None
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.timeMillis() =>
        None
      case Schema.Type.INT => withDefaultValueAs[Integer](fieldSchema)
      case Schema.Type.LONG =>
        withDefaultValueAs[java.lang.Long](fieldSchema)
      case Schema.Type.FLOAT =>
        withDefaultValueAs[java.lang.Float](fieldSchema)
      case Schema.Type.DOUBLE =>
        withDefaultValueAs[java.lang.Double](fieldSchema)
      case Schema.Type.BOOLEAN =>
        withDefaultValueAs[java.lang.Boolean](fieldSchema)
      case Schema.Type.NULL => Some(Json.Null)
      case Schema.Type.FIXED =>
        Option(fieldSchema.defaultVal()) match {
          case Some(array: Array[Byte]) => Some(Json.fromString(fixed(array)))
          case _                        => None
        }
    }
  }

  private def withDefaultValueAs[T <: AnyRef: ClassTag: Encoder](fieldSchema: Schema.Field): Option[Json] =
    Option(fieldSchema.defaultVal()) match {
      case Some(JsonProperties.NULL_VALUE) => Some(Json.Null)
      case Some(value: T)                  => Some(Encoder[T].apply(value))
      case Some(_) | None                  => None
    }

  private def jsonBasedOnSchema(schema: Schema): Option[Json] = {
    schema.getType match {
      case Schema.Type.RECORD =>
        val fields = schema.getFields.asScala.flatMap { field =>
          jsonBasedOnSchema(field.schema()).map(defaultValue => field.name() -> defaultValue)
        }
        Some(Json.fromFields(fields.sortBy(_._1)))
      case Schema.Type.ENUM =>
        Option(schema.getEnumDefault).orElse(schema.getEnumSymbols.asScala.headOption).map(Json.fromString)
      case Schema.Type.ARRAY =>
        Some(jsonBasedOnSchema(schema.getElementType).map(Json.arr(_)).getOrElse(Json.arr()))
      case Schema.Type.MAP =>
        Some(Json.obj())
      case Schema.Type.UNION =>
        schema.getTypes.asScala.headOption.flatMap(jsonBasedOnSchema)
      case Schema.Type.STRING  => Some(Json.fromString(""))
      case Schema.Type.BYTES   => Some(Json.fromString(""))
      case Schema.Type.INT     => Some(Json.fromInt(0))
      case Schema.Type.LONG    => Some(Json.fromLong(0L))
      case Schema.Type.BOOLEAN => Some(Json.fromBoolean(true))
      case Schema.Type.FLOAT   => Some(Json.fromFloatOrNull(0.0f))
      case Schema.Type.DOUBLE  => Some(Json.fromDoubleOrNull(0.0))
      case Schema.Type.NULL    => Some(Json.Null)
      case Schema.Type.FIXED   => Some(Json.fromString(fixed(Array.fill[Byte](schema.getFixedSize)(0))))
    }
  }

  private def fixed(array: Array[Byte]): String = {
    new String(array, "ISO-8859-1")
  }

}
