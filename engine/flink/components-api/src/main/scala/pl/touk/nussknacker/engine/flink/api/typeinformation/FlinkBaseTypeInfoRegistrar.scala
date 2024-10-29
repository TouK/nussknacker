package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation, Types}
import org.apache.flink.api.java.typeutils.TypeExtractor

import java.lang.reflect.Type
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util

object FlinkBaseTypeInfoRegistrar {

  private case class RegistrationEntry[T, K <: TypeInfoFactory[T]](klass: Class[T], factoryClass: Class[K])

  private val baseTypes = List(
    RegistrationEntry(classOf[LocalDate], classOf[LocalDateTypeInfoFactory]),
    RegistrationEntry(classOf[LocalTime], classOf[LocalTimeTypeInfoFactory]),
    RegistrationEntry(classOf[LocalDateTime], classOf[LocalDateTimeTypeInfoFactory]),
  )

  def ensureBaseTypesAreRegistered(): Unit =
    baseTypes.foreach { base =>
      register(base)
    }

  private def register(entry: RegistrationEntry[_, _ <: TypeInfoFactory[_]]): Unit = {
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
