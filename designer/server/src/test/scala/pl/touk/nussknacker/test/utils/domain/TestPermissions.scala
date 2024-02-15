package pl.touk.nussknacker.test.utils.domain

import cats.instances.all._
import cats.syntax.semigroup._
import pl.touk.nussknacker.security.Permission

import scala.language.implicitConversions

trait TestPermissions {

  import TestCategories._
  import TestPermissions._

  protected implicit def convertCategoryPermissionPairToCategorizedPermissionsMap(
      pair: (String, Permission.Value)
  ): CategorizedPermission = pair match {
    case (name, permission) => Map(name -> Set(permission))
  }

  val testPermissionEmpty: CategorizedPermission = Map.empty
  val testPermissionDeploy: CategorizedPermission =
    Map(Category1 -> Set(Permission.Deploy))
  val testPermissionRead: CategorizedPermission =
    Map(Category1 -> Set(Permission.Read))
  val testPermissionWrite: CategorizedPermission =
    Map(Category1 -> Set(Permission.Write))
  val testPermissionAll: CategorizedPermission = testPermissionDeploy |+| testPermissionRead |+| testPermissionWrite
}

object TestPermissions {
  type CategorizedPermission = Map[String, Set[Permission.Permission]]
}
