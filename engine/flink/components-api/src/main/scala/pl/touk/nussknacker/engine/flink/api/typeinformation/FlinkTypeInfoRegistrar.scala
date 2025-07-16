package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation, Types}
import org.apache.flink.api.java.typeutils.TypeExtractor

import java.lang.reflect.Type
import java.nio.charset.Charset
import java.time._
import java.util
import java.util.{Currency, Locale, UUID}
import java.util.concurrent.atomic.AtomicBoolean

// This class contains registers TypeInfoFactory for commonly used classes in Nussknacker.
// It is a singleton as Flink's only contains a global registry for such purpose
object FlinkTypeInfoRegistrar {

  private val typeInfoRegistrationEnabled = new AtomicBoolean(true)

  private val DisableFlinkTypeInfoRegistrationEnvVarName = "NU_DISABLE_FLINK_TYPE_INFO_REGISTRATION"

  // These members are package protected for purpose of TypingResultAwareTypeInformationDetection.FlinkBelow119AdditionalTypeInfo - see comment there
  private[engine] case class RegistrationEntry[T](klass: Class[T], factoryClass: Class[_ <: TypeInfoFactory[T]])

  private[engine] val typeInfoToRegister = List(
    // LocalDate/Time types are provided by Flink but not registered by default
    RegistrationEntry(classOf[LocalDate], classOf[LocalDateTypeInfoFactory]),
    RegistrationEntry(classOf[LocalTime], classOf[LocalTimeTypeInfoFactory]),
    RegistrationEntry(classOf[LocalDateTime], classOf[LocalDateTimeTypeInfoFactory]),
    // Below are types that are provided by Nussknacker itself - from Flink Table API perspective they are custom type info
    RegistrationEntry(classOf[OffsetDateTime], classOf[OffsetDateTimeTypeInfoFactory]),
    RegistrationEntry(classOf[ZonedDateTime], classOf[ZonedDateTimeTypeInfoFactory]),
    RegistrationEntry(classOf[Duration], classOf[DurationTypeInfoFactory]),
    RegistrationEntry(classOf[Period], classOf[PeriodTypeInfoFactory]),
    RegistrationEntry(classOf[ZoneId], classOf[ZoneIdTypeInfoFactory]),
    RegistrationEntry(classOf[Charset], classOf[CharsetTypeInfoFactory]),
    RegistrationEntry(classOf[Currency], classOf[CurrencyTypeInfoFactory]),
    RegistrationEntry(classOf[Locale], classOf[LocaleTypeInfoFactory]),
    RegistrationEntry(classOf[UUID], classOf[UUIDTypeInfoFactory]),
  )

  def ensureTypeInfosAreRegistered(): Unit = {
    // TypeInfo registration is available in Flink >= 1.19. For backward compatibility purpose we allow
    // to disable this by either environment variable or programmatically
    if (typeInfoRegistrationEnabled.get() && !typeInfoRegistrationDisabledByEnvVariable) {
      typeInfoToRegister.foreach { entry =>
        register(entry)
      }
    }
  }

  private def typeInfoRegistrationDisabledByEnvVariable = {
    Option(System.getenv(DisableFlinkTypeInfoRegistrationEnvVarName)).exists(_.toBoolean)
  }

  private def register(entry: RegistrationEntry[_]): Unit = {
    // TypeExtractor is not thread safe, and we may arrive here as a result of concurrent initialization
    // of multiple Flink deployment managers
    classOf[TypeExtractor].synchronized {
      if (TypeExtractor.getTypeInfoFactory(entry.klass) == null) {
        TypeExtractor.registerFactory(entry.klass, entry.factoryClass)
      }
    }
  }

  // These methods are mainly for purpose of tests in nussknacker-flink-compatibility project
  // It should be used with caution as it changes the global state. They will be removed when we stop supporting Flink < 1.19
  def isFlinkTypeInfoRegistrationEnabled: Boolean = typeInfoRegistrationEnabled.get()

  def enableFlinkTypeInfoRegistration(): Unit = {
    typeInfoRegistrationEnabled.set(true)
  }

  def disableFlinkTypeInfoRegistration(): Unit = {
    typeInfoRegistrationEnabled.set(false)
  }

  class LocalDateTypeInfoFactory extends TypeInfoFactory[LocalDate] {

    override def createTypeInfo(
        t: Type,
        genericParameters: util.Map[String, TypeInformation[_]]
    ): TypeInformation[LocalDate] =
      Types.LOCAL_DATE

  }

  class LocalTimeTypeInfoFactory extends TypeInfoFactory[LocalTime] {

    override def createTypeInfo(
        t: Type,
        genericParameters: util.Map[String, TypeInformation[_]]
    ): TypeInformation[LocalTime] =
      Types.LOCAL_TIME

  }

  class LocalDateTimeTypeInfoFactory extends TypeInfoFactory[LocalDateTime] {

    override def createTypeInfo(
        t: Type,
        genericParameters: util.Map[String, TypeInformation[_]]
    ): TypeInformation[LocalDateTime] =
      Types.LOCAL_DATE_TIME

  }

}
