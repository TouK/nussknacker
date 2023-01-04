package pl.touk.nussknacker.engine.api.process

import org.apache.commons.lang3.ClassUtils

import java.util.regex.Pattern
import scala.reflect.ClassTag

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

case class BasePackagePredicate(basePackageName: String) extends ClassPredicate {
  override def matches(clazz: Class[_]): Boolean = clazz.getPackageName.startsWith(basePackageName)
}

object ExactClassPredicate {

  def apply[T: ClassTag]: ExactClassPredicate = ExactClassPredicate(implicitly[ClassTag[T]].runtimeClass)

}

case class ExceptOfClassesPredicate(predicate: ClassPredicate, exceptions: ClassPredicate) extends ClassPredicate {
  override def matches(clazz: Class[_]): Boolean = predicate.matches(clazz) && !exceptions.matches(clazz)
}

case class ExactClassPredicate(classes: Class[_]*) extends ClassPredicate {
  override def matches(clazz: Class[_]): Boolean = classes.contains(clazz)
}

/**
 * Predicate that matches all superclasses and interfaces based on pattern
 * @param superClassPredicate - class predicate
 */
case class SuperClassPredicate(superClassPredicate: ClassPredicate) extends ClassPredicate {

  def matches(clazz: Class[_]): Boolean =
    superClasses(clazz).exists(cl => superClassPredicate.matches(cl))

  private def superClasses(clazz: Class[_]): Seq[Class[_]] = {
    import scala.jdk.CollectionConverters._
    Seq(clazz) ++
      ClassUtils.getAllSuperclasses(clazz).asScala ++
      ClassUtils.getAllInterfaces(clazz).asScala
  }

}
