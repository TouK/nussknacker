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

object typingDtoSchemas {

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
