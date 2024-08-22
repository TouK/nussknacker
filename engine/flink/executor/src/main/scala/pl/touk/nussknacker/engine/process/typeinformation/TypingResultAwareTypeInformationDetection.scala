package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.{ListTypeInfo, MapTypeInfo, RowTypeInfo}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.typeinformation.{
  TypeInformationDetection,
  TypingResultAwareTypeInformationCustomisation
}
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.process.typeinformation.internal.ContextTypeHelpers
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.{
  TypedJavaMapTypeInformation,
  TypedMapTypeInformation,
  TypedScalaMapTypeInformation
}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

object TypingResultAwareTypeInformationDetection {

  def apply(classLoader: ClassLoader): TypingResultAwareTypeInformationDetection = {
    val customisations = ScalaServiceLoader.load[TypingResultAwareTypeInformationCustomisation](classLoader)
    new TypingResultAwareTypeInformationDetection(customisations)
  }

}

// TODO: handle avro types - see FlinkConfluentUtils
/*
  This class generates TypeInformation based on ValidationContext and TypingResult.
  Please note that it is much more sensitive to differences between ValidationContext and real values (e.g. Int vs Long etc...)
  (see TypingResultAwareTypeInformationDetectionSpec."number promotion behaviour" test)

  We should try to produce types supported in TypeInfoDataTypeConverter. Otherwise, we will get problems like:
  Column types of query result and sink for '...' do not match.
  when we use non handled type of variable in table api component.
 */
class TypingResultAwareTypeInformationDetection(customisations: List[TypingResultAwareTypeInformationCustomisation])
    extends TypeInformationDetection {

  private val registeredTypeInfos: Map[TypedClass, TypeInformation[_]] = Map(
    Typed.typedClass[String]                  -> Types.STRING,
    Typed.typedClass[Boolean]                 -> Types.BOOLEAN,
    Typed.typedClass[Byte]                    -> Types.BYTE,
    Typed.typedClass[Short]                   -> Types.SHORT,
    Typed.typedClass[Integer]                 -> Types.INT,
    Typed.typedClass[Long]                    -> Types.LONG,
    Typed.typedClass[Float]                   -> Types.FLOAT,
    Typed.typedClass[Double]                  -> Types.DOUBLE,
    Typed.typedClass[Character]               -> Types.CHAR,
    Typed.typedClass[java.math.BigDecimal]    -> Types.BIG_DEC,
    Typed.typedClass[java.math.BigInteger]    -> Types.BIG_INT,
    Typed.typedClass[java.time.LocalDate]     -> Types.LOCAL_DATE,
    Typed.typedClass[java.time.LocalTime]     -> Types.LOCAL_TIME,
    Typed.typedClass[java.time.LocalDateTime] -> Types.LOCAL_DATE_TIME,
    Typed.typedClass[java.time.Instant]       -> Types.INSTANT,
    Typed.typedClass[java.sql.Date]           -> Types.SQL_DATE,
    Typed.typedClass[java.sql.Time]           -> Types.SQL_TIME,
    Typed.typedClass[java.sql.Timestamp]      -> Types.SQL_TIMESTAMP,
  )

  // See Types.PRIMITIVE_ARRAY
  private val primitiveArraySupportedTypes = Set[TypingResult](
    Typed.typedClass[Boolean],
    Typed.typedClass[Byte],
    Typed.typedClass[Short],
    Typed.typedClass[Integer],
    Typed.typedClass[Long],
    Typed.typedClass[Float],
    Typed.typedClass[Double],
    Typed.typedClass[Character],
  )

  def forContext(validationContext: ValidationContext): TypeInformation[Context] = {
    val variables = forType(
      Typed.record(validationContext.localVariables, Typed.typedClass[Map[String, AnyRef]])
    )
      .asInstanceOf[TypeInformation[Map[String, Any]]]
    val parentCtx = validationContext.parent.map(forContext)

    ContextTypeHelpers.infoFromVariablesAndParentOption(variables, parentCtx)
  }

  def forType[T](typingResult: TypingResult): TypeInformation[T] = {
    (typingResult match {
      case a if additionalTypeInfoDeterminer.isDefinedAt(a) =>
        additionalTypeInfoDeterminer.apply(a)
      case a: TypedClass if a.klass == classOf[java.util.List[_]] && a.params.size == 1 =>
        new ListTypeInfo[AnyRef](forType[AnyRef](a.params.head))
      case a: TypedClass
          if a.klass == classOf[Array[AnyRef]] && a.params.size == 1 && primitiveArraySupportedTypes.contains(
            a.params.head
          ) =>
        Types.PRIMITIVE_ARRAY(forType[AnyRef](a.params.head))
      case a: TypedClass if a.klass == classOf[Array[AnyRef]] && a.params.size == 1 =>
        Types.OBJECT_ARRAY(forType[AnyRef](a.params.head))
      case a: TypedClass if a.klass == classOf[java.util.Map[_, _]] && a.params.size == 2 =>
        new MapTypeInfo[AnyRef, AnyRef](forType[AnyRef](a.params.head), forType[AnyRef](a.params.last))
      case a: TypedObjectTypingResult if a.objType.klass == classOf[Map[String, _]] =>
        TypedScalaMapTypeInformation(a.fields.mapValuesNow(forType))
      case a: TypedObjectTypingResult if a.objType.klass == classOf[TypedMap] =>
        TypedMapTypeInformation(a.fields.mapValuesNow(forType))
      case a: TypedObjectTypingResult if a.objType.klass == classOf[Row] =>
        val (fieldNames, typeInfos) = a.fields.unzip
        // Warning: RowTypeInfo is fields order sensitive
        new RowTypeInfo(typeInfos.map(forType).toArray[TypeInformation[_]], fieldNames.toArray)
      // TODO: better handle specific map implementations - other than HashMap?
      case a: TypedObjectTypingResult if classOf[java.util.Map[String, _]].isAssignableFrom(a.objType.klass) =>
        TypedJavaMapTypeInformation(a.fields.mapValuesNow(forType))
      case a: SingleTypingResult if registeredTypeInfos.contains(a.objType) =>
        registeredTypeInfos(a.objType)
      // TODO: scala case classes are not handled nicely here... CaseClassTypeInfo is created only via macro, here Kryo is used
      case a: SingleTypingResult if a.objType.params.isEmpty =>
        TypeInformation.of(a.objType.klass)
      // TODO: how can we handle union - at least of some types?
      case TypedObjectWithValue(tc: TypedClass, _) =>
        forType(tc)
      case _ =>
        TypeInformation.of(classOf[Any])
    }).asInstanceOf[TypeInformation[T]]
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

  private lazy val additionalTypeInfoDeterminer: PartialFunction[TypingResult, TypeInformation[_]] =
    customisations.map(_.customise(this)).reduceOption(_.orElse(_)).getOrElse(Map.empty)

}
