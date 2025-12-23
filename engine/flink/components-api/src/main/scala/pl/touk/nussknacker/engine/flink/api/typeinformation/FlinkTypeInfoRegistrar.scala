package pl.touk.nussknacker.engine.flink.api.typeinformation

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation, Types}
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.typeutils.runtime.kryo.ChillSerializerRegistrar

import java.lang.reflect.Type
import java.time._
import java.util
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

  def ensureTypeInfosAreRegistered(): Unit = {
    register(typeInfoToRegister)
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

}
