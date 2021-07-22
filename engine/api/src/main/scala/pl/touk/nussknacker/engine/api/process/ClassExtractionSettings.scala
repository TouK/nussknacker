package pl.touk.nussknacker.engine.api.process

import java.lang.reflect.{AccessibleObject, Field, Member, Method, Modifier}
import java.util.regex.Pattern

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{Hidden, HideToString}

/**
  * Settings for class extraction which is done to handle e.g. syntax suggestions in UI
 *
 * One-liner that will help to find list of interesting methods in class:
 * {{{
 * classOf[CLASS_NAME].getMethods.filterNot(m => java.lang.reflect.Modifier.isStatic(m.getModifiers)).map(_.getName)
 *   .filterNot(Set("wait", "notify", "notifyAll", "equals", "hashCode", "getClass").contains).distinct.sorted.mkString("|")
 * }}}
 *
  * @param excludeClassPredicates - sequence of predicates to determine hidden classes
  * @param excludeClassMemberPredicates - sequence of predicates to determine excluded class members - will be
  *                                       used all predicates that matches given class
  * @param includeClassMemberPredicates - sequence of predicates to determine included class members - will be
  *                                       used all predicates that matches given class. If none is matching,
  *                                       all non-excluded members will be visible.
  * @param propertyExtractionStrategy - strategy for extraction property based on getter. It can be defined
  *                                     what will happen if some class has 'getField' or 'isField' method.
  *                                     It can be: added 'field' property next to 'getField', replaced 'getField' with
  *                                     'field' or leaved as it is.
  */
case class ClassExtractionSettings(excludeClassPredicates: Seq[ClassPredicate],
                                   excludeClassMemberPredicates: Seq[ClassMemberPredicate],
                                   includeClassMemberPredicates: Seq[ClassMemberPredicate],
                                   propertyExtractionStrategy: PropertyFromGetterExtractionStrategy) {

  def isHidden(clazz: Class[_]): Boolean =
    excludeClassPredicates.exists(_.matches(clazz))

  def visibleMembersPredicate(clazz: Class[_]): VisibleMembersPredicate = {
    VisibleMembersPredicate(
      excludeClassMemberPredicates.filter(p => p.matchesClass(clazz)),
      NonEmptyList.fromList(includeClassMemberPredicates.filter(p => p.matchesClass(clazz)).toList))
  }

}

case class VisibleMembersPredicate(excludePredicates: Seq[ClassMemberPredicate], includePredicates: Option[NonEmptyList[ClassMemberPredicate]]) {

  def shouldBeVisible(member: Member): Boolean =
    !excludePredicates.exists(_.matchesMember(member)) && includePredicates.forall(_.exists(_.matchesMember(member)))

}

sealed trait PropertyFromGetterExtractionStrategy

object PropertyFromGetterExtractionStrategy {

  case object AddPropertyNextToGetter extends PropertyFromGetterExtractionStrategy

  case object ReplaceGetterWithProperty extends PropertyFromGetterExtractionStrategy

  case object DoNothing extends PropertyFromGetterExtractionStrategy

}


object ClassExtractionSettings {

  val ToStringMethod = "toString"

  val Default: ClassExtractionSettings = ClassExtractionSettings(DefaultExcludedClasses, DefaultExcludedMembers, DefaultIncludedMembers,
    PropertyFromGetterExtractionStrategy.AddPropertyNextToGetter)

  lazy val DefaultExcludedClasses: List[ClassPredicate] = ExcludedStdClasses ++ ExcludedExtraClasses

  lazy val ExcludedStdClasses: List[ClassPredicate] =
    List(
      // Void types
      ClassPatternPredicate(Pattern.compile("void")),
      ClassPatternPredicate(Pattern.compile("java\\.lang\\.Void")),
      ClassPatternPredicate(Pattern.compile("scala\\.Unit.*")),
      ClassPatternPredicate(Pattern.compile("scala\\.runtime\\.BoxedUnit")),

      // In case if someone use it for kind of meta programming
      ClassPatternPredicate(Pattern.compile("java\\.lang\\.Class")),
      // In case if someone return function for lazy evaluation purpose
      ClassPatternPredicate(Pattern.compile("java\\.util\\.function\\..*")),
      ClassPatternPredicate(Pattern.compile("scala\\.Function.*")),

      // Arrays are not supported for now
      ClassPredicate { case cl => cl.isArray },

      // We use this type only programmable
      ClassPatternPredicate(Pattern.compile("pl\\.touk\\.nussknacker\\.engine\\.spel\\.SpelExpressionRepr"))
    )

