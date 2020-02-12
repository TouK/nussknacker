package pl.touk.nussknacker.engine.api.process

import java.lang.reflect.Member
import java.util.regex.Pattern

import org.apache.commons.lang3.ClassUtils

/**
 * Predicate for classes
 */
trait ClassPredicate {

  def matches(clazz: Class[_]): Boolean

}

object ClassPredicate {

  def apply(p: PartialFunction[Class[_], Boolean]): ClassPredicate = new ClassPredicate with Serializable {
    override def matches(clazz: Class[_]): Boolean = p.lift(clazz).getOrElse(false)
  }

}

/**
 * Simple implementation of ClassPredicate based on pattern of class name
 * @param classPattern - class name pattern
 */
case class ClassPatternPredicate(classPattern: Pattern) extends ClassPredicate {

  override def matches(clazz: Class[_]): Boolean = classPattern.matcher(clazz.getName).matches()

}

/**
 * Predicate that matches all superclasses and interfaces based on pattern
 * @param superClassPattern - class name pattern
 */
case class SuperClassPatternPredicate(superClassPattern: Pattern) extends ClassPredicate {

  def matches(clazz: Class[_]): Boolean =
    superClasses(clazz).exists(cl => superClassPattern.matcher(cl.getName).matches())

  private def superClasses(clazz: Class[_]): Seq[Class[_]] = {
    import scala.collection.JavaConverters._
    Seq(clazz) ++
      ClassUtils.getAllSuperclasses(clazz).asScala ++
      ClassUtils.getAllInterfaces(clazz).asScala
  }

}
