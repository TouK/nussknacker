package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.CastOrConversionExt.{canBeMethodName, orNullSuffix, toMethodName}
import pl.touk.nussknacker.engine.spel.internal.ConversionHandler
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassExtensions

import java.lang.{Boolean => JBoolean}
import java.util.{ArrayList => JArrayList, Collection => JCollection, HashMap => JHashMap, List => JList, Map => JMap}

object ToListConversionExt extends ConversionExt(ToListConversion) {

  private val booleanTyping = Typed.typedClass[Boolean]
  private val listTyping    = Typed.genericTypeClass[JList[_]](List(Unknown))

  private val isListMethodDefinition = FunctionalMethodDefinition(
    typeFunction = (targetTyping, _) => ToListConversion.typingFunction(targetTyping).map(_ => booleanTyping),
    signature = MethodTypeInfo.noArgTypeInfo(booleanTyping),
    name = s"${canBeMethodName}List",
    description = Some("Check whether can be convert to a list")
  )

  private val toListDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToListConversion.typingFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(listTyping),
    name = s"${toMethodName}List",
    description = Option("Convert to a list or throw exception in case of failure")
  )

  private val toListOrNullDefinition = FunctionalMethodDefinition(
    typeFunction = (invocationTarget, _) => ToListConversion.typingFunction(invocationTarget),
    signature = MethodTypeInfo.noArgTypeInfo(listTyping),
    name = s"${toMethodName}List${orNullSuffix}",
    description = Option("Convert to a list or null in case of failure")
  )

  override protected def definitions(): List[MethodDefinition] = List(
    isListMethodDefinition,
    toListDefinition,
    toListOrNullDefinition,
  )

}

object ToListConversion extends Conversion[JList[_]] {

  private val collectionClass = classOf[JCollection[_]]
  private val mapClass        = classOf[JMap[_, _]]

  override def convertEither(value: Any): Either[Throwable, JList[_]] = {
    value match {
      case l: JList[_]       => Right(l)
      case c: JCollection[_] => Right(new JArrayList[Any](c))
      case a: Array[_]       => Right(ConversionHandler.convertArrayToList(a))
      case m: JMap[_, _] =>
        val l = new JArrayList[JMap[_, _]]()
        m.entrySet().forEach { entry =>
          val entryRecord = new JHashMap[String, Any]()
          entryRecord.put(ToMapConversion.keyName, entry.getKey)
          entryRecord.put(ToMapConversion.valueName, entry.getValue)
          l.add(entryRecord)
        }
        Right(l)
      case x => Left(new IllegalArgumentException(s"Cannot convert: $x to a List"))
    }
  }

  override val typingResult: TypingResult = Typed.genericTypeClass(resultTypeClass, List(Unknown))

  override val typingFunction: TypingResult => ValidatedNel[GenericFunctionTypingError, TypingResult] =
    invocationTarget =>
      invocationTarget.withoutValue match {
        case TypedClass(klass, params) if klass.isAOrChildOf(collectionClass) || klass.isArray =>
          Typed.genericTypeClass[JList[_]](params).validNel
        case TypedObjectTypingResult(_, TypedClass(klass, _ :: valuesSuperType :: Nil), _)
            if klass.isAOrChildOf(mapClass) =>
          Typed
            .genericTypeClass[JList[_]](
              List(
                Typed.record(
                  List(
                    ToMapConversion.keyName   -> Typed[String],
                    ToMapConversion.valueName -> valuesSuperType
                  )
                )
              )
            )
            .validNel
        case TypedClass(klass, keysSuperType :: valuesSuperType :: Nil) if klass.isAOrChildOf(mapClass) =>
          Typed
            .genericTypeClass[JList[_]](
              List(
                Typed.record(
                  List(
                    ToMapConversion.keyName   -> keysSuperType,
                    ToMapConversion.valueName -> valuesSuperType
                  )
                )
              )
            )
            .validNel
        case Unknown => Typed.genericTypeClass[JList[_]](List(Unknown)).validNel
        case _       => GenericFunctionTypingError.ArgumentTypeError.invalidNel
      }

  // We could leave underlying method using convertEither as well but this implementation is faster
  override def canConvert(value: Any): JBoolean =
    value.getClass.isAOrChildOf(collectionClass) || value.getClass.isAOrChildOf(mapClass) || value.getClass.isArray

  override def appliesToConversion(clazz: Class[_]): Boolean =
    clazz != resultTypeClass && (clazz.isAOrChildOf(collectionClass) || clazz.isAOrChildOf(
      mapClass
    ) || clazz == unknownClass || clazz.isArray)

}
