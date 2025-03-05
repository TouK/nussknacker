package pl.touk.nussknacker.test.utils.domain

import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder

import scala.jdk.CollectionConverters._
import scala.reflect.{classTag, ClassTag}

object ReflectionBasedUtils {

  def findSubclassesOf[T: ClassTag](packageName: String): List[Class[_ <: T]] = {
    val baseClass = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    val reflections = new Reflections(
      new ConfigurationBuilder().forPackages(baseClass.getPackageName, packageName)
    )

    reflections
      .getSubTypesOf(baseClass)
      .asScala
      .toList
      .sortBy(_.getName)
  }

}
