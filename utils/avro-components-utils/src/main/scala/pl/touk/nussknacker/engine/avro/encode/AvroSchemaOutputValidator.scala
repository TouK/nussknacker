package pl.touk.nussknacker.engine.avro.encode

import cats.data.Validated.{Invalid, condNel}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema.Type
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.apache.avro.{LogicalTypes, Schema, SchemaCompatibility}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output._

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Try

private[encode] case class AvroSchemaExpected(schema: Schema) extends OutputValidatorExpected {
  override def expected: String = AvroSchemaOutputValidatorPrinter.print(schema)
}

object AvroSchemaOutputValidator {
  private[encode] val SimpleAvroPath = "Data"
}

class AvroSchemaOutputValidator(validationMode: ValidationMode) extends LazyLogging {

  import cats.implicits.{catsStdInstancesForList, toTraverseOps}
  import AvroSchemaOutputValidator._
  import scala.collection.JavaConverters._

  private val valid = Validated.Valid(())

  private val longLogicalTypes = Set(
    LogicalTypes.timeMicros(), LogicalTypes.timestampMillis(), LogicalTypes.timestampMicros()
  )

  /**
    * see {@link pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder} for underlying avro types
    */
  def validateTypingResultToSchema(typingResult: TypingResult, parentSchema: Schema)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    validateTypingResult(typingResult, parentSchema, None)

  final private def validateTypingResult(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    (typingResult, schema.getType) match {
      case (tc@TypedClass(cl, _), _) if AvroUtils.isSpecificRecord(cl) =>
        validateSpecificRecord(tc, schema, path)
      case (typingResult: TypedObjectTypingResult, Type.RECORD) =>
        validateRecordSchema(typingResult, schema, path)
      case (typingResult, Type.MAP) =>
        validateMapSchema(typingResult, schema, path)
      case (tc@TypedClass(cl, _), Type.ARRAY) if classOf[java.util.List[_]].isAssignableFrom(cl) =>
        validateArraySchema(tc, schema, path)
      case (_@TypedNull, _) if !schema.isNullable =>
        invalid(typingResult, schema, path)
      case (_@TypedNull, _) if schema.isNullable =>
        valid
      case (typingResult, Type.ENUM) =>
        validateEnum(typingResult, schema, path)
      case (typingResult, Type.FIXED) =>
        validateFixed(typingResult, schema, path)
      case (_, Type.STRING) if schema.getLogicalType == LogicalTypes.uuid() =>
        validateUUID(typingResult, schema, path)
      case (_, Type.INT) if schema.getLogicalType == LogicalTypes.date() =>
        validateSchemaWithBaseType[java.lang.Integer](typingResult, schema, path)
      case (_, Type.INT) if schema.getLogicalType == LogicalTypes.timeMillis() =>
        validateSchemaWithBaseType[java.lang.Integer](typingResult, schema, path)
      case (_, Type.LONG) if longLogicalTypes.contains(schema.getLogicalType) =>
        validateSchemaWithBaseType[java.lang.Long](typingResult, schema, path)
      case (typingResult, Type.UNION) =>
        validateUnionSchema(typingResult, schema, path)
      case (_, _) if AvroUtils.isLogicalType[LogicalTypes.Decimal](schema) =>
        validateSchemaWithBaseType[ByteBuffer](typingResult, schema, path)
      case (_, _) =>
        canBeSubclassOf(typingResult, schema, path)
    }
  }

  private def validateSpecificRecord(typedClass: TypedClass, schema: Schema, path: Option[String]) = {
    val valueSchema = AvroUtils.extractAvroSpecificSchema(typedClass.klass)
    // checkReaderWriterCompatibility is more accurate than our validation with given ValidationMode
    val compatibility = SchemaCompatibility.checkReaderWriterCompatibility(schema, valueSchema)
    if (compatibility.getType == SchemaCompatibilityType.COMPATIBLE) {
      valid
    } else {
      val typingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(valueSchema)
      validateTypingResult(typingResult, schema, path)
    }
  }

  private def validateRecordSchema(typingResult: TypedObjectTypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    val schemaFields = schema.getFields.asScala.map(field => field.name() -> field).toMap
    val requiredFieldNames = schemaFields.values.filterNot(_.hasDefaultValue).map(_.name())
    val fieldsToValidate: Map[String, TypingResult] = typingResult.fields.filterKeys(schemaFields.contains)

    def prepareFields(fields: Set[String]) = fields.flatMap(buildPath(_, path))

    val requiredFieldsValidation = {
      val missingFields = requiredFieldNames.filterNot(typingResult.fields.contains).toList.sorted.toSet
      condNel(missingFields.isEmpty, (), OutputValidatorMissingFieldsError(prepareFields(missingFields)))
    }

    val schemaFieldsValidation = {
      fieldsToValidate.flatMap{ case (key, value) =>
        val fieldPath = buildPath(key, path)
        schemaFields.get(key).map(f => validateTypingResult(value, f.schema(), fieldPath))
      }.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
    }

    val redundantFieldsValidation = {
      val redundantFields = typingResult.fields.keySet.diff(schemaFields.keySet)
      condNel(redundantFields.isEmpty || validationMode.acceptRedundant, (), OutputValidatorRedundantFieldsError(prepareFields(redundantFields)))
    }

   requiredFieldsValidation combine schemaFieldsValidation combine redundantFieldsValidation
  }

