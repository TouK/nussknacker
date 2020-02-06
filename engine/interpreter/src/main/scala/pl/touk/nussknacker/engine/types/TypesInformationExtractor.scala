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
  private val mandatoryClasses = (Set(
    classOf[java.util.List[_]],
    classOf[java.util.Map[_, _]],
    classOf[java.math.BigDecimal],
    classOf[Number],
    classOf[String],
    classOf[MetaVariables]
  ) ++
    // Literals for primitive types are wrapped to boxed representations
    primitiveTypes.map(ClassUtils.primitiveToWrapper)).map(TypedClass(_))

  def clazzAndItsChildrenDefinition(clazzes: Iterable[TypingResult])
                                   (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    // It's a deep first traversal - to avoid SOF we use mutable collection. It won't be easy to implement it using immutable collections
    val collectedSoFar = mutable.HashSet.empty[ClassLike]
    (clazzes.flatMap(clazzRefsFromTypingResult) ++ mandatoryClasses).flatMap { cl =>
      clazzAndItsChildrenDefinitionIfNotCollectedSoFar(cl)(collectedSoFar, DiscoveryPath(List(Clazz(cl))))
    }.toSet
  }

  private def clazzRefsFromTypingResult(typingResult: TypingResult): Set[ClassLike] = typingResult match {
    case tc: TypedClass =>
      clazzRefsFromTypedClass(tc)
    case TypedUnion(set) =>
      set.flatMap(clazzRefsFromTypingResult)
    case TypedObjectTypingResult(fields, clazz) =>
      clazzRefsFromTypedClass(clazz) ++ fields.values.flatMap(clazzRefsFromTypingResult)
    case dict: TypedDict =>
      clazzRefsFromTypedClass(dict.objType)
    case TypedTaggedValue(underlying, _) =>
      clazzRefsFromTypedClass(underlying.objType)
    case Unknown =>
      Set.empty
  }

  private def clazzRefsFromTypedClass(typedClass: TypedClass): Set[ClassLike]
    = typedClass.params.flatMap(clazzRefsFromTypingResult).toSet + TypedClass(typedClass.klass)

  private def clazzAndItsChildrenDefinitionIfNotCollectedSoFar(clazzRef: ClassLike)
                                                              (collectedSoFar: mutable.Set[ClassLike], path: DiscoveryPath)
                                                              (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    if (collectedSoFar.contains(clazzRef)) {
      Set.empty
    } else {
      collectedSoFar += clazzRef
      clazzAndItsChildrenDefinition(clazzRef)(collectedSoFar, path)
    }
  }

  private def clazzAndItsChildrenDefinition(clazzRef: ClassLike)
                                           (collectedSoFar: mutable.Set[ClassLike], path: DiscoveryPath)
                                           (implicit settings: ClassExtractionSettings) = {
    val definitionsForClass = if (settings.isHidden(clazzRef.klass)) {
      Set.empty
    } else {
      val classDefinition = clazzDefinitionWithLogging(clazzRef.klass)(path)
      definitionsFromMethods(classDefinition)(collectedSoFar, path) + classDefinition
    }
    definitionsForClass ++ definitionsFromGenericParameters(clazzRef)(collectedSoFar, path)
  }

  private def definitionsFromGenericParameters(clazzRef: ClassLike)
                                              (collectedSoFar: mutable.Set[ClassLike], path: DiscoveryPath)
                                              (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    (if (clazzRef.isInstanceOf[TypedClass]) clazzRef.typedClassUnsafe.params else Nil ).zipWithIndex.flatMap {
      case (k:TypedClass, idx) => clazzAndItsChildrenDefinitionIfNotCollectedSoFar(k)(collectedSoFar, path.pushSegment(GenericParameter(k, idx)))
      case _ => Set.empty[ClazzDefinition]
    }.toSet
  }

  private def definitionsFromMethods(classDefinition: ClazzDefinition)
                                    (collectedSoFar: mutable.Set[ClassLike], path: DiscoveryPath)
                                    (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    classDefinition.methods.values.flatMap { kl =>
      clazzAndItsChildrenDefinitionIfNotCollectedSoFar(kl.refClazz)(collectedSoFar, path.pushSegment(MethodReturnType(kl)))
      // TODO verify if parameters are need and if they are not, remove this
//        ++ kl.parameters.flatMap(p => clazzAndItsChildrenDefinition(p.refClazz)(collectedSoFar, path.pushSegment(MethodParameter(p))))
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
    protected def classNameWithStrippedPackages(cl: ClassLike): String =
      cl.klass.getCanonicalName.replaceAll("(.).*?\\.", "$1.")
  }

  private case class Clazz(cl: ClassLike) extends DiscoverySegment {
    override def print: String = classNameWithStrippedPackages(cl)
  }

  private case class GenericParameter(cl: ClassLike, ix: Int) extends DiscoverySegment {
    override def print: String = s"[$ix]${classNameWithStrippedPackages(cl)}"
  }

  private case class MethodReturnType(m: MethodInfo) extends DiscoverySegment {
    override def print: String = s"ret(${classNameWithStrippedPackages(m.refClazz)})"
  }

  private case class MethodParameter(p: Parameter) extends DiscoverySegment {
    override def print: String = s"${p.name}: ${classNameWithStrippedPackages(p.refClazz)}"
  }

}
