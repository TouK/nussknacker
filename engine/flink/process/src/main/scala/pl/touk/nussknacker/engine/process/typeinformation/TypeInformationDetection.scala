package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.{ListTypeInfo, MapTypeInfo}
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, OptionTypeInfo, ScalaCaseClassSerializer}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.lazyy.LazyContext
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, PartReference}
import pl.touk.nussknacker.engine.flink.api.{AdditionalTypeInformationProvider, NkGlobalParameters}
import pl.touk.nussknacker.engine.process.typeinformation.internal.{FixedValueSerializers, InterpretationResultMapTypeInfo, TypedMapTypeInformation}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.reflect.ClassTag

object TypeInformationDetection {

  def forExecutionConfig(executionConfig: ExecutionConfig, classLoader: ClassLoader): TypeInformationDetection = {
    val useTypeInformationBasedOnTypingResult =
      NkGlobalParameters.readFromContext(executionConfig).flatMap(_.configParameters).flatMap(_.useTypeInformationBasedOnTypingResult).getOrElse(false)
    if (useTypeInformationBasedOnTypingResult) {
      TypingResultAwareTypeInformationDetection.withLoadedAdditionalTypeInformations(classLoader)
    } else LegacyTypeInformationDetection

  }

}

trait TypeInformationDetection extends Serializable {
  def forContext(validationContext: ValidationContext): TypeInformation[Context]

  def forType(typingResult: TypingResult): TypeInformation[Any]

  def forInterpretationResult(validationContext: ValidationContext, outputRes: Option[TypingResult]): TypeInformation[InterpretationResult]

  def forInterpretationResults(possibleContexts: Map[String, ValidationContext]): TypeInformation[InterpretationResult]
}

object LegacyTypeInformationDetection extends TypeInformationDetection {

  override def forContext(validationContext: ValidationContext): TypeInformation[Context] = TypeInformation.of(classOf[Context])

  override def forType(typingResult: TypingResult): TypeInformation[Any] = TypeInformation.of(classOf[Any])

  override def forInterpretationResult(validationContext: ValidationContext, outputRes: Option[TypingResult]): TypeInformation[InterpretationResult]
  = TypeInformation.of(classOf[InterpretationResult])

  override def forInterpretationResults(possibleContexts: Map[String, ValidationContext]): TypeInformation[InterpretationResult]
  = TypeInformation.of(classOf[InterpretationResult])

}

object TypingResultAwareTypeInformationDetection {

  def withLoadedAdditionalTypeInformations(classLoader: ClassLoader): TypingResultAwareTypeInformationDetection = {
    val additionalTypeInfos: Set[TypeInformation[_]] = ScalaServiceLoader.load[AdditionalTypeInformationProvider](classLoader)
      .toSet
      .flatMap((k: AdditionalTypeInformationProvider) => k.additionalTypeInformation)
    new TypingResultAwareTypeInformationDetection(additionalTypeInfos)
  }

}

class TypingResultAwareTypeInformationDetection(additionalTypingInfo: Set[TypeInformation[_]]) extends TypeInformationDetection {

  private val registeredTypeInfos: Set[TypeInformation[_]] = {
    import org.apache.flink.api.scala._
    Set(
      implicitly[TypeInformation[BigDecimal]]
    )
  }

  def forContext(validationContext: ValidationContext): TypeInformation[Context] = {
    val id = TypeInformation.of(classOf[String])
    val variables = forType(TypedObjectTypingResult(validationContext.localVariables, Typed.typedClass[Map[String, AnyRef]]))
    val lazyContext = forLazyContext
    val parentCtx = new OptionTypeInfo[Context, Option[Context]](validationContext.parent.map(forContext).getOrElse(FixedValueSerializers.nullValueTypeInfo))

    val typeInfos = List(id, variables, lazyContext, parentCtx)
    new CaseClassTypeInfo[Context](classOf[Context],
      Array.empty, typeInfos, List("id", "variables", "lazyContext", "parentContext")) {
      override def createSerializer(config: ExecutionConfig): TypeSerializer[Context] = {
        new ScalaCaseClassSerializer[Context](classOf[Context], typeInfos.map(_.createSerializer(config)).toArray)
      }
    }
  }

  def forType(typingResult: TypingResult): TypeInformation[Any] = {
    (typingResult match {
      case a:TypedTaggedValue => forType(a.underlying)
      case a:TypedDict => forType(a.objType)
      case a:TypedClass if a.params.isEmpty =>
        (registeredTypeInfos ++ additionalTypingInfo).find(_.getTypeClass == a.klass).getOrElse(TypeInformation.of(a.klass))

      case a:TypedClass if a.klass == classOf[java.util.List[_]] => new ListTypeInfo[Any](forType(a.params.head))
      case a:TypedClass if a.klass == classOf[java.util.Map[_, _]] => new MapTypeInfo[Any, Any](forType(a.params.head), forType(a.params.last))

      case a:TypedObjectTypingResult if a.objType.klass == classOf[Map[String, _]] =>
        TypedMapTypeInformation(a.fields.mapValuesNow(forType))
      case _ =>
        fallback[Any]
    }).asInstanceOf[TypeInformation[Any]]
  }

  def forInterpretationResult(validationContext: ValidationContext, outputRes: Option[TypingResult]): TypeInformation[InterpretationResult] = {
    //TODO: here we still use Kryo :/
    val reference = TypeInformation.of(classOf[PartReference])
    val output = outputRes.map(forType).getOrElse(FixedValueSerializers.nullValueTypeInfo)
    val finalContext = forContext(validationContext)

    val typeInfos = List(reference, output, finalContext)
    new CaseClassTypeInfo[InterpretationResult](classOf[InterpretationResult],
      Array.empty, typeInfos, List("reference", "output", "finalContext")) {
      override def createSerializer(config: ExecutionConfig): TypeSerializer[InterpretationResult] = {
        new ScalaCaseClassSerializer[InterpretationResult](classOf[InterpretationResult], typeInfos.map(_.createSerializer(config)).toArray)
      }
    }
  }

  def forInterpretationResults(possibleContexts: Map[String, ValidationContext]): TypeInformation[InterpretationResult] = {
    InterpretationResultMapTypeInfo(possibleContexts.mapValuesNow(forInterpretationResult(_, None)))
  }

  private def fallback[T:ClassTag]: TypeInformation[T] = fallback(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])

  private def fallback[T](kl: Class[T]): TypeInformation[T] = TypeInformation.of(kl)

  private val forLazyContext: TypeInformation[LazyContext] = {
    val id = TypeInformation.of(classOf[String])
    val evaluatedValues = FixedValueSerializers.emptyMapTypeInfo
    val typeInfos = List(id, evaluatedValues)
    new CaseClassTypeInfo[LazyContext](classOf[LazyContext],
      Array.empty, typeInfos, List("id", "evaluatedValues")) {
      override def createSerializer(config: ExecutionConfig): TypeSerializer[LazyContext] = {
        new ScalaCaseClassSerializer[LazyContext](classOf[LazyContext], typeInfos.map(_.createSerializer(config)).toArray)
      }
    }
  }



}

