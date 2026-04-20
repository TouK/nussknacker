package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.{ListTypeInfo, MapTypeInfo, MultisetTypeInfo, RowTypeInfo}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.flink.api.TypedMultiset
import pl.touk.nussknacker.engine.flink.api.typeinfo.ListWithNullableValueTypeInfo
import pl.touk.nussknacker.engine.flink.api.typeinformation.{
  CharsetTypeInformation,
  CurrencyTypeInformation,
  DurationTypeInformation,
  FlinkTypeInfoRegistrar,
  LocaleTypeInformation,
  OffsetDateTimeTypeInformation,
  PeriodTypeInformation,
  TypeInformationDetection,
  UUIDTypeInformation,
  ZonedDateTimeTypeInformation,
  ZoneIdTypeInformation
}
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.process.typeinformation.internal.ContextTypeHelpers
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.{
  TypedJavaMapTypeInformation,
  TypedScalaMapTypeInformation
}
import pl.touk.nussknacker.engine.util.Implicits._

import java.nio.charset.Charset
import java.time.{Duration, OffsetDateTime, Period, ZonedDateTime, ZoneId}
import java.util.{Currency, Locale, UUID}
import scala.jdk.CollectionConverters._

// TODO: handle avro types - see FlinkConfluentUtils
/*
  This class generates TypeInformation based on ValidationContext and TypingResult.
  Please note that it is much more sensitive to differences between ValidationContext and real values (e.g. Int vs Long etc...)
  (see TypingResultAwareTypeInformationDetectionSpec."number promotion behaviour" test)

  We should try to produce types supported in TypeInfoDataTypeConverter. Otherwise, we will get problems like:
  Column types of query result and sink for '...' do not match.
  when we use non handled type of variable in table api component.
 */
class TypingResultAwareTypeInformationDetection extends TypeInformationDetection {

  def forContext(validationContext: ValidationContext): TypeInformation[Context] = {
    val variables = forType(
      Typed.record(validationContext.localVariables, Typed.typedClass[Map[String, AnyRef]])
    )
      .asInstanceOf[TypeInformation[Map[String, Any]]]
    val parentCtx = validationContext.parent.map(forContext)

    ContextTypeHelpers.infoFromVariablesAndParentOption(variables, parentCtx)
  }

  override def forType[T](typingResult: TypingResult, withNullableList: Boolean): TypeInformation[T] = {
    (typingResult match {
      case FlinkBelow119AdditionalTypeInfo(typeInfo) => typeInfo
      case TypedClass(klass, elementType :: Nil) if klass == classOf[java.util.List[_]] && withNullableList =>
        new ListWithNullableValueTypeInfo[AnyRef](forType[AnyRef](elementType, withNullableList))
      case TypedClass(klass, elementType :: Nil) if klass == classOf[java.util.List[_]] =>
        new ListTypeInfo[AnyRef](forType[AnyRef](elementType, withNullableList))
      case TypedClass(klass, Nil) if klass == classOf[ZonedDateTime]                => ZonedDateTimeTypeInformation
      case TypedClass(klass, Nil) if klass == classOf[OffsetDateTime]               => OffsetDateTimeTypeInformation
      case TypedClass(klass, Nil) if classOf[ZoneId].isAssignableFrom(klass)        => ZoneIdTypeInformation
      case TypedClass(klass, Nil) if klass == classOf[Duration]                     => DurationTypeInformation
      case TypedClass(klass, Nil) if klass == classOf[Period]                       => PeriodTypeInformation
      case TypedClass(klass, Nil) if klass == classOf[Charset]                      => CharsetTypeInformation
      case TypedClass(klass, Nil) if klass == classOf[Currency]                     => CurrencyTypeInformation
      case TypedClass(klass, Nil) if klass == classOf[Locale]                       => LocaleTypeInformation
      case TypedClass(klass, Nil) if klass == classOf[UUID]                         => UUIDTypeInformation
      case TypedClass(klass, elementType :: Nil) if klass == classOf[Array[AnyRef]] =>
        // We have to use OBJECT_ARRAY even for numeric types, because ARRAY<INT> is represented as Integer[] which can't be handled by IntPrimitiveArraySerializer
        Types.OBJECT_ARRAY(forType[AnyRef](elementType, withNullableList))
      case TypedClass(klass, keyType :: valueType :: Nil) if klass == classOf[java.util.Map[_, _]] =>
        new MapTypeInfo[AnyRef, AnyRef](
          forType[AnyRef](keyType, withNullableList),
          forType[AnyRef](valueType, withNullableList)
        )
      case TypedMultiset(elementType) =>
        new MultisetTypeInfo[AnyRef](forType[AnyRef](elementType, withNullableList))
      case a: TypedObjectTypingResult if a.runtimeObjType.klass == classOf[Row] =>
        val (fieldNames, typeInfos) = a.fields.unzip
        // Warning: RowTypeInfo is fields order sensitive
        new RowTypeInfo(typeInfos.map(forType(_, withNullableList)).toArray[TypeInformation[_]], fieldNames.toArray)
      // TODO: better handle specific map implementations - other than HashMap?
      case a: TypedObjectTypingResult
          if classOf[java.util.Map[String @unchecked, _]].isAssignableFrom(a.runtimeObjType.klass) =>
        TypedJavaMapTypeInformation(a.fields.mapValuesNow(forType(_, withNullableList)))
      // We generally don't use scala Maps in our runtime, but it is useful for some internal type infos: TODO move it somewhere else
      case a: TypedObjectTypingResult if a.runtimeObjType.klass == classOf[Map[String, _]] =>
        TypedScalaMapTypeInformation(a.fields.mapValuesNow(forType(_, withNullableList)))
      // TODO: scala case classes are not handled nicely here... CaseClassTypeInfo is created only via macro, here Kryo is used
      case a: SingleTypingResult if a.runtimeObjType.params.isEmpty =>
        TypeInformation.of(a.runtimeObjType.klass)
      // TODO: how can we handle union - at least of some types?
      case TypedObjectWithValue(tc: TypedClass, _) =>
        forType(tc, withNullableList)
      case _ =>
        TypeInformation.of(classOf[Any])
    }).asInstanceOf[TypeInformation[T]]
  }

  // This extractor is to allow using of predefined type infos in Flink < 1.19. Type info registration was added in 1.19
  // It should be removed when we stop supporting Flink < 1.19
  private object FlinkBelow119AdditionalTypeInfo extends Serializable {

    def unapply(typingResult: TypingResult): Option[TypeInformation[_]] = {
      if (FlinkTypeInfoRegistrar.isFlinkTypeInfoRegistrationEnabled) {
        None
      } else {
        for {
          clazz <- Option(typingResult).collect { case TypedClass(clazz, Nil) =>
            clazz
          }
          typeInfo <- FlinkTypeInfoRegistrar.typeInfoToRegister.collectFirst {
            case FlinkTypeInfoRegistrar.RegistrationEntry(`clazz`, factoryClass) =>
              val factory = factoryClass.getDeclaredConstructor().newInstance()
              factory.createTypeInfo(clazz, Map.empty[String, TypeInformation[_]].asJava)
          }
        } yield typeInfo
      }
    }

  }

  def forValueWithContext[T](
      validationContext: ValidationContext,
      value: TypeInformation[T]
  ): TypeInformation[ValueWithContext[T]] = {
    val finalContext = forContext(validationContext)
    ConcreteCaseClassTypeInfo[ValueWithContext[T]](
      ("value", value),
      ("context", finalContext)
    )
  }

  override def priority: Int = Integer.MIN_VALUE
}
