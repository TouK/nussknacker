package pl.touk.nussknacker.engine.api.process

import java.lang.reflect.{Field, Member, Method}
import java.util.regex.Pattern

import org.apache.commons.lang3.ClassUtils

/**
  * Predicate for class members (fields & methods)
  */
trait ClassMemberPredicate {

  def matches(member: Member): Boolean

}

/**
  * Simple implementation of ClassMemberPredicate based on class name pattern and class member's name pattern
  * @param classPattern - class name pattern
  * @param classMemberPattern - class member's name pattern
  */
case class ClassMemberPatternPredicate(classPattern: Pattern, classMemberPattern: Pattern) extends ClassMemberPredicate {

  override def matches(member: Member): Boolean = classMatches(member) && classMemberPattern.matcher(member.getName).matches()

  private def classMatches(member: Member) =
    superClasses(member.getDeclaringClass).exists(cl => classPattern.matcher(cl.getName).matches())

  private def superClasses(clazz: Class[_]): Seq[Class[_]] = {
    import scala.collection.JavaConverters._
    Seq(clazz) ++
      ClassUtils.getAllSuperclasses(clazz).asScala ++
      ClassUtils.getAllInterfaces(clazz).asScala
  }

}
