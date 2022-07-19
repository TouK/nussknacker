package pl.touk.nussknacker.engine.avro.encode

import cats.data.Validated.condNel
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema.Type
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.apache.avro.{LogicalTypes, Schema, SchemaCompatibility}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectWithValue, _}
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output.{OutputValidatorError, OutputValidatorExpected, OutputValidatorMissingFieldsError, OutputValidatorRedundantFieldsError, OutputValidatorTypeError}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Try

private[encode] object AvroSchemaExpected {

  private val NullSchema = Schema.create(Schema.Type.NULL)

  def apply(schema: Schema, isNullable: Boolean): AvroSchemaExpected =  {
    val expectedSchema = if (isNullable) {
      Schema.createUnion(NullSchema, schema)
    } else {
      schema
    }

    AvroSchemaExpected(expectedSchema)
  }

}

private[encode] case class AvroSchemaExpected(schema: Schema) extends OutputValidatorExpected {
  override def expected: String = AvroSchemaOutputValidatorPrinter.print(schema)
}

object AvroSchemaOutputValidator {
  private[encode] val SimpleAvroPath = "Data"
}

class AvroSchemaOutputValidator(validationMode: ValidationMode) extends LazyLogging {

  import AvroSchemaOutputValidator._

  import scala.collection.JavaConverters._

  private val nestedObjects = Set(Type.RECORD, Type.MAP, Type.ARRAY)

  private val valid = Validated.Valid(())

  private val longLogicalTypes = Set(
    LogicalTypes.timeMicros(), LogicalTypes.timestampMillis(), LogicalTypes.timestampMicros()
  )

  /**
    * see {@link pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder} for underlying avro types
    */
  def validateTypingResultToSchema(typingResult: TypingResult, parentSchema: Schema)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    validateTypingResult(typingResult, parentSchema, None)(isNullable = false)

  /**
    * isNullable is kind of workaround... In nullable nested schemas ([null, Record] / [null, Map] / [null, List]) we
    * don't want to display information about root nullability and nested property validation - more important are properties.
    * In union nullable nested structure we disable checking root nullability and turn on forwarding information
    * about nullability of root only when we compare generic (Record / Map / List) structure using canBeSubclassOf.
    */
  final private def validateTypingResult(typingResult: TypingResult, schema: Schema, path: Option[String])(implicit isNullable: Boolean): ValidatedNel[OutputValidatorError, Unit] = {
    (typingResult, schema.getType) match {
      case (tc@TypedClass(cl, _), _) if AvroUtils.isSpecificRecord(cl) =>
        validateSpecificRecord(tc, schema, path)
      case (record: TypedObjectTypingResult, Type.RECORD) =>
        validateRecordSchema(record, schema, path)
      case (map: TypedObjectTypingResult, Type.MAP) =>
        validateMapSchema(map, schema, path)
      case (tc@TypedClass(map, _), Type.MAP) if classOf[java.util.Map[_, _]].isAssignableFrom(map)  =>
        validateTypedClassToMapSchema(tc, schema, path)
      case (array@TypedClass(cl, _), Type.ARRAY) if classOf[java.util.List[_]].isAssignableFrom(cl) =>
        validateArraySchema(array, schema, path)
// Right now we can't handle null value.. Typing for null is Unknown and canBeSubclassOf returns for it always true..
//      case (Unknown, _) if !schema.isNullable => //null is converted to Unknown, Unknown is base type and canBeSubclassOf of any Type
//        invalid(typingResult, schema, path)
//      case (Unknown, _) => //situation when we pass null as value and schema is null or can be nullable
//        valid
      case (union: TypedUnion, _) if union.isEmptyUnion && schema.isNullable => //situation when we pass #input witch null schema as value
        valid
      case (typingResult, Type.ENUM) =>
        validateEnum(typingResult, schema, path)
      case (typingResult, Type.FIXED) =>
        validateFixed(typingResult, schema, path)
      case (_, Type.STRING) if schema.getLogicalType == LogicalTypes.uuid() =>
        validateUUID(typingResult, schema, path)
      case (_, Type.INT) if schema.getLogicalType == LogicalTypes.date() =>
        validateClass[java.lang.Integer](typingResult, schema, path)
      case (_, Type.INT) if schema.getLogicalType == LogicalTypes.timeMillis() =>
        validateClass[java.lang.Integer](typingResult, schema, path)
      case (_, Type.LONG) if longLogicalTypes.contains(schema.getLogicalType) =>
        validateClass[java.lang.Long](typingResult, schema, path)
      case (anyTypingResult, Type.UNION) =>
        val results = validateUnionSchema(anyTypingResult, schema, path)
        results
      case (_, _) if AvroUtils.isLogicalType[LogicalTypes.Decimal](schema) =>
        validateClass[ByteBuffer](typingResult, schema, path)
      case (_, _) =>
        canBeSubclassOf(typingResult, schema, path)
    }
  }

