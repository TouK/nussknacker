package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.{ListTypeInfo, MapTypeInfo, MultisetTypeInfo, RowTypeInfo}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.TypedMultiset
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.process.typeinformation.internal.ContextTypeHelpers
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.{
  TypedJavaMapTypeInformation,
  TypedScalaMapTypeInformation
}
import pl.touk.nussknacker.engine.util.Implicits._

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
      case TypedClass(klass, elementType :: Nil) if klass == classOf[java.util.List[_]] =>
        new ListTypeInfo[AnyRef](forType[AnyRef](elementType))
      case TypedClass(klass, elementType :: Nil) if klass == classOf[Array[AnyRef]] =>
        // We have to use OBJECT_ARRAY even for numeric types, because ARRAY<INT> is represented as Integer[] which can't be handled by IntPrimitiveArraySerializer
        Types.OBJECT_ARRAY(forType[AnyRef](elementType))
      case TypedClass(klass, keyType :: valueType :: Nil) if klass == classOf[java.util.Map[_, _]] =>
        new MapTypeInfo[AnyRef, AnyRef](forType[AnyRef](keyType), forType[AnyRef](valueType))
      case TypedMultiset(elementType) =>
        new MultisetTypeInfo[AnyRef](forType[AnyRef](elementType))
      case a: TypedObjectTypingResult if a.runtimeObjType.klass == classOf[Row] =>
        val (fieldNames, typeInfos) = a.fields.unzip
        // Warning: RowTypeInfo is fields order sensitive
        new RowTypeInfo(typeInfos.map(forType).toArray[TypeInformation[_]], fieldNames.toArray)
      // TODO: better handle specific map implementations - other than HashMap?
      case a: TypedObjectTypingResult
          if classOf[java.util.Map[String @unchecked, _]].isAssignableFrom(a.runtimeObjType.klass) =>
        TypedJavaMapTypeInformation(a.fields.mapValuesNow(forType))
      // We generally don't use scala Maps in our runtime, but it is useful for some internal type infos: TODO move it somewhere else
      case a: TypedObjectTypingResult if a.runtimeObjType.klass == classOf[Map[String, _]] =>
        TypedScalaMapTypeInformation(a.fields.mapValuesNow(forType))
      case a: SingleTypingResult if registeredTypeInfos.contains(a.runtimeObjType) =>
        registeredTypeInfos(a.runtimeObjType)
      // TODO: scala case classes are not handled nicely here... CaseClassTypeInfo is created only via macro, here Kryo is used
      case a: SingleTypingResult if a.runtimeObjType.params.isEmpty =>
        TypeInformation.of(a.runtimeObjType.klass)
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

}
