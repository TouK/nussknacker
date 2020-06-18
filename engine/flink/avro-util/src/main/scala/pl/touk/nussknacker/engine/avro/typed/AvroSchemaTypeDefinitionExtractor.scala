package pl.touk.nussknacker.engine.avro.typed

import java.nio.ByteBuffer

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.{GenericData, GenericRecord}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}

/**
  * Right now we're doing approximate type generation to avoid false positives in validation,
  * so now we add option to skip nullable fields.
  *
  * @TODO In future should do it in another way
  *
  * @param skippNullableFields
  */
class AvroSchemaTypeDefinitionExtractor(skippNullableFields: Boolean) {

  import collection.JavaConverters._

  // see BestEffortAvroEncoder for underlying avro types
  def typeDefinition(schema: Schema, possibleTypes: Set[TypedClass]): TypingResult = {
    schema.getType match {
      case Schema.Type.RECORD => {
        val fields = schema
          .getFields
          .asScala
          //@TODO: Maybe Optional field should be marked when it has defaultValue?
          .filterNot(field => skippNullableFields && field.schema().isNullable && field.hasDefaultValue)
          .map(field => field.name() -> typeDefinition(field.schema(), possibleTypes))
          .toMap

        Typed(possibleTypes.map(pt => TypedObjectTypingResult(fields, pt)))
      }
      case Schema.Type.ENUM =>  //It's should by Union, because output can store map with string for ENUM
        Typed(Set(Typed.typedClass[EnumSymbol], Typed.typedClass[CharSequence]))
      case Schema.Type.ARRAY =>
        Typed.genericTypeClass[java.util.List[_]](List(typeDefinition(schema.getElementType, possibleTypes)))
      case Schema.Type.MAP =>
        Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[CharSequence], typeDefinition(schema.getValueType, possibleTypes)))
      case Schema.Type.UNION =>
        val childTypeDefinitions = schema.getTypes.asScala.map(sch => typeDefinition(sch, possibleTypes)).toSet
        Typed(childTypeDefinitions)
      case Schema.Type.FIXED =>
        Typed[GenericData.Fixed]
      case Schema.Type.STRING =>
        Typed[CharSequence]
      case Schema.Type.BYTES =>
        Typed[ByteBuffer]
      case Schema.Type.INT =>
        Typed[Integer]
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

  private lazy val withoutOptionallyFieldsExtractor = new AvroSchemaTypeDefinitionExtractor(skippNullableFields = true)

  private lazy val withOptionallyFieldsExtractor = new AvroSchemaTypeDefinitionExtractor(skippNullableFields = false)

  def typeDefinitionWithoutNullableFields(schema: Schema, possibleTypes: Set[TypedClass]): TypingResult =
    withoutOptionallyFieldsExtractor.typeDefinition(schema, possibleTypes)

  def typeDefinition(schema: Schema): TypingResult =
    withOptionallyFieldsExtractor.typeDefinition(schema, DefaultPossibleTypes)
}