  private def validateSpecificRecord(tc: TypedClass, schema: Schema, path: Option[String]) = {
    val valueSchema = AvroUtils.extractAvroSpecificSchema(tc.klass)
    // checkReaderWriterCompatibility is more accurate than our validation with given ValidationMode
    val compatibility = SchemaCompatibility.checkReaderWriterCompatibility(schema, valueSchema)
    if (compatibility.getType == SchemaCompatibilityType.COMPATIBLE) {
      valid
    } else {
      val typingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(valueSchema)
      validateTypingResult(typingResult, schema, path)(isNullable = false)
    }
  }

  private def validateRecordSchema(record: TypedObjectTypingResult, schema: Schema, path: Option[String])(implicit isNullable: Boolean): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    val schemaFields = schema.getFields.asScala.map(field => field.name() -> field).toMap
    val requiredFieldNames = schemaFields.values.filterNot(_.hasDefaultValue).map(_.name())
    val fieldsToValidate: Map[String, TypingResult] = record.fields.filterKeys(schemaFields.contains)

    def prepareFields(fields: Set[String]) = fields.flatMap(buildPath(_, path))

    val requiredFieldsValidation = {
      val missingFields = requiredFieldNames.filterNot(record.fields.contains).toList.sorted.toSet
      condNel(missingFields.isEmpty, (), OutputValidatorMissingFieldsError(prepareFields(missingFields)))
    }

    val schemaFieldsValidation = {
      fieldsToValidate.flatMap{ case (key, value) =>
        val fieldPath = buildPath(key, path)
        schemaFields.get(key).map(f => validateTypingResult(value, f.schema(), fieldPath))
      }.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
    }

    val redundantFieldsValidation = {
      val redundantFields = record.fields.keySet.diff(schemaFields.keySet)
      condNel(validationMode.acceptRedundant || redundantFields.isEmpty, (), OutputValidatorRedundantFieldsError(prepareFields(redundantFields)))
    }

