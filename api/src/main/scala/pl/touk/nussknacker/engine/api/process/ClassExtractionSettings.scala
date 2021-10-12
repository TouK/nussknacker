package pl.touk.nussknacker.engine.api.process

import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.typed.supertype.ReturningSingleClassPromotionStrategy
import pl.touk.nussknacker.engine.api.{Hidden, HideToString}

import java.lang.reflect.{AccessibleObject, Field, Member, Method}
import java.text.NumberFormat
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime, ChronoZonedDateTime}
import java.time.temporal.ChronoUnit
import java.util
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.{Calendar, Date, Optional, UUID}

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

  lazy val ExcludedStdClasses: List[ClassPredicate] = ExcludedVoidClasses ++ ExcludedCollectionFunctionalClasses ++ ExcludedTimeClasses ++ 
    List(
      // In case if someone use it for kind of meta programming
      ExactClassPredicate[Class[_]],

      //we want only boxed types
      ClassPredicate { case cl => cl.isPrimitive },

      ExactClassPredicate[ReturningSingleClassPromotionStrategy],
      // We use this type only programmable
      ClassPatternPredicate(Pattern.compile("pl\\.touk\\.nussknacker\\.engine\\.spel\\.SpelExpressionRepr")),
    )

  lazy val ExcludedCollectionFunctionalClasses = List(
    // In case if someone return function for lazy evaluation purpose
    BasePackagePredicate("java.util.function"),
    ClassPatternPredicate(Pattern.compile("scala\\.Function.*")),
    BasePackagePredicate("java.util.stream"),
    //Scala collections are cumbersome to use with Spel
    BasePackagePredicate("scala.collection"),
    ClassPatternPredicate(Pattern.compile("scala\\.Tuple.*")),
    ExactClassPredicate[Option[_]],
  )

  lazy val ExcludedVoidClasses = List(
    // Void types
    ClassPatternPredicate(Pattern.compile("void")),
    ClassPatternPredicate(Pattern.compile("java\\.lang\\.Void")),
    ClassPatternPredicate(Pattern.compile("scala\\.Unit.*")),
    ClassPatternPredicate(Pattern.compile("scala\\.runtime\\.BoxedUnit"))
  )

  lazy val ExcludedTimeClasses = List(
    //we want to have Chrono*Time classes, as many parameters/return types of Local/ZonedDate(Time) use them
    ExceptOfClassesPredicate(BasePackagePredicate("java.time.chrono"),
      ExactClassPredicate(classOf[ChronoLocalDateTime[_]], classOf[ChronoLocalDate], classOf[ChronoZonedDateTime[_]])),
    ExceptOfClassesPredicate(BasePackagePredicate("java.time.temporal"), ExactClassPredicate(classOf[ChronoUnit])),
    BasePackagePredicate("java.time.zone"),
  )

  lazy val ExcludedExtraClasses: List[ClassPredicate] =
    List(
      // In case if there is some public method with flink's TypeInformation on serialization purpose
      BasePackagePredicate("org.apache.flink.api.common.typeinfo"),
      // java xml api is not easy to use without additional helpers, so we will skip these classes
      BasePackagePredicate("javax.xml"),
      // Not sure why these below exclusions are TODO describe why they should be here or remove it
      BasePackagePredicate("dispatch"),
      BasePackagePredicate("cats"),
      ExactClassPredicate(classOf[Decoder[_]], classOf[Encoder[_]]),
      //If cronutils are included to use Cron editor, we don't want quite a lot of unnecessary classes
      BasePackagePredicate("com.cronutils.model.field")
    )

  lazy val DefaultExcludedMembers: List[ClassMemberPredicate] = CommonExcludedMembers ++ AvroExcludedMembers :+ ReturnMemberPredicate(SuperClassPredicate(
    ExactClassPredicate(classOf[Decoder[_]], classOf[Encoder[_]]))) :+ ReturnMemberPredicate(SuperClassPredicate(ExactClassPredicate[ParameterEditor]))

  lazy val CommonExcludedMembers: List[ClassMemberPredicate] =
    List(
      // We want to hide all technical methods in every class, toString can be useful so we will leave it
      AllMethodNamesPredicate(classOf[DumpCaseClass], Set(ToStringMethod)),
      ClassMemberPredicate(ClassPredicate { case _ => true }, {
        case m => m.getName.contains("$")
      }),
      ClassMemberPredicate(ClassPredicate { case _ => true }, {
        case m: Member with AccessibleObject => m.getAnnotation(classOf[Hidden]) != null
      }),
      ClassMemberPredicate(ClassPredicate { case cl => classOf[HideToString].isAssignableFrom(cl) }, {
        case m: Method => m.getName == "toString" && m.getParameterCount == 0
      }),
      ClassMemberPredicate(ClassPredicate { case cl => cl.isEnum }, {
        case m: Method => List("declaringClass", "getDeclaringClass").contains(m.getName)
      })
    )

  lazy val AvroExcludedMembers: List[ClassMemberPredicate] =
    List(
      ClassMemberPatternPredicate(
        SuperClassPredicate(ClassPatternPredicate(Pattern.compile("org\\.apache\\.avro\\.generic\\.IndexedRecord"))),
        Pattern.compile("(get|getSchema|compareTo|put)")),
      ClassMemberPatternPredicate(
        SuperClassPredicate(ClassPatternPredicate(Pattern.compile("org\\.apache\\.avro\\.specific\\.SpecificRecordBase"))),
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
        SuperClassPredicate(ExactClassPredicate(classOf[java.lang.Boolean], classOf[Number], classOf[Date], classOf[Calendar], classOf[TimeUnit])),
        Pattern.compile(".*")),
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("java\\.time\\..*")),
        Pattern.compile(".*")),
      ClassMemberPatternPredicate(
        ClassPatternPredicate(Pattern.compile("scala\\.concurrent\\.duration\\..*")),
        Pattern.compile(".*")),
      ClassMemberPatternPredicate(
        SuperClassPredicate(ExactClassPredicate[CharSequence]),
        Pattern.compile(s"charAt|compareTo.*|concat|contains|endsWith|equalsIgnoreCase|format|indexOf|isBlank|isEmpty|join|lastIndexOf|length|matches|" +
          s"replaceAll|replaceFirst|split|startsWith|strip.*|substring|toLowerCase|toUpperCase|trim|$ToStringMethod")),
      ClassMemberPatternPredicate(
        SuperClassPredicate(ExactClassPredicate[NumberFormat]),
        Pattern.compile(s"get.*Instance|format|parse")),
      ClassMemberPatternPredicate(
        SuperClassPredicate(ExactClassPredicate[util.Collection[_]]),
        Pattern.compile(s"contains|containsAll|get|getOrDefault|indexOf|isEmpty|size")),
      ClassMemberPatternPredicate(
        SuperClassPredicate(ExactClassPredicate[util.Map[_, _]]),
        Pattern.compile(s"containsKey|containsValue|get|getOrDefault|isEmpty|size|values|keySet")),
      ClassMemberPatternPredicate(
        SuperClassPredicate(ExactClassPredicate[Optional[_]]),
        Pattern.compile(s"get|isPresent|orElse")),
      ClassMemberPatternPredicate(
        SuperClassPredicate(ExactClassPredicate[UUID]),
        Pattern.compile(s"clockSequence|getLeastSignificantBits|getMostSignificantBits|node|timestamp|$ToStringMethod|variant|version")),
      ClassMemberPatternPredicate(
        SuperClassPredicate(ExactClassPredicate(classOf[Traversable[_]], classOf[Option[_]])),
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
