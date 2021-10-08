package pl.touk.nussknacker.engine.avro.typed

import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.{LogicalTypes, Schema}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.avro.schema.AvroStringSettings

import java.nio.ByteBuffer
import java.time.{Instant, LocalDate, LocalTime}
import java.util.UUID

/**
  * Right now we're doing approximate type generation to avoid false positives in validation,
  * so now we add option to skip nullable fields.
  *
  * @TODO In future should do it in another way
  *
  * @param skipOptionalFields
  */
class AvroSchemaTypeDefinitionExtractor(skipOptionalFields: Boolean) {

  import collection.JavaConverters._

  // see BestEffortAvroEncoder for underlying avro types
  def typeDefinition(schema: Schema, possibleTypes: Set[TypedClass]): TypingResult = {
    schema.getType match {
      case Schema.Type.RECORD => {
        val fields = schema
          .getFields
          .asScala
          //Field is marked as optional when field has default value
          .filterNot(field => skipOptionalFields && field.hasDefaultValue)
          .map(field => field.name() -> typeDefinition(field.schema(), possibleTypes))
          .toList

        Typed(possibleTypes.map(pt => TypedObjectTypingResult(fields, pt)))
      }
      case Schema.Type.ENUM =>  //It's should by Union, because output can store map with string for ENUM
        Typed(Set(Typed.typedClass[EnumSymbol], AvroStringSettings.stringTypingResult))
      case Schema.Type.ARRAY =>
        Typed.genericTypeClass[java.util.List[_]](List(typeDefinition(schema.getElementType, possibleTypes)))
      case Schema.Type.MAP =>
        Typed.genericTypeClass[java.util.Map[_, _]](List(AvroStringSettings.stringTypingResult, typeDefinition(schema.getValueType, possibleTypes)))
      case Schema.Type.UNION =>
        val childTypeDefinitions = schema.getTypes.asScala.map(sch => typeDefinition(sch, possibleTypes)).toSet
        Typed(childTypeDefinitions)
      // See org.apache.avro.UUIDConversion
      case Schema.Type.STRING if schema.getLogicalType == LogicalTypes.uuid() =>
        Typed[UUID]
      // See org.apache.avro.DecimalConversion
      case Schema.Type.BYTES | Schema.Type.FIXED if schema.getLogicalType != null && schema.getLogicalType.isInstanceOf[LogicalTypes.Decimal] =>
        Typed[java.math.BigDecimal]
      case Schema.Type.STRING =>
        val baseType = AvroStringSettings.stringTypingResult
        Option(schema.getProp(AvroSchemaTypeDefinitionExtractor.dictIdProperty)).map(Typed.taggedDictValue(baseType, _)).getOrElse(baseType)
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
      case Schema.Type.LONG if schema.getLogicalType == LogicalTypes.timestampMillis() || schema.getLogicalType == LogicalTypes.timestampMicros() =>
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
        Typed.empty
    }
  }
}

object AvroSchemaTypeDefinitionExtractor {

  val DefaultPossibleTypes: Set[TypedClass] = Set(Typed.typedClass[GenericRecord])

  val ExtendedPossibleTypes: Set[TypedClass] = DefaultPossibleTypes ++ Set(Typed.typedClass[java.util.Map[String, Any]])

  val dictIdProperty = "nkDictId"

  private lazy val withoutOptionallyFieldsExtractor = new AvroSchemaTypeDefinitionExtractor(skipOptionalFields = true)

  private lazy val withOptionallyFieldsExtractor = new AvroSchemaTypeDefinitionExtractor(skipOptionalFields = false)

  def typeDefinitionWithoutNullableFields(schema: Schema, possibleTypes: Set[TypedClass]): TypingResult =
    withoutOptionallyFieldsExtractor.typeDefinition(schema, possibleTypes)

  def typeDefinition(schema: Schema): TypingResult =
    withOptionallyFieldsExtractor.typeDefinition(schema, DefaultPossibleTypes)
}
