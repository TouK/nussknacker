package pl.touk.nussknacker.ui.api

import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toTraverseOps
import io.circe.Json
import org.apache.commons.lang3
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.typed.TypingType.TypingType
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.typed.{SimpleObjectEncoder, TypingType}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.{SProductField, SchemaWithValue}
import sttp.tapir.{FieldName, Schema, SchemaType}

object typingDtos {
//  sealed trait TypingResultDto {
//    def valueOpt: Option[Any]
//
//    def withoutValue: TypingResultDto
//
//    def display: String
//  }
//
//  sealed trait KnownTypingResultDto extends TypingResultDto
//
//  sealed trait SingleTypingResultDto extends KnownTypingResultDto {
//    override def withoutValue: SingleTypingResultDto
//    def objType: TypedClassDto
//  }
//
//  object TypedObjectTypingResultDto {
//
//    def apply(
//               typedObjectTypingResult: TypedObjectTypingResult
//             )(implicit modelData: ModelData): TypedObjectTypingResultDto = {
//      new TypedObjectTypingResultDto(
//        typedObjectTypingResult.fields.map { case (key, typ) => (key, toDto(typ)) },
//        TypedClassDto.apply(typedObjectTypingResult.objType),
//        typedObjectTypingResult.additionalInfo
//      )
//    }
//  }
//
//  case class TypedObjectTypingResultDto(
//     fields: Map[String, TypingResultDto],
//     objType: TypedClassDto,
//     additionalInfo: Map[String, AdditionalDataValue]
//   ) extends SingleTypingResultDto {
//    override def valueOpt: Option[Map[String, Any]] =
//      fields.map { case (k, v) => v.valueOpt.map((k, _)) }.toList.sequence.map(Map(_: _*))
//
//    override def withoutValue: TypedObjectTypingResultDto =
//      TypedObjectTypingResultDto(fields.mapValuesNow(_.withoutValue), objType, additionalInfo)
//
//    override def display: String =
//      fields.map { case (name, typ) => s"$name: ${typ.display}" }.toList.sorted.mkString("Record{", ", ", "}")
//  }
//
//  case class TypedDictDto(dictId: String, valueType: SingleTypingResultDto) extends SingleTypingResultDto {
//    override def objType: TypedClassDto = valueType.objType
//
//    override def valueOpt: Option[Any] = valueType.valueOpt
//
//    override def withoutValue: TypedDictDto = TypedDictDto(dictId, valueType.withoutValue)
//
//    override def display: String = s"Dict(id=$dictId)"
//
//  }
//
//  sealed trait TypedObjectWithDataDto extends SingleTypingResultDto {
//    def underlying: SingleTypingResultDto
//
//    def data: Any
//
//    override def objType: TypedClassDto = underlying.objType
//  }
//
//  case class TypedTaggedValueDto(underlying: SingleTypingResultDto, tag: String) extends TypedObjectWithDataDto {
//    override def data: String = tag
//
//    override def valueOpt: Option[Any] = underlying.valueOpt
//
//    override def withoutValue: TypedTaggedValueDto = TypedTaggedValueDto(underlying.withoutValue, tag)
//
//    override def display: String = s"${underlying.display} @ $tag"
//
//  }
//
//  // todo
//  case class TypedObjectWithValueDto private[typingDtos](
//    underlying: TypedClassDto,
//    value: Any,
//    waitRefClazzName: Option[String] = None,
//    waitValue: Json = Json.Null
//  ) extends TypedObjectWithDataDto {
//    val maxDataDisplaySize: Int = 15
//    val maxDataDisplaySizeWithDots: Int = maxDataDisplaySize - "...".length
//
//    override def data: Any = value
//
//    override def valueOpt: Option[Any] = Some(value)
//
//    override def withoutValue: SingleTypingResultDto = underlying.withoutValue
//
//    override def display: String = {
//      val dataString = data.toString
//      val shortenedDataString =
//        if (dataString.length <= maxDataDisplaySize) dataString
//        else dataString.take(maxDataDisplaySizeWithDots) ++ "..."
//      s"${underlying.display}($shortenedDataString)"
//    }
//
//  }
//
//  case object TypedNullDto extends TypingResultDto {
//    override def withoutValue: TypedNullDto.type = TypedNullDto
//
//    // this value is intentionally `Some(null)` (and not `None`), as TypedNull represents null value
//    override val valueOpt: Some[Null] = Some(null)
//    override val display = "Null"
//  }
//
//  case object UnknownDto extends TypingResultDto {
//    override def withoutValue: UnknownDto.type = UnknownDto
//
//    override val valueOpt: None.type = None
//
//    override val display = "Unknown"
//
//  }
//
//  case class TypedUnionDto private[typingDtos](possibleTypes: Set[SingleTypingResultDto]) extends KnownTypingResultDto {
//
//    assert(
//      possibleTypes.size != 1,
//      "TypedUnion should has zero or more than one possibleType - in other case should be used TypedObjectTypingResult or TypedClass"
//    )
//
//    override def valueOpt: None.type = None
//
//    override def withoutValue: TypingResultDto = this
//
//    override val display: String = possibleTypes.toList match {
//      case Nil => "EmptyUnion"
//      case many => many.map(_.display).mkString(" | ")
//    }
//
//  }
//
//  object TypedClassDto {
//
//    private[typingDtos] def apply(klass: Class[_], params: List[TypingResultDto]) = new TypedClassDto(klass, params)
//
//    def apply(typedClass: TypedClass)(implicit modelData: ModelData): TypedClassDto = {
//      new TypedClassDto(typedClass.klass, typedClass.params.map(typ => toDto(typ)))
//    }
//
//    def apply(onWaitClass: String, paramsOnWait: Json): TypedClassDto = {
//      new TypedClassDto(null, null, onWaitClass, paramsOnWait)
//    }
//
//  }
//
//  // todo
//  case class TypedClassDto private[typingDtos](
//                                                      klass: Class[_],
//                                                      params: List[TypingResultDto],
//                                                      classOnWait: String = "",
//                                                      paramsOnWait: Json = Json.Null
//                                                    ) extends SingleTypingResultDto {
//    override val valueOpt: None.type = None
//
//    override def withoutValue: TypedClassDto = this
//
//    override def display: String = {
//      val className =
//        if (klass.isArray) "Array"
//        else ReflectUtils.simpleNameWithoutSuffix(klass)
//      if (params.nonEmpty) s"$className[${params.map(_.display).mkString(",")}]"
//      else s"$className"
//    }
//
//    override def objType: TypedClassDto = this
//
//    def primitiveClass: Class[_] = Option(lang3.ClassUtils.wrapperToPrimitive(klass)).getOrElse(klass)
//
//  }
//
//  object SingleTypingResultDto {
//
//    def apply(singleTypingResult: SingleTypingResult)(implicit modelData: ModelData): SingleTypingResultDto = {
//      toDto(singleTypingResult).asInstanceOf[SingleTypingResultDto]
//    }
//
//  }
//
//
//  object TypedDictDto {
//
//    def apply(typedDict: TypedDict)(implicit modelData: ModelData): TypedDictDto = {
//      new TypedDictDto(typedDict.dictId, SingleTypingResultDto.apply(typedDict.valueType))
//    }
//
//  }
//
//  object TypedTaggedValueDto {
//    def apply(typedTaggedValue: TypedTaggedValue)(implicit modelData: ModelData): TypedTaggedValueDto = {
//      TypedTaggedValueDto(SingleTypingResultDto.apply(typedTaggedValue.underlying), typedTaggedValue.tag)
//    }
//
//  }
//
//
//  object TypedObjectWithValueDto {
//    def apply(typedObjectWithValue: TypedObjectWithValue)(implicit modelData: ModelData): TypedObjectWithValueDto = {
//      new TypedObjectWithValueDto(
//        TypedClassDto.apply(typedObjectWithValue.underlying),
//        typedObjectWithValue.value,
//        Some(typedObjectWithValue.underlying.klass.getName),
//        waitValue = SimpleObjectEncoder.encode(
//          typedObjectWithValue.underlying,
//          typedObjectWithValue.value
//        ) match {
//          case Valid(value) => value
//          case Invalid(_) => Json.Null
//        }
//      )
//    }
//
//  }
//
//  object TypedUnionDto {
//
//    def apply(typedUnion: TypedUnion)(implicit modelData: ModelData): TypedUnionDto = {
//      TypedUnionDto(typedUnion.possibleTypes.map(typ => SingleTypingResultDto.apply(typ)))
//    }
//  }
}

