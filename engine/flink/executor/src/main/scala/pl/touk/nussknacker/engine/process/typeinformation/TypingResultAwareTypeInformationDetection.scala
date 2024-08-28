package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.{ListTypeInfo, MapTypeInfo, MultisetTypeInfo, RowTypeInfo}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.TypedMultiset
import pl.touk.nussknacker.engine.flink.api.typeinformation.{TypeInformationDetection, TypeInformationWithDetails}
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.process.typeinformation.internal.ContextTypeHelpers
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.{
  TypedJavaMapTypeInformation,
  TypedScalaMapTypeInformation
}
import pl.touk.nussknacker.engine.util.Implicits._
import TypeInformationWithDetails._

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

  private val listClass = classOf[java.util.List[_]]

  private val arrayClass = classOf[Array[AnyRef]]

  private val javaMapClass = classOf[java.util.Map[_, _]]

  private val rowClass = classOf[Row]

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

  def forTypeWithDetails[T](typingResult: TypingResult): TypeInformationWithDetails[T] = {
    (typingResult match {
      case TypedClass(`listClass`, elementType :: Nil) =>
        forTypeWithDetails[AnyRef](elementType).map(Types.LIST[AnyRef])
      case TypedClass(`arrayClass`, elementType :: Nil) =>
        // We have to use OBJECT_ARRAY even for numeric types, because ARRAY<INT> is represented as Integer[] which can't be handled by IntPrimitiveArraySerializer
        forTypeWithDetails[AnyRef](elementType).map(Types.OBJECT_ARRAY[AnyRef])
      case TypedClass(`javaMapClass`, keyType :: valueType :: Nil) =>
        TypeInformationWithDetails.combine(forTypeWithDetails[AnyRef](keyType), forTypeWithDetails[AnyRef](valueType))(
          Types.MAP[AnyRef, AnyRef]
        )
      case TypedMultiset(elementType) =>
        forTypeWithDetails[AnyRef](elementType).map(new MultisetTypeInfo[AnyRef](_))
      case a: TypedObjectTypingResult if a.objType.klass == rowClass =>
        val (fieldNames, typeInfos) = a.fields.unzip
        // Warning: RowTypeInfo is fields order sensitive
        val typeInfoWithDetailsForFields =
          typeInfos.map(forTypeWithDetails[Any](_): TypeInformationWithDetails[_]).toSeq
        val tableApiCompatible = typeInfoWithDetailsForFields.forall(_.isTableApiCompatible)
        TypeInformationWithDetails(
          Types.ROW_NAMED(fieldNames.toArray, typeInfoWithDetailsForFields.map(_.typeInformation): _*),
          tableApiCompatible
        )
      // TODO: better handle specific map implementations - other than HashMap?
      case a: TypedObjectTypingResult if javaMapClass.isAssignableFrom(a.objType.klass) =>
        TypedJavaMapTypeInformation(a.fields.mapValuesNow(forType)).toTableApiIncompatibleTypeInformation
      // We generally don't use scala Maps in our runtime, but it is useful for some internal type infos: TODO move it somewhere else
      case a: TypedObjectTypingResult if a.objType.klass == classOf[Map[String, _]] =>
        TypedScalaMapTypeInformation(a.fields.mapValuesNow(forType)).toTableApiIncompatibleTypeInformation
      case a: SingleTypingResult if registeredTypeInfos.contains(a.objType) =>
        registeredTypeInfos(a.objType).toTableApiCompatibleTypeInformation
      // TODO: scala case classes are not handled nicely here... CaseClassTypeInfo is created only via macro, here Kryo is used
      case a: SingleTypingResult if a.objType.params.isEmpty =>
        TypeInformation.of(a.objType.klass).toTableApiIncompatibleTypeInformation
      // TODO: how can we handle union - at least of some types?
      case TypedObjectWithValue(tc: TypedClass, _) =>
        forTypeWithDetails[T](tc)
      case _ =>
        TypeInformation.of(classOf[Any]).toTableApiIncompatibleTypeInformation
    }).asInstanceOf[TypeInformationWithDetails[T]]
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
