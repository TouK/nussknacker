package pl.touk.nussknacker.ui.api

import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toTraverseOps
import derevo.derive
import io.circe._
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import sttp.tapir.{FieldName, Schema, SchemaType}
import sttp.tapir.derevo.schema
import org.apache.commons.lang3
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.typed.SimpleObjectEncoder
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.TypingResultDtoHelpers.toDto
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.SProductField

object typingDto {

  @derive(schema)
  sealed trait TypingResultDto {
    def valueOpt: Option[Any]

    def withoutValue: TypingResultDto

    def display: String
  }

  @derive(schema)
  sealed trait KnownTypingResultDto extends TypingResultDto {
    implicit val knownTypingResultDtoSchema: Schema[KnownTypingResultDto] =
      Schema.derived.hidden(true)
  }

  sealed trait SingleTypingResultDto extends KnownTypingResultDto {
    override def withoutValue: SingleTypingResultDto
    def objType: TypedClassDto

    implicit val singleTypingResultDtoDtoSchema: Schema[SingleTypingResultDto] =
      Schema.derived.hidden(true)
  }

  object SingleTypingResultDto {

    def apply(singleTypingResult: SingleTypingResult)(implicit modelData: ModelData): SingleTypingResultDto = {
      toDto(singleTypingResult).asInstanceOf[SingleTypingResultDto]
    }

  }

  object TypedObjectTypingResultDto {

    def apply(
        typedObjectTypingResult: TypedObjectTypingResult
    )(implicit modelData: ModelData): TypedObjectTypingResultDto = {
      new TypedObjectTypingResultDto(
        typedObjectTypingResult.fields.map { case (key, typ) => (key, toDto(typ)) },
        TypedClassDto.apply(typedObjectTypingResult.objType),
        typedObjectTypingResult.additionalInfo
      )
    }

    implicit lazy val additionalDataValueSchema: Schema[AdditionalDataValue] = Schema.derived

    implicit lazy val typedObjectTypingResultDtoSchema: Schema[TypedObjectTypingResultDto] = {
      implicit val typingResultDtoSchema: Schema[TypingResultDto] = Schema.derived
      Schema(
        SchemaType.SProduct(
          List(
            SProductField[String, String](FieldName("display"), Schema.string, display => Some(display)),
            SProductField[String, String](FieldName("type"), Schema.string, typ => Some(typ)),
            SProductField[String, Map[String, TypingResultDto]](FieldName("fields"), Schema.any, sth => None),
            SProductField[String, String](FieldName("refClazzName"), Schema.string, refClazzName => Some(refClazzName)),
            SProductField[String, List[TypingResultDto]](
              FieldName("params"),
              Schema.schemaForArray[TypingResultDto].as,
              sth => Some(List(UnknownDto))
            )
          )
        ),
        Some(SName("TypedObjectTypingResultDto"))
      )
        .title("TypedObjectTypingResultDto")
        .as
    }

  }

  case class TypedObjectTypingResultDto(
      fields: Map[String, TypingResultDto],
      objType: TypedClassDto,
      additionalInfo: Map[String, AdditionalDataValue]
  ) extends SingleTypingResultDto {
    override def valueOpt: Option[Map[String, Any]] =
      fields.map { case (k, v) => v.valueOpt.map((k, _)) }.toList.sequence.map(Map(_: _*))
    override def withoutValue: TypedObjectTypingResultDto =
      TypedObjectTypingResultDto(fields.mapValuesNow(_.withoutValue), objType, additionalInfo)

    override def display: String =
      fields.map { case (name, typ) => s"$name: ${typ.display}" }.toList.sorted.mkString("Record{", ", ", "}")
  }

  case class TypedDictDto(dictId: String, valueType: SingleTypingResultDto) extends SingleTypingResultDto {
    override def objType: TypedClassDto     = valueType.objType
    override def valueOpt: Option[Any]      = valueType.valueOpt
    override def withoutValue: TypedDictDto = TypedDictDto(dictId, valueType.withoutValue)
    override def display: String            = s"Dict(id=$dictId)"

  }

  object TypedDictDto {

