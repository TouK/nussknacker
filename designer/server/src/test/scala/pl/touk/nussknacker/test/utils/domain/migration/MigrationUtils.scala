package pl.touk.nussknacker.test.utils.domain.migration

import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.MigrateScenarioRequestDto

import scala.reflect.runtime.universe._

object MigrationUtils {

  def allSubclassesContainVersionField[T](
      classes: List[Class[_ <: T]]
  ): List[(String, Boolean)] = {
    classes.map { clazz =>
      val mirror       = runtimeMirror(clazz.getClassLoader)
      val classSymbol  = mirror.classSymbol(clazz)
      val versionField = classSymbol.typeSignature.decls.find(_.name.toString == "version")
      versionField match {
        case Some(_) => (clazz.getName, true)
        case None    => (clazz.getName, false)
      }
    }
  }

}
