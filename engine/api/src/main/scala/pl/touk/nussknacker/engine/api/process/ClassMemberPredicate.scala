package pl.touk.nussknacker.engine.api.process

import java.lang.reflect.Member
import java.util.regex.Pattern

/**
  * Predicate for class members (fields & methods)
  */
trait ClassMemberPredicate {

  def matchesClass(clazz: Class[_]): Boolean

  def matchesMember(member: Member): Boolean

}

object ClassMemberPredicate {

  def apply(classPredicate: ClassPredicate, p: PartialFunction[Member, Boolean]): ClassMemberPredicate = new ClassMemberPredicate with Serializable {

    override def matchesClass(clazz: Class[_]): Boolean = classPredicate.matches(clazz)

    override def matchesMember(member: Member): Boolean = p.lift(member).getOrElse(false)

  }

}

/**
  * Simple implementation of ClassMemberPredicate based on class member's name pattern
  * @param classPredicate - class predicate
  * @param classMemberPattern - class member's name pattern
  */
case class ClassMemberPatternPredicate(classPredicate: ClassPredicate, classMemberPattern: Pattern) extends ClassMemberPredicate {

  override def matchesClass(clazz: Class[_]): Boolean = classPredicate.matches(clazz)

  override def matchesMember(member: Member): Boolean = classMemberPattern.matcher(member.getName).matches()

}

/**
 * Implementation of ClassMemberPredicate matching all methods that has the same name as methods in given class
 * @param clazz - class which method names will match predicate
 * @param exceptMethodNames - method names that will be excluded from matching
 */
case class AllMethodNamesPredicate(clazz: Class[_], exceptMethodNames: Set[String] = Set.empty) extends ClassMemberPredicate {

  private val matchingMethodNames = clazz.getMethods.map(_.getName).toSet -- exceptMethodNames

  override def matchesClass(clazz: Class[_]): Boolean = true

  override def matchesMember(member: Member): Boolean =matchingMethodNames.contains(member.getName)

}
