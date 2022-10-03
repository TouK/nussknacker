package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.{ListTypeInfo, MapTypeInfo}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.flink.api.typeinformation.{TypeInformationDetection, TypingResultAwareTypeInformationCustomisation}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject.{TypedJavaMapTypeInformation, TypedMapTypeInformation, TypedScalaMapTypeInformation}
import pl.touk.nussknacker.engine.process.typeinformation.internal.ContextTypeHelpers
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.reflect.ClassTag

object TypingResultAwareTypeInformationDetection {

  def apply(classLoader: ClassLoader): TypingResultAwareTypeInformationDetection = {
    val customisations = ScalaServiceLoader.load[TypingResultAwareTypeInformationCustomisation](classLoader)
    new TypingResultAwareTypeInformationDetection(new CompositeCustomisation(customisations))
  }

  class CompositeCustomisation(customisations: List[TypingResultAwareTypeInformationCustomisation]) extends TypingResultAwareTypeInformationCustomisation {
    override def customise(originalDetection: TypeInformationDetection): PartialFunction[TypingResult, TypeInformation[_]] =
      customisations.map(_.customise(originalDetection)).reduceOption(_.orElse(_)).getOrElse(Map.empty)
  }

}

// TODO: handle avro types - see FlinkConfluentUtils
/*
  This is *experimental* TypeInformationDetection, which generates TypeInformation based on ValidationContext and TypingResult.
  Please note that it is much more sensitive to differences between ValidationContext and real values (e.g. Int vs Long etc...)
  (see TypingResultAwareTypeInformationDetectionSpec."number promotion behaviour" test)

  To use it for serialization between operators use TypeInformationDetection service loading.
  To use it for state serialization one can use it directly in operators/process functions (compatibility is *NOT* guaranteed ATM).
 */
class TypingResultAwareTypeInformationDetection(customisation:
                                                TypingResultAwareTypeInformationCustomisation) extends TypeInformationDetection {

  private val registeredTypeInfos: Set[TypeInformation[_]] = {
    Set(
      TypeInformation.of(classOf[BigDecimal])
    )
  }

  def forContext(validationContext: ValidationContext): TypeInformation[Context] = {
    val variables = forType(TypedObjectTypingResult(validationContext.localVariables.toList, Typed.typedClass[Map[String, AnyRef]]))
      .asInstanceOf[TypeInformation[Map[String, Any]]]
    val parentCtx = validationContext.parent.map(forContext)

    ContextTypeHelpers.infoFromVariablesAndParentOption(variables, parentCtx)
  }

  //This is based on TypeInformationGen macro
  def generateTraversable(traversableClass: Class[_], elementTpi: TypeInformation[AnyRef]): TypeInformation[_] = {
    new TraversableTypeInfo[TraversableOnce[AnyRef], AnyRef](traversableClass.asInstanceOf[Class[TraversableOnce[AnyRef]]], elementTpi) {
      override def createSerializer(executionConfig: ExecutionConfig): TypeSerializer[TraversableOnce[AnyRef]] = {
        val traversableClassName = s"${traversableClass.getName}[AnyRef]"
        new TraversableSerializer[TraversableOnce[AnyRef], AnyRef](elementTpi.createSerializer(executionConfig),
          s"implicitly[scala.collection.generic.CanBuildFrom[$traversableClassName, AnyRef, $traversableClassName]]")
      }
    }
  }

  def forType(typingResult: TypingResult): TypeInformation[AnyRef] = {
    (typingResult match {
      case a if additionalTypeInfoDeterminer.isDefinedAt(a) =>
        additionalTypeInfoDeterminer.apply(a)
      case a: TypedTaggedValue => forType(a.underlying)
      case a: TypedDict => forType(a.objType)
      case a: TypedClass if a.params.isEmpty =>
        //TODO: scala case classes are not handled nicely here... CaseClassTypeInfo is created only via macro, here Kryo is used
        registeredTypeInfos.find(_.getTypeClass == a.klass).getOrElse(TypeInformation.of(a.klass))

      case a: TypedClass if a.klass == classOf[java.util.List[_]] => new ListTypeInfo[AnyRef](forType(a.params.head))

      case TraversableType(traversableClass, elementType) => generateTraversable(traversableClass, forType(elementType))

      case a: TypedClass if a.klass == classOf[java.util.Map[_, _]] => new MapTypeInfo[AnyRef, AnyRef](forType(a.params.head), forType(a.params.last))

      case a: TypedObjectTypingResult if a.objType.klass == classOf[Map[String, _]] =>
        TypedScalaMapTypeInformation(a.fields.mapValuesNow(forType))
      case a: TypedObjectTypingResult if a.objType.klass == classOf[TypedMap] =>
        TypedMapTypeInformation(a.fields.mapValuesNow(forType))
      //TODO: better handle specific map implementations - other than HashMap?
      case a: TypedObjectTypingResult if classOf[java.util.Map[String, _]].isAssignableFrom(a.objType.klass) =>
        TypedJavaMapTypeInformation(a.fields.mapValuesNow(forType))
      //TODO: how can we handle union - at least of some types?
      case _ =>
        fallback[Any]
    }).asInstanceOf[TypeInformation[AnyRef]]
  }

  def forValueWithContext[T](validationContext: ValidationContext, value: TypingResult): TypeInformation[ValueWithContext[T]] = {
    val valueType = forType(value)
    val finalContext = forContext(validationContext)

    ConcreteCaseClassTypeInfo[ValueWithContext[T]](
      ("value", valueType),
      ("context", finalContext)
    )
  }

  //we have def here, as Scala 2.11 has problems with serialization of PartialFunctions...
  private def additionalTypeInfoDeterminer = customisation.customise(this)

  private def fallback[T: ClassTag]: TypeInformation[T] = fallback(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])

  private def fallback[T](kl: Class[T]): TypeInformation[T] = TypeInformation.of(kl)

}

private object TraversableType {

  //we have to pick exact types, to avoid problems with "::" classes etc.
  private val handledTypes = List(classOf[List[_]], classOf[Seq[_]])

  def unapply(typedClass: TypingResult): Option[(Class[_], TypingResult)] = typedClass match {
    case TypedClass(klass, param :: Nil) => handledTypes.find(_.isAssignableFrom(klass)).map((_, param))
    case _ => None
  }

}

