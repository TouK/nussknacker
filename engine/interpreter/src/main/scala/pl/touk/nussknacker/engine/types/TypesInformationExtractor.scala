package pl.touk.nussknacker.engine.types

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo, Parameter}
import pl.touk.nussknacker.engine.types.EspTypeUtils.clazzDefinition
import pl.touk.nussknacker.engine.util.logging.ExecutionTimeMeasuring
import pl.touk.nussknacker.engine.variables.MetaVariables

import scala.collection.mutable

object TypesInformationExtractor extends LazyLogging with ExecutionTimeMeasuring {

  //TODO: this should be somewhere in utils??
  private val primitiveTypes : List[Class[_]] = List(
    java.lang.Boolean.TYPE,
    java.lang.Integer.TYPE,
    java.lang.Long.TYPE,
    java.lang.Float.TYPE,
    java.lang.Double.TYPE,
    java.lang.Byte.TYPE,
    java.lang.Short.TYPE,
    java.lang.Character.TYPE
  )

  // We have some types that can't be discovered based on model but can by provided using e.g. literals
  //TODO: what else should be here?
  //TODO: Here, this static list of mandatory classes, should be replaced by mechanizm that allows
  // to plug in mandatory classes from other modules, e.g. BaseKafkaInputMetaVariables from kafka-util
  private val mandatoryClasses = (Set(
    classOf[java.util.List[_]],
    classOf[java.util.Map[_, _]],
    classOf[java.math.BigDecimal],
    classOf[Number],
    classOf[String],
    classOf[MetaVariables]
  ) ++
    // Literals for primitive types are wrapped to boxed representations
    primitiveTypes.map(ClassUtils.primitiveToWrapper)).map(Typed(_))

  def clazzAndItsChildrenDefinition(clazzes: Iterable[TypingResult])
                                   (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    // It's a deep first traversal - to avoid SOF we use mutable collection. It won't be easy to implement it using immutable collections
    val collectedSoFar = mutable.HashSet.empty[TypingResult]
    (clazzes.flatMap(typesFromTypingResult) ++ mandatoryClasses).flatMap { cl =>
      clazzAndItsChildrenDefinitionIfNotCollectedSoFar(cl)(collectedSoFar, DiscoveryPath(List(Clazz(cl))))
    }.toSet
  }

  private def typesFromTypingResult(typingResult: TypingResult): Set[TypingResult] = typingResult match {
    case tc: TypedClass =>
      typesFromTypedClass(tc)
    case TypedUnion(set) =>
      set.flatMap(typesFromTypingResult)
    case TypedObjectTypingResult(fields, clazz, _) =>
      typesFromTypedClass(clazz) ++ fields.values.flatMap(typesFromTypingResult)
    case dict: TypedDict =>
      typesFromTypedClass(dict.objType)
    case TypedTaggedValue(underlying, _) =>
      typesFromTypedClass(underlying.objType)
    case Unknown =>
      Set.empty
  }

  private def typesFromTypedClass(typedClass: TypedClass): Set[TypingResult]
    = typedClass.params.flatMap(typesFromTypingResult).toSet + Typed(typedClass.klass)

  private def clazzAndItsChildrenDefinitionIfNotCollectedSoFar(typingResult: TypingResult)
                                                              (collectedSoFar: mutable.Set[TypingResult], path: DiscoveryPath)
                                                              (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    if (collectedSoFar.contains(typingResult)) {
      Set.empty
    } else {
      collectedSoFar += typingResult
      clazzAndItsChildrenDefinition(typingResult)(collectedSoFar, path)
    }
  }

  private def clazzAndItsChildrenDefinition(typingResult: TypingResult)
                                           (collectedSoFar: mutable.Set[TypingResult], path: DiscoveryPath)
                                           (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    typingResult match {
      case e:TypedClass =>
        val definitionsForClass = if (settings.isHidden(e.klass)) {
          Set.empty
        } else {
          val classDefinition = clazzDefinitionWithLogging(e.klass)(path)
          definitionsFromMethods(classDefinition)(collectedSoFar, path) + classDefinition
        }
        definitionsForClass ++ definitionsFromGenericParameters(e)(collectedSoFar, path)
      case _ => Set.empty[ClazzDefinition]
    }
  }

  private def definitionsFromGenericParameters(typedClass: TypedClass)
                                              (collectedSoFar: mutable.Set[TypingResult], path: DiscoveryPath)
                                              (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    typedClass.params.zipWithIndex.flatMap {
      case (k:TypedClass, idx) => clazzAndItsChildrenDefinitionIfNotCollectedSoFar(k)(collectedSoFar, path.pushSegment(GenericParameter(k, idx)))
      case _ => Set.empty[ClazzDefinition]
    }.toSet
  }

  private def definitionsFromMethods(classDefinition: ClazzDefinition)
                                    (collectedSoFar: mutable.Set[TypingResult], path: DiscoveryPath)
                                    (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    classDefinition.methods.values.flatten.flatMap { kl =>
      clazzAndItsChildrenDefinitionIfNotCollectedSoFar(kl.refClazz)(collectedSoFar, path.pushSegment(MethodReturnType(kl)))
      // TODO verify if parameters are needed and if they are not, remove this
//        ++ kl.parameters.flatMap(p => clazzAndItsChildrenDefinition(p.refClazz)(collectedSoFar, path.pushSegment(MethodParameter(p))))
    }.toSet ++
    classDefinition.staticMethods.values.flatten.flatMap { kl =>
      clazzAndItsChildrenDefinitionIfNotCollectedSoFar(kl.refClazz)(collectedSoFar, path.pushSegment(MethodReturnType(kl)))
    }.toSet
  }

  private def clazzDefinitionWithLogging(clazz: Class[_])
                                        (path: DiscoveryPath)
                                        (implicit settings: ClassExtractionSettings) = {
    def message = if (logger.underlying.isTraceEnabled) path.print else clazz.getName
    measure(message) {
      clazzDefinition(clazz)
    }
  }

  private case class DiscoveryPath(path: List[DiscoverySegment]) {
    def pushSegment(seg: DiscoverySegment): DiscoveryPath = DiscoveryPath(seg :: path)
    def print: String = {
      path.reverse.map(_.print).mkString(" > ")
    }
  }

  private sealed trait DiscoverySegment {
    def print: String
    protected def classNameWithStrippedPackages(cl: TypingResult): String = cl match {
      case TypedClass(klass, _) => klass.getCanonicalName.replaceAll("(.).*?\\.", "$1.")
      //In fact, should not happen except Unknown...
      case other => other.display
    }
  }

  private case class Clazz(cl: TypingResult) extends DiscoverySegment {
    override def print: String = classNameWithStrippedPackages(cl)
  }

  private case class GenericParameter(cl: TypingResult, ix: Int) extends DiscoverySegment {
    override def print: String = s"[$ix]${classNameWithStrippedPackages(cl)}"
  }

  private case class MethodReturnType(m: MethodInfo) extends DiscoverySegment {
    override def print: String = s"ret(${classNameWithStrippedPackages(m.refClazz)})"
  }

  private case class MethodParameter(p: Parameter) extends DiscoverySegment {
    override def print: String = s"${p.name}: ${classNameWithStrippedPackages(p.refClazz)}"
  }

}