  lazy val ExcludedExtraClasses: List[ClassPredicate] =
    List(
      // In case if there is some public method with flink's TypeInformation on serialization purpose
      ClassPatternPredicate(Pattern.compile("org\\.apache\\.flink\\.api\\.common\\.typeinfo\\.TypeInformation")),
      // java xml api is not easy to use without additional helpers, so we will skip these classes
      ClassPatternPredicate(Pattern.compile("javax\\.xml\\..*")),
      // Not sure why these below exclusions are TODO describe why they should be here or remove it
      ClassPatternPredicate(Pattern.compile("dispatch\\..*")),
      ClassPatternPredicate(Pattern.compile("cats\\..*"))
    )

  lazy val DefaultExcludedMembers: List[ClassMemberPredicate] = CommonExcludedMembers ++ AvroExcludedMembers

  lazy val CommonExcludedMembers: List[ClassMemberPredicate] =
    List(
      // We want to hide all technical methods in every class, toString can be useful so we will leave it
      AllMethodNamesPredicate(classOf[DumpCaseClass], Set(ToStringMethod)),
      // Arrays are not supported for now
      ClassMemberPredicate(ClassPredicate { case _ => true }, {
        case m: Method => m.getReturnType.isArray
        case f: Field => f.getType.isArray
      }),
      ClassMemberPredicate(ClassPredicate { case _ => true }, {
        case m => m.getName.contains("$")
      }),
      ClassMemberPredicate(ClassPredicate { case _ => true }, {
        case m: Member with AccessibleObject => m.getAnnotation(classOf[Hidden]) != null
      }),
      ClassMemberPredicate(ClassPredicate { case cl => classOf[HideToString].isAssignableFrom(cl) }, {
        case m: Method => m.getName == "toString" && m.getParameterCount == 0
      })
    )

  lazy val AvroExcludedMembers: List[ClassMemberPredicate] =
    List(
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("org\\.apache\\.avro\\.generic\\.IndexedRecord")),
        Pattern.compile("(get|getSchema|compareTo|put)")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("org\\.apache\\.avro\\.specific\\.SpecificRecordBase")),
        Pattern.compile("(getConverion|getConversion|writeExternal|readExternal|toByteBuffer|set[A-Z].*)"))
    )

  lazy val DefaultIncludedMembers: List[ClassMemberPredicate] = IncludedUtilsMembers ++ IncludedSerializableMembers ++ IncludedStdMembers

  lazy val IncludedStdMembers: List[ClassMemberPredicate] =
    List(
      // For other std types we don't want to see anything but toString method
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("java\\..*")),
        Pattern.compile(ToStringMethod)),
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("scala\\..*")),
        Pattern.compile(ToStringMethod))
    )

  lazy val IncludedUtilsMembers: List[ClassMemberPredicate] =
    List(
      // For numeric types, strings an collections, date types we want to see all useful methods - we need this explicitly define here because
      // we have another, more general rule: IncludedStdMembers and both predicates are composed
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
        Pattern.compile(s"contains|containsAll|get|getOrDefault|indexOf|isEmpty|size")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("java\\.util\\.Map")),
        Pattern.compile(s"containsKey|containsValue|get|getOrDefault|isEmpty|size|values|keySet")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("java\\.util\\.Optional")),
        Pattern.compile(s"get|isPresent|orElse")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("java\\.util\\.UUID")),
        Pattern.compile(s"clockSequence|getLeastSignificantBits|getMostSignificantBits|node|timestamp|$ToStringMethod|variant|version")),
      ClassMemberPatternPredicate(
        SuperClassPatternPredicate(Pattern.compile("(scala\\.collection\\.Traversable|scala\\.Option)")),
        Pattern.compile(s"apply|applyOrElse|contains|get|getOrDefault|indexOf|isDefined|isEmpty|size|values|keys|diff"))
    )

  lazy val IncludedSerializableMembers: List[ClassMemberPredicate] =
    List(
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("scala\\.xml\\..*")),
        Pattern.compile(ToStringMethod)),
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("(io\\.circe\\..*|argonaut\\..*)")),
        Pattern.compile(s"noSpaces|nospaces|spaces2|spaces4|$ToStringMethod"))
    )

  private case class DumpCaseClass()

}