object typingDtoSchemas {
  import typingDtos._

  // Pewnie można by to opakować w jakiś obiekt i do niego jakąś schemę zrobic
  implicit lazy val jsonSchema: Schema[Json] = Schema.any
//  implicit val typedClassSchema: Schema[TypedClass] = Schema.any
  implicit lazy val typingResult: Schema[TypingResult] = Schema.derived

  implicit lazy val singleTypingResultSchema: Schema[SingleTypingResult] =
    Schema.derived.hidden(true)

  implicit lazy val additionalDataValueSchema: Schema[AdditionalDataValue] = Schema.derived

  implicit lazy val typedObjectTypingResultSchema: Schema[TypedObjectTypingResult] = {
    Schema(
      SchemaType.SProduct(
        sProductFieldForDisplayAndType :::
          List(
            SProductField[String, Map[String, TypingResult]](
              FieldName("fields"),
              Schema.schemaForMap[TypingResult],
              _ => None
            )
          ) :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedObjectTypingResult"))
    )
      .title("TypedObjectTypingResult")
      .as
  }

  implicit lazy val typedDictSchema: Schema[TypedDict] = {
    final case class Dict(id: String, valueType: TypedTaggedValue)
    lazy val dictSchema: Schema[Dict] = Schema.derived
    Schema(
      SchemaType.SProduct(
        sProductFieldForDisplayAndType :::
          List(SProductField[String, Dict](FieldName("dict"), dictSchema, _ => None))
      ),
      Some(SName("TypedDict"))
    )
      .title("TypedDict")
      .as
  }

  implicit lazy val typedObjectWithDataSchema: Schema[TypedObjectWithData] =
    Schema.derived.hidden(true)

  implicit lazy val typedTaggedSchema: Schema[TypedTaggedValue] = {
    Schema(
      SchemaType.SProduct(
        List(SProductField[String, String](FieldName("tag"), Schema.string, tag => Some(tag))) :::
          sProductFieldForDisplayAndType :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedTaggedValue"))
    )
      .title("TypedTaggedValue")
      .as
  }

  implicit lazy val typedObjectSchema: Schema[TypedObjectWithValue] = {
    Schema(
      SchemaType.SProduct(
        List(SProductField[String, Any](FieldName("value"), Schema.any, value => Some(value))) :::
          sProductFieldForDisplayAndType :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedObjectWithValue"))
    )
      .title("TypedObjectWithValue")
      .as
  }

  implicit lazy val typedNullSchema: Schema[TypedNull.type] =
    Schema.derived.name(Schema.SName("TypedNull")).title("TypedNull")

  implicit lazy val unknownSchema: Schema[Unknown.type] =
    Schema.derived.name(Schema.SName("Unknown")).title("Unknown")

  implicit lazy val unionSchema: Schema[TypedUnion] = {
    Schema(
      SchemaType.SProduct(
        sProductFieldForDisplayAndType :::
          List(
            SProductField[String, List[TypingResult]](
              FieldName("union"),
              Schema.schemaForArray[TypingResult].as,
              _ => Some(List(Unknown))
            )
          )
      ),
      Some(Schema.SName("TypedUnion"))
    )
      .title("TypedUnion")
      .as
  }

  implicit lazy val typedClassSchema: Schema[TypedClass] = {
    Schema(
      SchemaType.SProduct(
        sProductFieldForDisplayAndType :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedClass"))
    )
      .title("TypedClass")
      .as
  }

  private lazy val sProductFieldForDisplayAndType: List[SProductField[String]] = {
    List(
      SProductField[String, String](FieldName("display"), Schema.string, display => Some(display)),
      SProductField[String, TypingType](
        FieldName("type"),
        Schema.derivedEnumerationValue,
        _ => Some(TypingType.Unknown)
      )
    )
  }

  private lazy val sProductFieldForKlassAndParams: List[SProductField[String]] = {
    lazy val typingResultSchema: Schema[TypingResult] = Schema.derived
    List(
      SProductField[String, String](FieldName("refClazzName"), Schema.string, refClazzName => Some(refClazzName)),
      SProductField[String, List[TypingResult]](
        FieldName("params"),
        Schema.schemaForIterable[TypingResult, List](typingResultSchema),
        _ => Some(List(Unknown))
      )
    )
  }

}
