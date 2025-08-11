package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import io.circe.Json
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.util.Implicits.{RichScalaListMap, RichTupleList}

object InitialJsonFromTypingResultDeterminer {

  private val defaultJsonForDecimalNumber: Json       = Json.fromInt(0)
  private val defaultJsonForFloatingPointNumber: Json = Json.fromDoubleOrNull(0.0)

  private val defaultJsonsForStandardClasses = List[(Class[_], Json)](
    StandardTypesClasses.StringClass  -> Json.fromString(""),
    StandardTypesClasses.BooleanClass -> Json.fromBoolean(false),
    // date types
    StandardTypesClasses.InstantClass        -> Json.fromString("1900-01-01T00:00:00Z"),
    StandardTypesClasses.OffsetDateTimeClass -> Json.fromString("1900-01-01T00:00:00+00:00"),
    StandardTypesClasses.ZonedDateTimeClass  -> Json.fromString("1900-01-01T00:00:00+00:00"),
    StandardTypesClasses.LocalDateTimeClass  -> Json.fromString("1900-01-01T00:00:00"),
    StandardTypesClasses.LocalDateClass      -> Json.fromString("1900-01-01"),
    StandardTypesClasses.LocalTimeClass      -> Json.fromString("00:00:00"),
    StandardTypesClasses.DurationClass       -> Json.fromString("PT0H"),
    StandardTypesClasses.PeriodClass         -> Json.fromString("P0D"),
    StandardTypesClasses.ZoneOffsetClass     -> Json.fromString("+00:00"),
    StandardTypesClasses.ZoneIdClass         -> Json.fromString("UTC"),
    // other supported logical types
    StandardTypesClasses.CurrencyClass -> Json.fromString("USD"),
    StandardTypesClasses.LocaleClass   -> Json.fromString("en"),
    StandardTypesClasses.UUIDClass     -> Json.fromString("00000000-0000-0000-0000-000000000000"),
    StandardTypesClasses.CharsetClass  -> Json.fromString("UTF-8"),
  ).toMapCheckingDuplicatesUnsafe

  private object StandardClass {
    def unapply(clazz: Class[_]): Option[Json] = defaultJsonsForStandardClasses.get(clazz)
  }

  private val fallbackJson = Json.obj()

  def initialJson(typ: TypingResult): Json = {
    initialJsonFor(typ).getOrElse(fallbackJson)
  }

  private def initialJsonFor(typ: TypingResult): Option[Json] = typ match {
    case TypedObjectTypingResult(fields, _, _) =>
      Some(Json.fromFields(fields.mapValuesNow(initialJsonFor(_).getOrElse(fallbackJson))))
    case TypedTaggedValue(underlying, _) => initialJsonFor(underlying)
    case TypedObjectWithValue(_, value)  => ToJsonEncoder.default.encode(value).toOption
    case TypedNull                       => Some(Json.Null)
    case klass: TypedClass               => defaultJsonForClass.lift(klass)
    case union: TypedUnion               => initialJsonFor(union.possibleTypes.head)
    case _: Unknown                      => None
    case _: TypedDict                    => None
  }

  private def defaultJsonForClass: PartialFunction[TypedClass, Json] = {
    case TypedClass(MapClass, _ :: valueType :: Nil) =>
      Json.fromFields(initialJsonFor(valueType).map("field" -> _))
    case TypedClass(ListClass | ArrayClass, elementType :: Nil) =>
      Json.fromValues(initialJsonFor(elementType))
    case TypedClass(clazz, _) if StandardTypesClasses.isDecimalNumber(clazz) =>
      defaultJsonForDecimalNumber
    case TypedClass(clazz, _) if StandardTypesClasses.isFloatingPointNumber(clazz) =>
      defaultJsonForFloatingPointNumber
    case TypedClass(clazz, _) if clazz.isEnum && ReflectUtils.javaEnumNames(clazz).nonEmpty =>
      Json.fromString(ReflectUtils.javaEnumNames(clazz).head)
    case TypedClass(StandardClass(defaultJson), _) => defaultJson
  }

}
