package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.{ListTypeInfo, MapTypeInfo}
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, OptionTypeInfo, ScalaCaseClassSerializer}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, PartReference, ValueWithContext}
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.{TypedJavaMapTypeInformation, TypedMapTypeInformation, TypedScalaMapTypeInformation}
import pl.touk.nussknacker.engine.process.typeinformation.internal.{FixedValueSerializers, InterpretationResultMapTypeInfo}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.reflect.ClassTag

object TypingResultAwareTypeInformationDetection {

  def apply(classLoader: ClassLoader): TypingResultAwareTypeInformationDetection = {
    val customisations = ScalaServiceLoader.load[TypingResultAwareTypeInformationCustomisation](classLoader)
    new TypingResultAwareTypeInformationDetection(new CompositeCustomisation(customisations))
  }

  class CompositeCustomisation(customisations: List[TypingResultAwareTypeInformationCustomisation]) extends TypingResultAwareTypeInformationCustomisation {
    override def customise(originalDetection: TypingResultAwareTypeInformationDetection): PartialFunction[TypingResult, TypeInformation[_]] =
      customisations.map(_.customise(originalDetection)).reduceOption(_.orElse(_)).getOrElse(Map.empty)
  }

}

/*
  This is *experimental* TypeInformationDetection, which generates TypeInformation based on ValidationContext and TypingResult.
  Please note that it is much more sensitive to differences between ValidationContext and real values (e.g. Int vs Long etc...)
  (see TypingResultAwareTypeInformationDetectionSpec."number promotion behaviour" test)

  To use it for serialization between operators use TypeInformationDetection service loading.
  To use it for state serialization one can use it directly in operators/process functions (compatibility is *NOT* guaranteed ATM).
 */
class TypingResultAwareTypeInformationDetection(customisation:
                                                TypingResultAwareTypeInformationCustomisation) extends TypeInformationDetection {

  private val additionalTypeInfoDeterminer = customisation.customise(this)

  private val registeredTypeInfos: Set[TypeInformation[_]] = {
    import org.apache.flink.api.scala._
    Set(
      implicitly[TypeInformation[BigDecimal]]
    )
  }

  def forContext(validationContext: ValidationContext): TypeInformation[Context] = {
    val id = TypeInformation.of(classOf[String])
    val variables = forType(TypedObjectTypingResult(validationContext.localVariables, Typed.typedClass[Map[String, AnyRef]]))
    val parentCtx = new OptionTypeInfo[Context, Option[Context]](validationContext.parent.map(forContext).getOrElse(FixedValueSerializers.nullValueTypeInfo))

    val typeInfos = List(id, variables, parentCtx)
    new CaseClassTypeInfo[Context](classOf[Context],
      Array.empty, typeInfos, List("id", "variables", "parentContext")) {
      override def createSerializer(config: ExecutionConfig): TypeSerializer[Context] = {
        new ScalaCaseClassSerializer[Context](classOf[Context], typeInfos.map(_.createSerializer(config)).toArray)
      }
    }
  }

  def forType(typingResult: TypingResult): TypeInformation[Any] = {
    (typingResult match {
      case a if additionalTypeInfoDeterminer.isDefinedAt(a) =>
        additionalTypeInfoDeterminer.apply(a)
      case a:TypedTaggedValue => forType(a.underlying)
      case a:TypedDict => forType(a.objType)
      case a:TypedClass if a.params.isEmpty =>
        //TODO: scala case classes are not handled nicely here... CaseClassTypeInfo is created only via macro, here Kryo is used
        registeredTypeInfos.find(_.getTypeClass == a.klass).getOrElse(TypeInformation.of(a.klass))

      case a:TypedClass if a.klass == classOf[java.util.List[_]] => new ListTypeInfo[Any](forType(a.params.head))
      case a:TypedClass if a.klass == classOf[java.util.Map[_, _]] => new MapTypeInfo[Any, Any](forType(a.params.head), forType(a.params.last))

      case a:TypedObjectTypingResult if a.objType.klass == classOf[Map[String, _]] =>
        TypedScalaMapTypeInformation(a.fields.mapValuesNow(forType))
      case a:TypedObjectTypingResult if a.objType.klass == classOf[TypedMap] =>
        TypedMapTypeInformation(a.fields.mapValuesNow(forType))
      //TODO: better handle specific map implementations - other than HashMap?
      case a:TypedObjectTypingResult if classOf[java.util.Map[String, _]].isAssignableFrom(a.objType.klass) =>
        TypedJavaMapTypeInformation(a.fields.mapValuesNow(forType))
      //TODO: how can we handle union - at least of some types?
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

  def forValueWithContext[T](validationContext: ValidationContext, value: TypingResult): TypeInformation[ValueWithContext[T]] = {
    val valueType = forType(value)
    val finalContext = forContext(validationContext)

    val typeInfos = List(valueType, finalContext)
    new CaseClassTypeInfo[ValueWithContext[T]](classOf[ValueWithContext[T]],
      Array.empty, typeInfos, List("value", "context")) {
      override def createSerializer(config: ExecutionConfig): TypeSerializer[ValueWithContext[T]] = {
        new ScalaCaseClassSerializer[ValueWithContext[T]](classOf[ValueWithContext[T]], typeInfos.map(_.createSerializer(config)).toArray)
      }
    }
  }

  private def fallback[T:ClassTag]: TypeInformation[T] = fallback(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])

  private def fallback[T](kl: Class[T]): TypeInformation[T] = TypeInformation.of(kl)

}

