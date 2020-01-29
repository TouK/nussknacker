package pl.touk.nussknacker.engine.types

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.ClazzRef
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
    primitiveTypes.map(ClassUtils.primitiveToWrapper)).map(ClazzRef(_))

  def clazzAndItsChildrenDefinition(clazzes: Iterable[TypingResult])
                                   (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    // It's a deep first traversal - to avoid SOF we use mutable collection. It won't be easy to implement it using immutable collections
    val collectedSoFar = mutable.HashSet.empty[ClazzRef]
    (clazzes.flatMap(clazzRefsFromTypingResult) ++ mandatoryClasses).flatMap { cl =>
      clazzAndItsChildrenDefinition(cl)(collectedSoFar, DiscoveryPath(List(Clazz(cl))))
    }.toSet
  }

  private def clazzRefsFromTypingResult(typingResult: TypingResult): Set[ClazzRef] = typingResult match {
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

  private def clazzRefsFromTypedClass(typedClass: TypedClass): Set[ClazzRef]
    = typedClass.params.flatMap(clazzRefsFromTypingResult).toSet + ClazzRef(typedClass.klass)

  private def clazzAndItsChildrenDefinition(clazzRef: ClazzRef)
                                           (collectedSoFar: mutable.Set[ClazzRef], path: DiscoveryPath)
                                           (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    // we not support arrays for now - will looks ugly in UI, so we need to hide it
    if (clazzRef.clazz.isArray || collectedSoFar.contains(clazzRef)) {
      Set.empty
    } else {
      collectedSoFar += clazzRef
      val definitionsForClass = if (settings.isHidden(clazzRef.clazz)) {
        Set.empty
      } else {
        val classDefinition = clazzDefinitionWithLogging(clazzRef.clazz)(path)
        definitionsFromMethods(classDefinition)(collectedSoFar, path) + classDefinition
      }
      definitionsForClass ++ definitionsFromGenericParameters(clazzRef)(collectedSoFar, path)
    }
  }

  private def definitionsFromGenericParameters(clazzRef: ClazzRef)
                                              (collectedSoFar: mutable.Set[ClazzRef], path: DiscoveryPath)
                                              (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    clazzRef.params.zipWithIndex.flatMap {
      case (k, idx) => clazzAndItsChildrenDefinition(k)(collectedSoFar, path.pushSegment(GenericParameter(k, idx)))
    }.toSet
  }

  private def definitionsFromMethods(classDefinition: ClazzDefinition)
                                    (collectedSoFar: mutable.Set[ClazzRef], path: DiscoveryPath)
                                    (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    classDefinition.methods.values.flatMap { kl =>
      clazzAndItsChildrenDefinition(kl.refClazz)(collectedSoFar, path.pushSegment(MethodReturnType(kl)))
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
    protected def classNameWithStrippedPackages(cl: ClazzRef): String =
      cl.refClazzName.replaceAll("(.).*?\\.", "$1.")
  }

  private case class Clazz(cl: ClazzRef) extends DiscoverySegment {
    override def print: String = classNameWithStrippedPackages(cl)
  }

  private case class GenericParameter(cl: ClazzRef, ix: Int) extends DiscoverySegment {
    override def print: String = s"[$ix]${classNameWithStrippedPackages(cl)}"
  }

  private case class MethodReturnType(m: MethodInfo) extends DiscoverySegment {
    override def print: String = s"ret(${classNameWithStrippedPackages(m.refClazz)})"
  }

  private case class MethodParameter(p: Parameter) extends DiscoverySegment {
    override def print: String = s"${p.name}: ${classNameWithStrippedPackages(p.refClazz)}"
  }

}