    implicit lazy val typedDictDtoSchema: Schema[TypedDictDto] = {
      final case class Dict(id: String, valueType: TypedTaggedValueDto)
      val dictSchema: Schema[Dict]                             = Schema.derived
      implicit val typingResultSchema: Schema[TypingResultDto] = Schema.derived[TypingResultDto]
      Schema(
        SchemaType.SProduct(
          List(
            SProductField[String, String](FieldName("display"), Schema.string, display => Some(display)),
            SProductField[String, String](FieldName("type"), Schema.string, typ => Some(typ)),
            SProductField[String, Dict](FieldName("dict"), dictSchema, dict => None),
          )
        ),
        Some(SName("TypedDictDto"))
      )
        .title("TypedDictDto")
        .as
    }

    def apply(typedDict: TypedDict)(implicit modelData: ModelData): TypedDictDto = {
      new TypedDictDto(typedDict.dictId, SingleTypingResultDto.apply(typedDict.valueType))
    }

  }

  @derive(schema)
  sealed trait TypedObjectWithDataDto extends SingleTypingResultDto {
    def underlying: SingleTypingResultDto
    def data: Any
    override def objType: TypedClassDto = underlying.objType

    implicit val typedObjectWithDataDtoSchema: Schema[TypedObjectWithDataDto] =
      Schema.derived.hidden(true)
  }

  case class TypedTaggedValueDto(underlying: SingleTypingResultDto, tag: String) extends TypedObjectWithDataDto {
    override def data: String = tag

    override def valueOpt: Option[Any] = underlying.valueOpt

    override def withoutValue: TypedTaggedValueDto = TypedTaggedValueDto(underlying.withoutValue, tag)

    override def display: String = s"${underlying.display} @ $tag"

  }

  object TypedTaggedValueDto {

    implicit lazy val typedTaggedSchema: Schema[TypedTaggedValueDto] = {
      implicit val typingResultSchema: Schema[TypingResultDto] = Schema.derived[TypingResultDto]
      Schema(
        SchemaType.SProduct(
          List(
            SProductField[String, String](FieldName("tag"), Schema.string, tag => Some(tag)),
            SProductField[String, String](FieldName("display"), Schema.string, display => Some(display)),
            SProductField[String, String](FieldName("type"), Schema.string, typ => Some(typ)),
            SProductField[String, String](FieldName("refClazzName"), Schema.string, refClazzName => Some(refClazzName)),
            SProductField[String, List[TypingResultDto]](
              FieldName("params"),
              Schema.schemaForArray[TypingResultDto].as,
              sth => Some(List(UnknownDto))
            )
          )
        ),
        Some(SName("TypedTaggedValueDto"))
      )
        .title("TypedTaggedValueDto")
        .as
    }

    def apply(typedTaggedValue: TypedTaggedValue)(implicit modelData: ModelData): TypedTaggedValueDto = {
      TypedTaggedValueDto(SingleTypingResultDto.apply(typedTaggedValue.underlying), typedTaggedValue.tag)
    }

  }

  case class TypedObjectWithValueDto private[typingDto] (
      underlying: TypedClassDto,
      value: Any,
      waitRefClazzName: String = "",
      waitValue: Json = Json.fromString("")
  ) extends TypedObjectWithDataDto {
    val maxDataDisplaySize: Int         = 15
    val maxDataDisplaySizeWithDots: Int = maxDataDisplaySize - "...".length

    override def data: Any = value

    override def valueOpt: Option[Any] = Some(value)

    override def withoutValue: SingleTypingResultDto = underlying.withoutValue

    override def display: String = {
      val dataString = data.toString
      val shortenedDataString =
        if (dataString.length <= maxDataDisplaySize) dataString
        else dataString.take(maxDataDisplaySizeWithDots) ++ "..."
      s"${underlying.display}($shortenedDataString)"
    }

  }

  object TypedObjectWithValueDto {

    implicit lazy val typedObjectDtoSchema: Schema[TypedObjectWithValueDto] = {
      implicit val typingResultSchema: Schema[TypingResultDto] = Schema.derived[TypingResultDto]
      Schema(
        SchemaType.SProduct(
          List(
            SProductField[String, Any](FieldName("value"), Schema.any, value => Some(value)),
            SProductField[String, String](FieldName("display"), Schema.string, display => Some(display)),
            SProductField[String, String](FieldName("type"), Schema.string, typ => Some(typ)),
            SProductField[String, String](FieldName("refClazzName"), Schema.string, refClazzName => Some(refClazzName)),
            SProductField[String, List[TypingResultDto]](
              FieldName("params"),
              Schema.schemaForArray[TypingResultDto].as,
              sth => Some(List(UnknownDto))
            )
          )
        ),
        Some(SName("TypedObjectWithValueDto"))
      )
        .title("TypedObjectWithValueDto")
        .as
    }

