package pl.touk.nussknacker.engine.api.process

import java.lang.reflect.{Field, Member, Method}
import java.util.regex.Pattern

/**
  * Predicate for class members (fields & methods)
  */
trait ClassMemberPredicate {

  final def matchesClassMember(clazz: Class[_], member: Member): Boolean = matchesClass(clazz) && matchesMember(member)

  def matchesClass(clazz: Class[_]): Boolean

  def matchesMember(member: Member): Boolean

}

object ClassMemberPredicate {

  def apply(classPredicate: ClassPredicate, p: PartialFunction[Member, Boolean]): ClassMemberPredicate =
    new ClassMemberPredicate with Serializable {

      override def matchesClass(clazz: Class[_]): Boolean = classPredicate.matches(clazz)

      override def matchesMember(member: Member): Boolean = p.lift(member).getOrElse(false)

    }

}

case class ReturnMemberPredicate(returnClassPredicate: ClassPredicate, classPredicate: ClassPredicate = _ => true)
    extends ClassMemberPredicate {

  override def matchesClass(clazz: Class[_]): Boolean = classPredicate.matches(clazz)

  override def matchesMember(member: Member): Boolean = member match {
    case m: Method => returnClassPredicate.matches(m.getReturnType)
    case f: Field  => returnClassPredicate.matches(f.getType)
    case _         => false
  }

}

/**
  * Simple implementation of ClassMemberPredicate based on class member's name
  * @param classPredicate - class predicate
  * @param memberNames - class member names
  */
case class MemberNamePredicate(classPredicate: ClassPredicate, memberNames: Set[String]) extends ClassMemberPredicate {

  override def matchesClass(clazz: Class[_]): Boolean = classPredicate.matches(clazz)

  override def matchesMember(member: Member): Boolean = memberNames.contains(member.getName)

}

/**
  * Simple implementation of ClassMemberPredicate based on class member's name pattern
  * @param classPredicate - class predicate
  * @param memberNamePattern - class member's name pattern
  */
case class MemberNamePatternPredicate(classPredicate: ClassPredicate, memberNamePattern: Pattern)
    extends ClassMemberPredicate {

  override def matchesClass(clazz: Class[_]): Boolean = classPredicate.matches(clazz)

  override def matchesMember(member: Member): Boolean = memberNamePattern.matcher(member.getName).matches()

}

/**
 * Implementation of ClassMemberPredicate matching all methods that has the same name as methods in given class
 * @param clazz - class which method names will match predicate
 * @param exceptNames - method names that will be excluded from matching
 */
case class AllMembersPredicate(clazz: Class[_], exceptNames: Set[String] = Set.empty) extends ClassMemberPredicate {

  private val matchingMethodNames = clazz.getMethods.map(_.getName).toSet -- exceptNames

  override def matchesClass(clazz: Class[_]): Boolean = true

  override def matchesMember(member: Member): Boolean = matchingMethodNames.contains(member.getName)

}
