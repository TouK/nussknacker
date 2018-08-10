package pl.touk.nussknacker.engine.api.process

import java.lang.reflect.Member
import java.util.regex.Pattern

/**
  * Settings for class extraction which is done to handle e.g. syntax suggestions in UI
  * @param blacklistedClassMemberPredicates - sequence of predicates to recognize blacklisted class members
  */
case class ClassExtractionSettings(blacklistedClassMemberPredicates: Seq[ClassMemberPredicate]) {

  def isBlacklisted(member: Member): Boolean =
    blacklistedClassMemberPredicates.exists(_.matches(member))

}

object ClassExtractionSettings {

  val Default: ClassExtractionSettings = ClassExtractionSettings(AvroBlacklistedMembers)

  lazy val AvroBlacklistedMembers: List[ClassMemberPatternPredicate] =
    List(
      ClassMemberPatternPredicate(
        Pattern.compile("org\\.apache\\.avro\\.generic\\.IndexedRecord"),
        Pattern.compile("(getSchema|compareTo|put)")),
      ClassMemberPatternPredicate(
        Pattern.compile("org\\.apache\\.avro\\.specific\\.SpecificRecordBase"),
        Pattern.compile("(getConverion|getConversion|writeExternal|readExternal|toByteBuffer|set[A-Z].*)"))
    )

}