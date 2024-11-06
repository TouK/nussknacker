package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation, Types}
import org.apache.flink.api.java.typeutils.TypeExtractor

import java.lang.reflect.Type
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util
import java.util.concurrent.atomic.AtomicBoolean

// This class contains registers TypeInfoFactory for commonly used classes in Nussknacker.
// It is a singleton as Flink's only contains a global registry for such purpose
object FlinkTypeInfoRegistrar {

  private val typeInfoRegistrationEnabled = new AtomicBoolean(true)

  private val DisableFlinkTypeInfoRegistrationEnvVarName = "NU_DISABLE_FLINK_TYPE_INFO_REGISTRATION"

  private case class RegistrationEntry[T](klass: Class[T], factoryClass: Class[_ <: TypeInfoFactory[T]])

  private val typeInfoToRegister = List(
    RegistrationEntry(classOf[LocalDate], classOf[LocalDateTypeInfoFactory]),
    RegistrationEntry(classOf[LocalTime], classOf[LocalTimeTypeInfoFactory]),
    RegistrationEntry(classOf[LocalDateTime], classOf[LocalDateTimeTypeInfoFactory]),
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
    val opt = Option(TypeExtractor.getTypeInfoFactory(entry.klass))
    if (opt.isEmpty) {
      TypeExtractor.registerFactory(entry.klass, entry.factoryClass)
    }
  }

  // These methods are mainly for purpose of tests in nussknacker-flink-compatibility project
  // It should be used in caution as it changes the global state
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
