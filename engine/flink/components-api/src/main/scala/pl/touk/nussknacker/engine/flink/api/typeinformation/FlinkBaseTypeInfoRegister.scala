package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation, Types}
import org.apache.flink.api.java.typeutils.TypeExtractor

import java.lang.reflect.Type
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util

object FlinkBaseTypeInfoRegister {

  private[typeinformation] case class Base[T, K <: TypeInfoFactory[T]](klass: Class[T], factoryClass: Class[K])

  private[typeinformation] val baseTypes = List(
    Base(classOf[LocalDate], classOf[LocalDateTypeInfoFactory]),
    Base(classOf[LocalTime], classOf[LocalTimeTypeInfoFactory]),
    Base(classOf[LocalDateTime], classOf[LocalDateTimeTypeInfoFactory]),
  )

  def makeSureBaseTypesAreRegistered(): Unit =
    baseTypes.foreach { base =>
      register(base)
    }

  private def register(base: Base[_, _ <: TypeInfoFactory[_]]): Unit = {
    val opt = Option(TypeExtractor.getTypeInfoFactory(base.klass))
    if (opt.isEmpty) {
      TypeExtractor.registerFactory(base.klass, base.factoryClass)
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