    def apply(typedObjectWithValue: TypedObjectWithValue)(implicit modelData: ModelData): TypedObjectWithValueDto = {
      new TypedObjectWithValueDto(
        TypedClassDto.apply(typedObjectWithValue.underlying),
        typedObjectWithValue.value,
        typedObjectWithValue.underlying.klass.getName,
        waitValue = SimpleObjectEncoder.encode(
          typedObjectWithValue.underlying,
          typedObjectWithValue.value
        ) match {
          case Valid(value) => value
          case Invalid(_)   => Json.fromString("")
        }
      )
    }

  }

  case object TypedNullDto extends TypingResultDto {
    override def withoutValue: TypedNullDto.type = TypedNullDto
    // this value is intentionally `Some(null)` (and not `None`), as TypedNull represents null value
    override val valueOpt: Some[Null] = Some(null)
    override val display              = "Null"

    implicit val typedNullDtoSchema: Schema[TypedNullDto.type] =
      Schema.derived.name(Schema.SName("TypedNullDto")).title("TypedNullDto")
  }

  case object UnknownDto extends TypingResultDto {
    override def withoutValue: UnknownDto.type = UnknownDto

    override val valueOpt: None.type = None

    override val display = "Unknown"

    implicit val unknownDtoSchema: Schema[UnknownDto.type] =
      Schema.derived.name(Schema.SName("UnknownDto")).title("UnknownDto")
  }

  case class TypedUnionDto private[typingDto] (possibleTypes: Set[SingleTypingResultDto]) extends KnownTypingResultDto {

    assert(
      possibleTypes.size != 1,
      "TypedUnion should has zero or more than one possibleType - in other case should be used TypedObjectTypingResult or TypedClass"
    )

    override def valueOpt: None.type = None

    override def withoutValue: TypingResultDto = this

    override val display: String = possibleTypes.toList match {
      case Nil  => "EmptyUnion"
      case many => many.map(_.display).mkString(" | ")
    }

  }

  object TypedUnionDto {
    implicit lazy val unionSchema: Schema[TypedUnionDto] = Schema.any

    def apply(typedUnion: TypedUnion)(implicit modelData: ModelData): TypedUnionDto = {
      TypedUnionDto(typedUnion.possibleTypes.map(typ => SingleTypingResultDto.apply(typ)))
    }

  }

  object TypedClassDto {

    implicit lazy val typedClassDtoSchema: Schema[TypedClassDto] = {
      implicit val typingResultSchema: Schema[TypingResultDto] = Schema.derived[TypingResultDto]
      Schema(
        SchemaType.SProduct(
          List(
            SProductField[String, String](FieldName("display"), Schema.string, display => Some(display)),
            SProductField[String, String](FieldName("type"), Schema.string, typ => Some(typ)),
            SProductField[String, String](FieldName("refClazzName"), Schema.string, refClazzName => Some(refClazzName)),
            SProductField[String, List[TypingResultDto]](
              FieldName("params"),
              Schema.schemaForArray[TypingResultDto].as,
              sth => Some(List(UnknownDto))
            )
          )
        ),
        Some(SName("TypedClassDto"))
      )
        .title("TypedClassDto")
        .as
    }

    private[typingDto] def apply(klass: Class[_], params: List[TypingResultDto]) = new TypedClassDto(klass, params)

    def apply(typedClass: TypedClass)(implicit modelData: ModelData): TypedClassDto = {
      new TypedClassDto(typedClass.klass, typedClass.params.map(typ => toDto(typ)))
    }

    def apply(onWaitClass: String, paramsOnWait: Json): TypedClassDto = {
      new TypedClassDto(null, null, onWaitClass, paramsOnWait)
    }

  }

  case class TypedClassDto private[typingDto] (
      klass: Class[_],
      params: List[TypingResultDto],
      classOnWait: String = "",
      paramsOnWait: Json = Json.fromString("")
  ) extends SingleTypingResultDto {
    override val valueOpt: None.type = None

    override def withoutValue: TypedClassDto = this

    override def display: String = {
      val className =
        if (klass.isArray) "Array"
        else ReflectUtils.simpleNameWithoutSuffix(klass)
      if (params.nonEmpty) s"$className[${params.map(_.display).mkString(",")}]"
      else s"$className"
    }

    override def objType: TypedClassDto = this

    def primitiveClass: Class[_] = Option(lang3.ClassUtils.wrapperToPrimitive(klass)).getOrElse(klass)

  }

}
