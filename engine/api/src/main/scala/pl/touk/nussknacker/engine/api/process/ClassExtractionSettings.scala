package pl.touk.nussknacker.engine.api.process

import java.lang.reflect.Member

/**
  * Settings for class extraction which is done to handle e.g. syntax suggestions in UI
  * @param blacklistedClassMemberPredicates - sequence of predicates to recognize blacklisted class members
  */
case class ClassExtractionSettings(blacklistedClassMemberPredicates: Seq[ClassMemberPredicate]) {

  def isBlacklisted(member: Member): Boolean =
    blacklistedClassMemberPredicates.exists(_.matches(member))

}

object ClassExtractionSettings {

  val Default: ClassExtractionSettings = ClassExtractionSettings(Seq.empty)

}