  private def validateMapSchema(typingResult: TypingResult, schema: Schema, path: Option[String]) = {
    def isMap(klass: Class[_]) = classOf[java.util.Map[_, _]].isAssignableFrom(klass)

    typingResult match {
      case _@TypedClass(klass, key :: value :: Nil) if isMap(klass) =>
        //Map keys are assumed to be strings: https://avro.apache.org/docs/current/spec.html#Maps
        condNel(key.canBeSubclassOf(Typed.apply[java.lang.String]), (), typeError(typingResult, schema, path)).andThen(_ =>
          validateTypingResult(value, schema.getValueType, buildPath("*", path, useIndexer = true))
        )
      case map@TypedClass(klass, _) if isMap(klass) =>
        throw new IllegalArgumentException(s"Illegal typing Map: $map.")
      case _@TypedObjectTypingResult(fields, _, _) =>
        fields.map{ case (key, value) =>
          val fieldPath = buildPath(key, path)
          validateTypingResult(value, schema.getValueType, fieldPath)
        }.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
      case _ =>
        invalid(typingResult, schema, path)
    }
  }

  private def validateArraySchema(typedClass: TypedClass, schema: Schema, path: Option[String]) =
    typedClass.params match {
      case head :: Nil =>
        val elementPath = buildPath("", path, useIndexer = true)
        validateTypingResult(head, schema.getElementType, elementPath)
      case _ =>
        throw new IllegalArgumentException(s"Illegal typing List: $typedClass.")
    }

  private def validateUnionSchema(typingResult: TypingResult, schema: Schema, path: Option[String]) = {
    //check is there only one typing error with exactly same field as path - it means there was checking whole object (without going deeper e.g. List/Map/Record)
    def singleObjectTypingError(errors: NonEmptyList[OutputValidatorError]): Boolean =
      errors.collect{case err: OutputValidatorTypeError => err} match {
        case head :: Nil => path.contains(head.field)
        case _ => false
      }

    def createUnionValidationResults(checkNullability: Boolean): List[ValidatedNel[OutputValidatorError, Unit]] = {
      val schemas = if(checkNullability) schema.getTypes.asScala else schema.getTypes.asScala.filterNot(_.isNullable)
      schemas.map(validateTypingResult(typingResult, _, path)).toList
    }

    def isNullableSchema(schema: Schema): Boolean = schema.getTypes.size() == 2 && schema.isNullable

    def asSingleValidatedResults(results: List[ValidatedNel[OutputValidatorError, Unit]]) =
      if (results.exists(_.isValid)) valid else results.sequence.map(_=> ())

    val unionValidationResults = schema match {
      case sch if isNullableSchema(sch) =>
        val notNullableValidationResults = createUnionValidationResults(checkNullability = false)

        asSingleValidatedResults(notNullableValidationResults) match {
          case Invalid(errors) if singleObjectTypingError(errors) => //when single typing error is true, we have to validate again including nullability
            createUnionValidationResults(checkNullability = true)
          case _ =>
            notNullableValidationResults
        }
      case _ =>
        createUnionValidationResults(checkNullability = true)
    }

    asSingleValidatedResults(unionValidationResults)
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
        case fixed: Fixed => Some(fixed.bytes())
        case buffer: ByteBuffer => Some(buffer.array())
        case bytes: Array[Byte] => Some(bytes)
        case str: String => Some(str.getBytes(StandardCharsets.UTF_8))
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
    val validationTypingResult = validateSchemaWithBaseType[T](typingResult, schema, path)

    validationTypingResult.andThen{ _ =>
      typingResult match {
        case obj: TypedObjectWithValue if isValueValid(obj, schema) => valid
        case _: TypedObjectWithValue => invalid(typingResult, schema, path)
        case _ => validationTypingResult
      }
    }

  }

  private def validateSchemaWithBaseType[T:ClassTag](typingResult: TypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorTypeError], Unit] = {
    val schemaAsTypedResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
    val clazz: Class[_] = implicitly[ClassTag[T]].runtimeClass

    (schemaAsTypedResult, typingResult) match {
      case (TypedClass(schemaClass, _), TypedClass(typeClass, _)) if typeClass == schemaClass => valid
      case (_, TypedClass(typeClass, _)) if clazz == typeClass => valid
      case (_, TypedObjectWithValue(underlying, _)) if clazz == underlying.klass => valid
      case _ => invalid(typingResult, schema, path)
    }
  }

  /**
    * TODO: Consider verification class instead of using .canBeSubclassOf from Typing - at avro we want to avoid:
    * * Unknown.canBeSubclassOf(Any) => true
    * * Long.canBeSubclassOf(Integer) => true
    * Should we use strict verification at avro?
    */
  private def canBeSubclassOf(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
      val schemaAsTypedResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
      condNel(typingResult.canBeSubclassOf(schemaAsTypedResult), (),
        OutputValidatorTypeError(path.getOrElse(SimpleAvroPath), typingResult, AvroSchemaExpected(schema))
      )
  }

  private def invalid(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorTypeError, Nothing] =
    Validated.invalidNel(typeError(typingResult, schema, path))

  private def typeError(typingResult: TypingResult, schema: Schema, path: Option[String]) =
    OutputValidatorTypeError(path.getOrElse(SimpleAvroPath), typingResult, AvroSchemaExpected(schema))

  private def buildPath(key: String, path: Option[String], useIndexer: Boolean = false) = Some(
    path.map(p => if(useIndexer) s"$p[$key]" else s"$p.$key").getOrElse(key)
  )

}
