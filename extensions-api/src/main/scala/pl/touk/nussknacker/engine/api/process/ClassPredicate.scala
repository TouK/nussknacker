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
  * Matches classes using their exact names
  * @param classNames class names
  */
case class ClassNamePredicate(classNames: Set[String]) extends ClassPredicate {

  override def matches(clazz: Class[_]): Boolean = classNames.contains(clazz.getName)

}

object ClassNamePredicate {

  def apply(classNames: String*): ClassNamePredicate = ClassNamePredicate(classNames.toSet)

}

/**
  * Matches classes by prefix of their name
  * @param classPrefix class name prefix
  */
case class ClassNamePrefixPredicate(classPrefix: String) extends ClassPredicate {

  override def matches(clazz: Class[_]): Boolean = clazz.getName.startsWith(classPrefix)

}

/**
 * Matches classes by their name, using passed Pattern
 * @param classPattern class name pattern
 */
case class ClassPatternPredicate(classPattern: Pattern) extends ClassPredicate {

  override def matches(clazz: Class[_]): Boolean = classPattern.matcher(clazz.getName).matches()

}

case class BasePackagePredicate(basePackageName: String) extends ClassPredicate {
  override def matches(clazz: Class[_]): Boolean = clazz.getPackageName.startsWith(basePackageName)
}

object ExactClassPredicate {

  def apply[T: ClassTag]: ExactClassPredicate = ExactClassPredicate(implicitly[ClassTag[T]].runtimeClass)

  def apply(classes: Class[_]*): ExactClassPredicate = ExactClassPredicate(classes.toSet)

}

case class ExceptOfClassesPredicate(predicate: ClassPredicate, exceptions: ClassPredicate) extends ClassPredicate {
  override def matches(clazz: Class[_]): Boolean = predicate.matches(clazz) && !exceptions.matches(clazz)
}

case class ExactClassPredicate(classes: Set[Class[_]]) extends ClassPredicate {
  override def matches(clazz: Class[_]): Boolean = classes.contains(clazz)
}

/**
 * Predicate that matches all superclasses and interfaces based on pattern
 * @param classPredicate - class predicate
 */
case class SuperClassPredicate(classPredicate: ClassPredicate) extends ClassPredicate {
  import scala.jdk.CollectionConverters._

  def matches(clazz: Class[_]): Boolean = {
    // this is meant to be fast and do minimal allocations
    classPredicate.matches(clazz) ||
    ClassUtils.getAllSuperclasses(clazz).asScala.exists(cl => classPredicate.matches(cl)) ||
    ClassUtils.getAllInterfaces(clazz).asScala.exists(cl => classPredicate.matches(cl))
  }

}
