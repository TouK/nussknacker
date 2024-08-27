package pl.touk.nussknacker.engine.schemedkafka.typed

import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.{LogicalTypes, Schema}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedNull, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.schemedkafka.schema.AvroStringSettings

import java.nio.ByteBuffer
import java.time.{Instant, LocalDate, LocalTime}
import java.util.UUID

object AvroSchemaTypeDefinitionExtractor extends AvroSchemaTypeDefinitionExtractor(Typed.typedClass[GenericRecord])

class AvroSchemaTypeDefinitionExtractor(recordUnderlyingType: TypedClass) {

  import scala.jdk.CollectionConverters._

  val dictIdProperty = "nkDictId"

  /**
    * See {@link pl.touk.nussknacker.engine.schemedkafka.encode.ToAvroSchemaBasedEncoder} for underlying avro types
    *
    * !When applying changes keep in mind that this Schema.Type pattern matching is duplicated in {@link pl.touk.nussknacker.engine.schemedkafka.AvroDefaultExpressionDeterminer},
    * and is used at {@link  pl.touk.nussknacker.engine.schemedkafka.encode.AvroSchemaOutputValidator}
    */
  def typeDefinition(schema: Schema): TypingResult = {
    schema.getType match {
      case Schema.Type.RECORD => {
        val fields = schema.getFields.asScala
          .map(field => field.name() -> typeDefinition(field.schema()))

        Typed.record(fields, recordUnderlyingType)
      }
      case Schema.Type.ENUM =>
        Typed.typedClass[EnumSymbol]
      case Schema.Type.ARRAY =>
        Typed.genericTypeClass[java.util.List[_]](List(typeDefinition(schema.getElementType)))
      case Schema.Type.MAP =>
        Typed.genericTypeClass[java.util.Map[_, _]](
          List(AvroStringSettings.stringTypingResult, typeDefinition(schema.getValueType))
        )
      case Schema.Type.UNION =>
        val childTypeDefinitions = schema.getTypes.asScala.map(sch => typeDefinition(sch)).toSet
        // TODO: Is it ok? Or we should skip this element?
        Typed.fromIterableOrUnknownIfEmpty(childTypeDefinitions)
      // See org.apache.avro.UUIDConversion
      case Schema.Type.STRING if schema.getLogicalType == LogicalTypes.uuid() =>
        Typed[UUID]
      // See org.apache.avro.DecimalConversion
      case Schema.Type.BYTES | Schema.Type.FIXED
          if schema.getLogicalType != null && schema.getLogicalType.isInstanceOf[LogicalTypes.Decimal] =>
        Typed[java.math.BigDecimal]
      case Schema.Type.STRING =>
        val baseType = AvroStringSettings.stringTypingResult
        Option(schema.getProp(AvroSchemaTypeDefinitionExtractor.dictIdProperty))
          .map(Typed.taggedDictValue(baseType, _))
          .getOrElse(baseType)
      case Schema.Type.BYTES =>
        Typed[ByteBuffer]
      case Schema.Type.FIXED =>
        Typed[GenericData.Fixed]
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.date() =>
        Typed[LocalDate]
      case Schema.Type.INT if schema.getLogicalType == LogicalTypes.timeMillis() =>
        Typed[LocalTime]
      case Schema.Type.INT =>
        Typed[Int]
      // See org.apache.avro.data.TimeConversions
      case Schema.Type.LONG
          if schema.getLogicalType == LogicalTypes.timestampMillis() || schema.getLogicalType == LogicalTypes
            .timestampMicros() =>
        Typed[Instant]
      case Schema.Type.LONG if schema.getLogicalType == LogicalTypes.timeMicros() =>
        Typed[LocalTime]
      case Schema.Type.LONG =>
        Typed[Long]
      case Schema.Type.FLOAT =>
        Typed[Float]
      case Schema.Type.DOUBLE =>
        Typed[Double]
      case Schema.Type.BOOLEAN =>
        Typed[Boolean]
      case Schema.Type.NULL =>
        TypedNull
    }
  }

}