   requiredFieldsValidation combine schemaFieldsValidation combine redundantFieldsValidation
  }

  private def validateTypedClassToMapSchema(map: TypedClass, schema: Schema, path: Option[String]) = {
    map.params match {
      case _ :: value :: Nil =>
        implicit val isNullable = false
        validateTypingResult(value, schema.getValueType, buildPath("*", path, isGeneric = true))
      case _ =>
        canBeSubclassOf(map, schema, path)
    }
  }

  private def validateMapSchema(map: TypedObjectTypingResult, schema: Schema, path: Option[String]) = {
    val schemaFieldsValidation = map.fields.map{ case (key, value) =>
      val fieldPath = buildPath(key, path)
      validateTypingResult(value, schema.getValueType, fieldPath)(isNullable = false)
    }.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)

    schemaFieldsValidation
  }

  private def validateArraySchema(array: TypedClass, schema: Schema, path: Option[String])(implicit isNullable: Boolean) = {
    def validateListElements(params: List[TypingResult]) = {
      params
        .zipWithIndex
        .map { case (el, index) =>
          val pathKey = params match {
            case _ :: Nil => "" //One element list is 'general' object, and we preset it as field[]
            case _ => index.toString
          }

          val elementPath = buildPath(pathKey, path, isGeneric = true)
          validateTypingResult(el, schema.getElementType, elementPath)(isNullable = false)
        }
        .foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
    }

    //Empty array is presented as List(Unknown) or List()
    val isEmptyArray = array.params match {
      case head :: _ => head match {
        case Unknown => true
        case _ => false
      }
      case Nil => true
      case _ => false
    }

    if (isEmptyArray) {
      canBeSubclassOf(array, schema, path)
    } else {
      validateListElements(array.params)
    }

  }

  private def validateUnionSchema(union: TypingResult, schema: Schema, path: Option[String]) = {
    val nestedObjectsInUnion = schema.getTypes.asScala.exists(sch => nestedObjects.contains(sch.getType))

    val results = if (nestedObjectsInUnion) { // we want to go inside object and verify each field.. (we don't verify potential root nullability)
      implicit val isNullable: Boolean = schema.isNullable
      schema.getTypes.asScala.filterNot(_.isNullable).map(validateTypingResult(union, _, path)).toList
    } else {
      implicit val isNullable: Boolean = false
      schema.getTypes.asScala.map(validateTypingResult(union, _, path)).toList
    }

    if (results.exists(_.isValid)) {
      valid
    } else {
      results.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
    }
  }

  private def validateEnum(typingResult: TypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    def isValueValid(typedWithObject: TypedObjectWithValue, schema: Schema): Boolean = {
      val enumValue = typedWithObject.value match {
        case enum: EnumSymbol => Some(`enum`.toString)
        case str: String => Some(str)
        case _ => None
      }

      enumValue.exists(schema.getEnumSymbols.asScala.contains)
    }

    validateWithValue[java.lang.String](isValueValid, typingResult: TypingResult, schema: Schema, path: Option[String])
  }

  private def validateFixed(typingResult: TypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    def isValueValid(typedWithObject: TypedObjectWithValue, schema: Schema): Boolean = {
      val fixedStringValue = typedWithObject.value match {
        case fixed: Fixed => Some(new String(fixed.bytes(), StandardCharsets.UTF_8))
        case str: String => Some(str)
        case _ => None
      }

      fixedStringValue.exists(_.length == schema.getFixedSize)
    }

    validateWithValue[java.lang.String](isValueValid, typingResult: TypingResult, schema: Schema, path: Option[String])
  }

  private def validateUUID(typingResult: TypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    def isValueValid(typedWithObject: TypedObjectWithValue, schema: Schema): Boolean = {
      typedWithObject.value match {
        case _:UUID => true
        case str: String => Try(UUID.fromString(str)).toValidated.isValid
        case _ => false
      }
    }

    validateWithValue[java.lang.String](isValueValid, typingResult: TypingResult, schema: Schema, path: Option[String])
  }

  private def validateWithValue[T:ClassTag](isValueValid: (TypedObjectWithValue, Schema) => Boolean, typingResult: TypingResult, schema: Schema, path: Option[String]) = {
    val typeResult = validateClass[T](typingResult, schema, path)

    typingResult match {
      case obj: TypedObjectWithValue if typeResult.isValid && isValueValid(obj, schema) => valid
      case _: TypedObjectWithValue => invalid(typingResult, schema, path)
      case _ if typeResult.isValid => valid
      case _ => typeResult
    }
  }

  private def validateClass[T:ClassTag](typingResult: TypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorTypeError], Unit] = {
    val schemaAsTypedResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
    val clazz: Class[_] = implicitly[ClassTag[T]].runtimeClass

    (schemaAsTypedResult, typingResult) match {
      case (TypedClass(schemaClass, _), TypedClass(typeClass, _)) if typeClass.equals(schemaClass) => valid
      case (_, TypedClass(typeClass, _)) if clazz.equals(typeClass) => valid
      case (_, TypedObjectWithValue(underlying, _)) if underlying.klass.equals(clazz) => valid
      case _ => invalid(typingResult, schema, path) //we don't use canBeSubclassOf here, because it can return true eg. Integer (Number) vs BigDecimal but Avro doesn't allow for that...
    }
  }

  private def canBeSubclassOf(objTypingResult: TypingResult, schema: Schema, path: Option[String])(implicit isNullable: Boolean = false): ValidatedNel[OutputValidatorError, Unit] = {
      val schemaAsTypedResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
      condNel(objTypingResult.canBeSubclassOf(schemaAsTypedResult), (),
        OutputValidatorTypeError(path.getOrElse(SimpleAvroPath), objTypingResult, AvroSchemaExpected(schema, isNullable))
      )
  }

  private def invalid(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorTypeError, Nothing] =
    Validated.invalidNel(OutputValidatorTypeError(path.getOrElse(SimpleAvroPath), typingResult, AvroSchemaExpected(schema)))

  private def buildPath(key: String, path: Option[String], isGeneric: Boolean = false) = Some(
    path.map(p => if(isGeneric) s"$p[$key]" else s"$p.$key").getOrElse(key)
  )
}
