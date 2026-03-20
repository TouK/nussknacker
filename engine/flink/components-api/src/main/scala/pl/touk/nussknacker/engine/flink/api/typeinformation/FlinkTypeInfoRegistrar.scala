package pl.touk.nussknacker.engine.flink.api.typeinformation

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.serialization.SerializerConfig
import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation, Types}
import org.apache.flink.api.java.typeutils.{GenericTypeInfo, TypeExtractor}
import org.apache.flink.api.java.typeutils.runtime.kryo.ChillSerializerRegistrar

import java.lang.reflect.Type
import java.nio.charset.Charset
import java.time._
import java.util
import java.util.{Currency, Locale, UUID}
import scala.util.Try

/**
 * This class registers TypeInfoFactory for commonly used classes in Nussknacker.
 * It is a singleton as Flink uses a global registry for such purpose.
 */
//noinspection ScalaWeakerAccess
object FlinkTypeInfoRegistrar extends LazyLogging {

  private val FLINK_1_CHILL_PACKAGE_REGISTRAR =
    "org.apache.flink.api.java.typeutils.runtime.kryo.FlinkChillPackageRegistrar"
  private val FLINK_2_CHILL_PACKAGE_REGISTRAR = "org.apache.flink.streaming.util.serialize.FlinkChillPackageRegistrar"

  private case class RegistrationEntry[T](klass: Class[T], factoryClass: Class[_ <: TypeInfoFactory[T]])

  private val typeInfoToRegister = List(
    RegistrationEntry(classOf[LocalDate], classOf[LocalDateTypeInfoFactory]),
    RegistrationEntry(classOf[LocalTime], classOf[LocalTimeTypeInfoFactory]),
    RegistrationEntry(classOf[LocalDateTime], classOf[LocalDateTimeTypeInfoFactory]),
  )

  /**
   * Additional [[org.apache.flink.api.common.typeinfo.TypeInformation]] for Flink classes.
   *
   * These should be implemented as proper `TypeInformation` subclasses, but we can't use non-core
   * serializers when interacting with Table API's AdaptiveJoins. This also means that we can't cache
   * created `GenericTypeInfo` instances in a static variable.
   *
   * See <a href="https://issues.apache.org/jira/browse/FLINK-39150">FLINK-39150</a> for more details.
   */
  private val kryoTypesToRegister = List(
    RegistrationEntry(classOf[Charset], classOf[CharsetTypeInfoFactory]),
    RegistrationEntry(classOf[Currency], classOf[CurrencyTypeInfoFactory]),
    RegistrationEntry(classOf[Duration], classOf[DurationTypeInfoFactory]),
    RegistrationEntry(classOf[Locale], classOf[LocaleTypeInfoFactory]),
    RegistrationEntry(classOf[OffsetDateTime], classOf[OffsetDateTimeTypeInfoFactory]),
    RegistrationEntry(classOf[Period], classOf[PeriodTypeInfoFactory]),
    RegistrationEntry(classOf[UUID], classOf[UUIDTypeInfoFactory]),
    RegistrationEntry(classOf[ZonedDateTime], classOf[ZonedDateTimeTypeInfoFactory]),
    RegistrationEntry(classOf[ZoneId], classOf[ZoneIdTypeInfoFactory]),
  )

  def ensureTypeInfosAreRegistered(): Unit = {
    register(typeInfoToRegister)
  }

  /**
   * Registers some common types as known to Flink's type system.
   *
   * This should be implemented with proper [[org.apache.flink.api.common.typeutils.TypeSerializer]]s
   * but that would break these types when used with Flink's SQL joins
   * - see <a href="https://issues.apache.org/jira/browse/FLINK-39150">FLINK-39150</a> for details.
   */
  def ensureKryoTypesAreRegistered(serializerConfig: SerializerConfig): Unit = {
    // register types so that their ids in global Kryo serializer are stable
    kryoTypesToRegister.foreach { entry => serializerConfig.getRegisteredKryoTypes.add(entry.klass) }
    // add explicit entries in TypeExtractor to silence type extraction warnings about unknown types
    register(kryoTypesToRegister)
  }

