package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation, Types}
import org.apache.flink.api.java.typeutils.TypeExtractor

import java.lang.reflect.Type
import java.time._
import java.util

// This class contains registers TypeInfoFactory for commonly used classes in Nussknacker.
// It is a singleton as Flink's only contains a global registry for such purpose
object FlinkTypeInfoRegistrar {

  private case class RegistrationEntry[T](klass: Class[T], factoryClass: Class[_ <: TypeInfoFactory[T]])

  private val typeInfoToRegister = List(
    RegistrationEntry(classOf[LocalDate], classOf[LocalDateTypeInfoFactory]),
    RegistrationEntry(classOf[LocalTime], classOf[LocalTimeTypeInfoFactory]),
    RegistrationEntry(classOf[LocalDateTime], classOf[LocalDateTimeTypeInfoFactory]),
  )

  def ensureTypeInfosAreRegistered(): Unit = {
    register(typeInfoToRegister)
  }

  private def register(entries: List[RegistrationEntry[_]]): Unit = {
    // TypeExtractor is not thread safe, and we may arrive here as a result of concurrent initialization
    // of multiple Flink deployment managers
    classOf[TypeExtractor].synchronized {
      entries.foreach { entry =>
        if (TypeExtractor.getTypeInfoFactory(entry.klass) == null) {
          TypeExtractor.registerFactory(entry.klass, entry.factoryClass)
        }
      }
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
