package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.{ListTypeInfo, MapTypeInfo, MultisetTypeInfo, RowTypeInfo}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.flink.api.TypedMultiset
import pl.touk.nussknacker.engine.flink.api.typeinformation.{FlinkTypeInfoRegistrar, TypeInformationDetection}
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.process.typeinformation.internal.ContextTypeHelpers
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.{
  TypedJavaMapTypeInformation,
  TypedScalaMapTypeInformation
}
import pl.touk.nussknacker.engine.util.Implicits._

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

  def forType[T](typingResult: TypingResult): TypeInformation[T] = {
    (typingResult match {
      case FlinkBelow119AdditionalTypeInfo(typeInfo) => typeInfo
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
        createJavaMapTypeInformation(a)
      // We generally don't use scala Maps in our runtime, but it is useful for some internal type infos: TODO move it somewhere else
      case a: TypedObjectTypingResult if a.runtimeObjType.klass == classOf[Map[String, _]] =>
        createScalaMapTypeInformation(a)
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

  private def createScalaMapTypeInformation(typingResult: TypedObjectTypingResult) =
    TypedScalaMapTypeInformation(typingResult.fields.mapValuesNow(forType))

  private def createJavaMapTypeInformation(typingResult: TypedObjectTypingResult) =
    TypedJavaMapTypeInformation(typingResult.fields.mapValuesNow(forType))

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