  def validateKryoTypeRegistrations(): Unit = {
    val chillSerializerRegistrar = Try(Class.forName(FLINK_2_CHILL_PACKAGE_REGISTRAR))
      .recover { case _: ClassNotFoundException => Class.forName(FLINK_1_CHILL_PACKAGE_REGISTRAR) }
      .get
      .getDeclaredConstructor()
      .newInstance()
      .asInstanceOf[ChillSerializerRegistrar]
    if (chillSerializerRegistrar.getNextRegistrationId == 85) {
      if (!classExists("pl.touk.nussknacker.FlinkScalaBuildInfo")) {
        logger.warn("flink-scala.jar is missing from classpath - serialization of some Scala types may be broken")
      } else {
        logger.warn("flink-scala.jar is loaded after Flink classes - serialization of some Scala types may be broken")
      }
    } else {
      val oldFlinkScalaLoaded = classExists("org.apache.flink.runtime.types.UnmodifiableJavaCollectionsRegistrar")
      if (oldFlinkScalaLoaded) {
        logger.warn("found old flink-scala.jar - serialization of some Scala types may be broken")
      }
    }
  }

  private def classExists(className: String): Boolean = {
    try {
      Class.forName(className)
      true
    } catch {
      case _: ClassNotFoundException => false
    }
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

  // format: off
  class CharsetTypeInfoFactory extends TypeInfoFactory[Charset] {
    override def createTypeInfo(t: Type, genericParameters: util.Map[String, TypeInformation[_]]): TypeInformation[Charset] =
      new GenericTypeInfo(classOf[Charset])
  }
  class CurrencyTypeInfoFactory extends TypeInfoFactory[Currency] {
    override def createTypeInfo(t: Type, genericParameters: util.Map[String, TypeInformation[_]]): TypeInformation[Currency] =
      new GenericTypeInfo(classOf[Currency])
  }
  class DurationTypeInfoFactory extends TypeInfoFactory[Duration] {
    override def createTypeInfo(t: Type, genericParameters: util.Map[String, TypeInformation[_]]): TypeInformation[Duration] =
      new GenericTypeInfo(classOf[Duration])
  }
  class LocaleTypeInfoFactory extends TypeInfoFactory[Locale] {
    override def createTypeInfo(t: Type, genericParameters: util.Map[String, TypeInformation[_]]): TypeInformation[Locale] =
      new GenericTypeInfo(classOf[Locale])
  }
  class OffsetDateTimeTypeInfoFactory extends TypeInfoFactory[OffsetDateTime] {
    override def createTypeInfo(t: Type, genericParameters: util.Map[String, TypeInformation[_]]): TypeInformation[OffsetDateTime] =
      new GenericTypeInfo(classOf[OffsetDateTime])
  }
  class PeriodTypeInfoFactory extends TypeInfoFactory[Period] {
    override def createTypeInfo(t: Type, genericParameters: util.Map[String, TypeInformation[_]]): TypeInformation[Period] =
      new GenericTypeInfo(classOf[Period])
  }
  class UUIDTypeInfoFactory extends TypeInfoFactory[UUID] {
    override def createTypeInfo(t: Type, genericParameters: util.Map[String, TypeInformation[_]]): TypeInformation[UUID] =
      new GenericTypeInfo(classOf[UUID])
  }
  class ZonedDateTimeTypeInfoFactory extends TypeInfoFactory[ZonedDateTime] {
    override def createTypeInfo(t: Type, genericParameters: util.Map[String, TypeInformation[_]]): TypeInformation[ZonedDateTime] =
      new GenericTypeInfo(classOf[ZonedDateTime])
  }
  class ZoneIdTypeInfoFactory extends TypeInfoFactory[ZoneId] {
    override def createTypeInfo(t: Type, genericParameters: util.Map[String, TypeInformation[_]]): TypeInformation[ZoneId] =
      new GenericTypeInfo(classOf[ZoneId])
  }
  // format: on

}
