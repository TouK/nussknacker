package pl.touk.nussknacker.test.utils.domain

import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder

import scala.jdk.CollectionConverters._

object ReflectionBasedUtils {

  def findSubclassesOf[T](clazz: Class[T], packageName: String): List[Class[_ <: T]] = {
    val baseClass = clazz
    val reflections = new Reflections(
      new ConfigurationBuilder().forPackages(baseClass.getPackageName, "pl.touk.nussknacker.ui.api.description")
    )

    reflections
      .getSubTypesOf(baseClass)
      .asScala
      .toList
      .sortBy(_.getName)
  }

}
