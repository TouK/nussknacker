package pl.touk.nussknacker.engine.definition.clazz

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.generics.Parameter
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.util.logging.ExecutionTimeMeasuring
import pl.touk.nussknacker.engine.variables.MetaVariables

import scala.collection.mutable

class ClassDefinitionDiscovery(classDefinitionExtractor: ClassDefinitionExtractor)
    extends LazyLogging
    with ExecutionTimeMeasuring {

  // TODO: this should be somewhere in utils??
  private val primitiveTypes: List[Class[_]] = List(
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
  // TODO: what else should be here?
  // TODO: Here, this static list of mandatory classes, should be replaced by mechanizm that allows
  // to plug in mandatory classes from other modules, e.g. BaseKafkaInputMetaVariables from kafka-util
  private val mandatoryClasses = (Set(
    classOf[java.util.List[_]],
    classOf[java.util.Map[_, _]],
    classOf[java.math.BigDecimal],
    classOf[Number],
    classOf[String],
    classOf[MetaVariables],
    classOf[Any]
  ) ++
    // Literals for primitive types are wrapped to boxed representations
    primitiveTypes.map(ClassUtils.primitiveToWrapper)).map(Typed(_))

  def discoverClasses(classes: Iterable[Class[_]]): Set[ClassDefinition] = {
    discoverClassesFromTypes(classes.map(Typed(_)))
  }

  def discoverClassesFromTypes(
      types: Iterable[TypingResult]
  ): Set[ClassDefinition] = {
    // It's a deep first traversal - to avoid SOF we use mutable collection. It won't be easy to implement it using immutable collections
    val collectedSoFar = mutable.HashSet.empty[TypingResult]
    (types.flatMap(typesFromTypingResult) ++ mandatoryClasses).flatMap { cl =>
      classAndItsChildrenDefinitionIfNotCollectedSoFar(cl)(collectedSoFar, DiscoveryPath(List(Clazz(cl))))
    }.toSet
  }

  private def typesFromTypingResult(typingResult: TypingResult): Set[TypingResult] = typingResult match {
    case TypedObjectTypingResult(fields, clazz, _) =>
      typesFromTypedClass(clazz) ++ fields.values.flatMap(typesFromTypingResult)
    case typedObjectWithData: SingleTypingResult =>
      typesFromTypedClass(typedObjectWithData.typeHintsObjType)
    case union: TypedUnion =>
      union.possibleTypes.toList.toSet.flatMap(typesFromTypingResult)
    case TypedNull =>
      Set.empty
    case Unknown =>
      Set.empty
  }

  private def typesFromTypedClass(typedClass: TypedClass): Set[TypingResult] =
    typedClass.params.flatMap(typesFromTypingResult).toSet + Typed(typedClass.klass)

  private def classAndItsChildrenDefinitionIfNotCollectedSoFar(
      typingResult: TypingResult
  )(collectedSoFar: mutable.Set[TypingResult], path: DiscoveryPath): Set[ClassDefinition] = {
    if (collectedSoFar.contains(typingResult)) {
      Set.empty
    } else {
      collectedSoFar += typingResult
      classAndItsChildrenDefinition(typingResult)(collectedSoFar, path)
    }
  }

  private def classAndItsChildrenDefinition(
      typingResult: TypingResult
  )(collectedSoFar: mutable.Set[TypingResult], path: DiscoveryPath): Set[ClassDefinition] = {
    typingResult match {
      case e: TypedClass =>
        val definitionsForClass = if (classDefinitionExtractor.settings.isHidden(e.klass)) {
          Set.empty
        } else {
          val classDefinition = extractDefinitionWithLogging(e.klass)(path)
          definitionsFromMethods(classDefinition)(collectedSoFar, path) + classDefinition
        }
        definitionsForClass ++ definitionsFromGenericParameters(e)(collectedSoFar, path)
      case Unknown => Set(extractDefinitionWithLogging(classOf[Any])(path))
      case _       => Set.empty[ClassDefinition]
    }
  }

  private def definitionsFromGenericParameters(
      typedClass: TypedClass
  )(collectedSoFar: mutable.Set[TypingResult], path: DiscoveryPath): Set[ClassDefinition] = {
    typedClass.params.zipWithIndex.flatMap {
      case (k: TypedClass, idx) =>
        classAndItsChildrenDefinitionIfNotCollectedSoFar(k)(collectedSoFar, path.pushSegment(GenericParameter(k, idx)))
      case _ => Set.empty[ClassDefinition]
    }.toSet
  }

  private def definitionsFromMethods(
      classDefinition: ClassDefinition
  )(collectedSoFar: mutable.Set[TypingResult], path: DiscoveryPath): Set[ClassDefinition] = {
    def extractFromMethods(methods: Map[String, List[MethodDefinition]]) =
      methods.values.flatten
        .flatMap(_.signatures.toList.map(_.result))
        .flatMap { kl =>
          classAndItsChildrenDefinitionIfNotCollectedSoFar(kl)(collectedSoFar, path.pushSegment(MethodReturnType(kl)))
          // TODO verify if parameters are need and if they are not, remove this
          //        ++ kl.parameters.flatMap(p => classAndItsChildrenDefinition(p.refClazz)(collectedSoFar, path.pushSegment(MethodParameter(p))))
        }
        .toSet

    extractFromMethods(classDefinition.methods) ++ extractFromMethods(classDefinition.staticMethods)
  }

  private def extractDefinitionWithLogging(
      clazz: Class[_]
  )(path: DiscoveryPath) = {
    def message = if (logger.underlying.isTraceEnabled) path.print else clazz.getName
    measure(message) {
      classDefinitionExtractor.extract(clazz)
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
      // In fact, should not happen except Unknown...
      case other => other.display
    }

  }

  private case class Clazz(cl: TypingResult) extends DiscoverySegment {
    override def print: String = classNameWithStrippedPackages(cl)
  }

  private case class GenericParameter(cl: TypingResult, ix: Int) extends DiscoverySegment {
    override def print: String = s"[$ix]${classNameWithStrippedPackages(cl)}"
  }

  private case class MethodReturnType(m: TypingResult) extends DiscoverySegment {
    override def print: String = s"ret(${classNameWithStrippedPackages(m)})"
  }

  private case class MethodParameter(p: Parameter) extends DiscoverySegment {
    override def print: String = s"${p.name}: ${classNameWithStrippedPackages(p.refClazz)}"
  }

}
