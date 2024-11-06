package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation, Types}
import org.apache.flink.api.java.typeutils.TypeExtractor

import java.lang.reflect.Type
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util

object FlinkTypeInfoRegistrar {

  private val DisableFlinkTypeInfosRegistrationEnvVarName = "NU_DISABLE_FLINK_TYPE_INFOS_REGISTRATION"

  private case class RegistrationEntry[T](klass: Class[T], factoryClass: Class[_ <: TypeInfoFactory[T]])

  private val typeInfosToRegister = List(
    RegistrationEntry(classOf[LocalDate], classOf[LocalDateTypeInfoFactory]),
    RegistrationEntry(classOf[LocalTime], classOf[LocalTimeTypeInfoFactory]),
    RegistrationEntry(classOf[LocalDateTime], classOf[LocalDateTimeTypeInfoFactory]),
  )

  def ensureBaseTypesAreRegistered(): Unit = {
    if (!Option(System.getenv(DisableFlinkTypeInfosRegistrationEnvVarName)).exists(java.lang.Boolean.parseBoolean)) {
      typeInfosToRegister.foreach { entry =>
        register(entry)
      }
    }
  }

  private def register(entry: RegistrationEntry[_]): Unit = {
    val opt = Option(TypeExtractor.getTypeInfoFactory(entry.klass))
    if (opt.isEmpty) {
      TypeExtractor.registerFactory(entry.klass, entry.factoryClass)
    }
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
