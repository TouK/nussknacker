package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ConversionExt.getConversion
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

// todo: lbg - add casting methods to UTIL
class ConversionExt(target: Any) {

  def is(className: String): Boolean = getConversion(className) match {
    case Right(conversion) => conversion.canConvert(target)
    case Left(ex)          => throw ex
  }

  def to(className: String): Any = getConversion(className) match {
    case Right(conversion) => conversion.convert(target)
    case Left(ex)          => throw ex
  }

  def toOrNull(className: String): Any = getConversion(className) match {
    case Right(conversion) => conversion.convertOrNull(target)
    case Left(ex)          => throw ex
  }

}

object ConversionExt extends ExtensionMethodsHandler {
  private val stringClass  = classOf[String]
  private val unknownClass = classOf[Object]
  private val numberClass  = classOf[Number]

  private val methodTypeInfoWithStringParam = MethodTypeInfo(
    noVarArgs = List(
      Parameter("className", Typed.genericTypeClass(stringClass, Nil))
    ),
    varArg = None,
    result = Unknown
  )

  private val definitions: Map[String, List[MethodDefinition]] =
    List(
      FunctionalMethodDefinition(
        (_, x) => canConvertToTyping(x),
        methodTypeInfoWithStringParam,
        "is",
        Some("Checks if a type can be converted to a given class")
      ),
      FunctionalMethodDefinition(
        (_, x) => convertToTyping(x),
        methodTypeInfoWithStringParam,
        "to",
        Some("Converts a type to a given class or throws exception if type cannot be converted.")
      ),
      FunctionalMethodDefinition(
        (_, x) => convertToTyping(x),
        methodTypeInfoWithStringParam,
        "toOrNull",
        Some("Converts a type to a given class or return null if type cannot be converted.")
      ),
    ).groupBy(_.name)

  private val convertMethodsNames = definitions.keySet

  private val conversionsRegistry: List[Conversion] = List(
    ToLongConversion,
    ToDoubleConversion,
    ToBigDecimalConversion,
    ToBooleanConversion,
    ToStringConversion,
    ToMapConversion,
    ToListConversion,
  )

  private val conversionsByType: Map[String, Conversion] = conversionsRegistry
    .flatMap(c => c.supportedResultTypes.map(t => t.toLowerCase -> c))
    .toMap

  val supportedTypes: List[(String, TypingResult)] =
    conversionsRegistry.map(c => c.simpleSupportedResultTypeName -> c.typingResult)

  override type ExtensionMethodInvocationTarget = ConversionExt
  override val invocationTargetClass: Class[ConversionExt] = classOf[ConversionExt]

  def isConversionMethod(methodName: String): Boolean =
    convertMethodsNames.contains(methodName)

  def getConversion(className: String): Either[Throwable, Conversion] =
    conversionsByType.get(className.toLowerCase) match {
      case Some(conversion) => Right(conversion)
      case None             => Left(new IllegalArgumentException(s"Conversion for class $className not found"))
    }

  override def createConverter(): ToExtensionMethodInvocationTargetConverter[ConversionExt] =
    (target: Any) => new ConversionExt(target)

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] =
    if (clazz.isAOrChildOf(numberClass) || clazz == stringClass || clazz == unknownClass) definitions
    else Map.empty

  // Convert methods should visible in runtime for every class because we allow invoke convert methods on an unknown
  // object in Typer, but in the runtime the same type could be known and that's why should add convert method for an
  // every class.
  override def applies(clazz: Class[_]): Boolean = true

  private def convertToTyping(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectWithValue(_, clazzName: String) :: Nil =>
      getConversion(clazzName) match {
        case Right(conversion) => conversion.typingResult.validNel
        case Left(ex)          => GenericFunctionTypingError.OtherError(ex.getMessage).invalidNel
      }
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  private def canConvertToTyping(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    convertToTyping(arguments).map(_ => Typed.typedClass[Boolean])

}
