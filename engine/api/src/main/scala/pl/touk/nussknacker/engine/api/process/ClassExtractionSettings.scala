package pl.touk.nussknacker.engine.api.process

import java.lang.reflect.Member
import java.util.regex.Pattern

/**
  * Settings for class extraction which is done to handle e.g. syntax suggestions in UI
  * @param blacklistedClassPredicates - sequence of predicates to recognize blacklisted classes
  * @param blacklistedClassMemberPredicates - sequence of predicates to recognize blacklisted class members - will be
 *                                            used all predicates that matches given class
  * @param whitelistedClassMemberPredicates - sequence of predicates to recognize whitelisted class members - will be
 *                                            used first predicate that matches given class
  */
case class ClassExtractionSettings(blacklistedClassPredicates: Seq[ClassPredicate],
                                   blacklistedClassMemberPredicates: Seq[ClassMemberPredicate],
                                   whitelistedClassMemberPredicates: Seq[ClassMemberPredicate]) {

  def isBlacklisted(clazz: Class[_]): Boolean =
    blacklistedClassPredicates.exists(_.matches(clazz))

  def visibleMembersPredicate(clazz: Class[_]): VisibleMembersPredicate =
    VisibleMembersPredicate(
      blacklistedClassMemberPredicates.filter(p => p.matchesClass(clazz)),
      whitelistedClassMemberPredicates.find(p => p.matchesClass(clazz)))

}

case class VisibleMembersPredicate(blacklist: Seq[ClassMemberPredicate], whitelist: Option[ClassMemberPredicate]) {

  def shouldBeVisible(member: Member): Boolean =
    !blacklist.exists(_.matchesMember(member)) && whitelist.forall(_.matchesMember(member))

}

object ClassExtractionSettings {

  val ToStringMethod = "toString"

  val Default: ClassExtractionSettings = ClassExtractionSettings(DefaultBlacklistedClasses, DefaultBlacklistedMembers, DefaultWhitelistedMembers)

  lazy val DefaultBlacklistedClasses: List[ClassPredicate] =
    List(
      // Void types
      ClassPatternPredicate(Pattern.compile("void")),
      ClassPatternPredicate(Pattern.compile("java\\.lang\\.Void")),
      ClassPatternPredicate(Pattern.compile("scala\\.Unit.*")),
      ClassPatternPredicate(Pattern.compile("scala\\.runtime\\.BoxedUnit")),

      // In case if there is some public method with flink's TypeInformation on serialization purpose
      ClassPatternPredicate(Pattern.compile("org\\.apache\\.flink\\.api\\.common\\.typeinfo\\.TypeInformation")),
      // We use this type only programmable
      ClassPatternPredicate(Pattern.compile("pl\\.touk\\.nussknacker\\.engine\\.spel\\.SpelExpressionRepr")),
      // In case if someone use it for kind of meta programming
      ClassPatternPredicate(Pattern.compile("java\\.lang\\.Class")),
      // In case if someone return function for lazy evaluation purpose
      ClassPatternPredicate(Pattern.compile("java\\.util\\.function\\..*")),
      ClassPatternPredicate(Pattern.compile("scala\\.Function.*")),
      // java xml api is not easy to use without additional helpers, so we will skip these classes
      ClassPatternPredicate(Pattern.compile("javax\\.xml\\..*")),
      // Not sure why these below exclusions are TODO describe why they should be here or remove it
      ClassPatternPredicate(Pattern.compile("dispatch\\..*")),
      ClassPatternPredicate(Pattern.compile("cats\\..*"))
    )

  lazy val DefaultBlacklistedMembers: List[ClassMemberPredicate] = CommonBlacklistedMembers ++ AvroBlacklistedMembers

  lazy val CommonBlacklistedMembers: List[ClassMemberPredicate] =
    List(
      // We want to hide all technical methods in every class, toString can be useful so we will leave it
      AllMethodNamesPredicate(classOf[DumpCaseClass], Set(ToStringMethod))
    )

  lazy val AvroBlacklistedMembers: List[ClassMemberPredicate] =
    List(
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("org\\.apache\\.avro\\.generic\\.IndexedRecord")),
        Pattern.compile("(getSchema|compareTo|put)")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("org\\.apache\\.avro\\.specific\\.SpecificRecordBase")),
        Pattern.compile("(getConverion|getConversion|writeExternal|readExternal|toByteBuffer|set[A-Z].*)"))
    )

  lazy val DefaultWhitelistedMembers: List[ClassMemberPredicate] = WhitelistedUtilMembers ++ WhitelistedSerializableMembers ++ WhitelistedStdMembers

  lazy val WhitelistedUtilMembers: List[ClassMemberPredicate] =
    List(
      // For numeric types, strings an collections, date types we want to see all useful methods
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("(java\\.lang\\.Number|java\\.util\\.Date|java\\.util\\.Calendar|java\\.util\\.concurrent\\.TimeUnit)")),
        Pattern.compile(".*")),
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("java\\.time\\..*")),
        Pattern.compile(".*")),
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("scala\\.concurrent\\.duration\\..*")),
        Pattern.compile(".*")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("java\\.lang\\.CharSequence")),
        Pattern.compile(s"charAt|compareTo.*|concat|contains|endsWith|equalsIgnoreCase|isEmpty|lastIndexOf|length|matches|" +
          s"replaceAll|replaceFirst|split|startsWith|substring|toLowerCase|toUpperCase|trim|$ToStringMethod")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("java\\.util\\.Collection")),
        Pattern.compile(s"contains|containsAll|get|getOrDefault|indexOf|isEmpty|size|$ToStringMethod")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("java\\.util\\.Optional")),
        Pattern.compile(s"get|isPresent|orElse|$ToStringMethod")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("(scala\\.collection\\.Traversable|scala\\.Option)")),
        Pattern.compile(s"apply|applyOrElse|contains|get|getOrDefault|indexOf|isDefined|isEmpty|size|$ToStringMethod"))
    )

  lazy val WhitelistedSerializableMembers: List[ClassMemberPredicate] =
    List(
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("scala\\.xml\\..*")),
        Pattern.compile(ToStringMethod)),
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("(io\\.circe\\..*|argonaut\\..*)")),
        Pattern.compile(s"noSpaces|spaces2|spaces4|$ToStringMethod"))
    )

  lazy val WhitelistedStdMembers: List[ClassMemberPredicate] =
    List(
      // For other std types we don't want to see anything but toString method
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("java\\..*")),
        Pattern.compile(ToStringMethod)),
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("scala\\..*")),
        Pattern.compile(ToStringMethod))
    )

  private case class DumpCaseClass()

}
