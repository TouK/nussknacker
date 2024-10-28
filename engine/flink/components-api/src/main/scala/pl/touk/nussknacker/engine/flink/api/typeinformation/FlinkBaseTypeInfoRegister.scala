package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation, Types}
import org.apache.flink.api.java.typeutils.TypeExtractor

import java.lang.reflect.Type
import java.sql.{Date => SqlDate, Time => SqlTime, Timestamp => SqlTimestamp}
import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.util

object FlinkBaseTypeInfoRegister {

  private case class Base[T, K <: TypeInfoFactory[T]](klass: Class[T], factoryClass: Class[K])

  private val baseTypes = List(
    Base(classOf[LocalDate], classOf[LocalDateTypeInfoFactory]),
    Base(classOf[LocalTime], classOf[LocalTimeTypeInfoFactory]),
    Base(classOf[LocalDateTime], classOf[LocalDateTimeTypeInfoFactory]),
    Base(classOf[Instant], classOf[InstantTypeInfoFactory]),
    Base(classOf[SqlDate], classOf[SqlDateTypeInfoFactory]),
    Base(classOf[SqlTime], classOf[SqlTimeTypeInfoFactory]),
    Base(classOf[SqlTimestamp], classOf[SqlTimestampTypeInfoFactory]),
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

  class InstantTypeInfoFactory extends TypeInfoFactory[Instant] {

    override def createTypeInfo(
        t: Type,
        genericParameters: util.Map[String, TypeInformation[_]]
    ): TypeInformation[Instant] =
      Types.INSTANT

  }

  class SqlDateTypeInfoFactory extends TypeInfoFactory[SqlDate] {

    override def createTypeInfo(
        t: Type,
        genericParameters: util.Map[String, TypeInformation[_]]
    ): TypeInformation[SqlDate] =
      Types.SQL_DATE

  }

  class SqlTimeTypeInfoFactory extends TypeInfoFactory[SqlTime] {

    override def createTypeInfo(
        t: Type,
        genericParameters: util.Map[String, TypeInformation[_]]
    ): TypeInformation[SqlTime] =
      Types.SQL_TIME

  }

  class SqlTimestampTypeInfoFactory extends TypeInfoFactory[SqlTimestamp] {

    override def createTypeInfo(
        t: Type,
        genericParameters: util.Map[String, TypeInformation[_]]
    ): TypeInformation[SqlTimestamp] =
      Types.SQL_TIMESTAMP

  }

}
