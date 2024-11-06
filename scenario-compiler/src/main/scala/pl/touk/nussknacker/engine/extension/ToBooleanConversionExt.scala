package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet

import java.lang.{Boolean => JBoolean}

class ToBooleanConversionExt extends ExtensionMethodHandler {

  override val methodRegistry: Map[String, ExtensionMethod] = Map(
    "isBoolean"       -> ((target: Any, _) => ToBooleanConversionExt.canConvert(target)),
    "toBoolean"       -> ((target: Any, _) => ToBooleanConversionExt.convert(target)),
    "toBooleanOrNull" -> ((target: Any, _) => ToBooleanConversionExt.convertOrNull(target)),
  )

}

object ToBooleanConversionExt extends ConversionExt {
  private val cannotConvertException = (value: Any) =>
    new IllegalArgumentException(s"Cannot convert: $value to Boolean")
  private val allowedClassesForConversion: Set[Class[_]] = Set(classOf[String], classOf[Object])

  override type ResultType = JBoolean
  override val resultTypeClass: Class[JBoolean] = classOf[JBoolean]

  override def createHandler(set: ClassDefinitionSet): ExtensionMethodHandler = new ToBooleanConversionExt

  override def appliesToConversion(clazz: Class[_]): Boolean = allowedClassesForConversion.contains(clazz)

  override def convertEither(value: Any): Either[Throwable, JBoolean] = value match {
    case b: JBoolean => Right(b)
    case s: String   => stringToBoolean(s).toRight(cannotConvertException(value))
    case _           => Left(cannotConvertException(value))
  }

  private def stringToBoolean(value: String): Option[JBoolean] =
    if ("true".equalsIgnoreCase(value)) {
      Some(true)
    } else if ("false".equalsIgnoreCase(value)) {
      Some(false)
    } else {
      None
    }

}
