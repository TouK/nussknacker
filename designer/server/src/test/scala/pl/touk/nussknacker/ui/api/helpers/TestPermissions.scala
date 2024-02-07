package pl.touk.nussknacker.ui.api.helpers

import cats.instances.all._
import cats.syntax.semigroup._
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.helpers.TestData.Categories.TestCategory
import pl.touk.nussknacker.ui.api.helpers.TestData.Categories.TestCategory.Category1

import scala.language.implicitConversions

trait TestPermissions {

  import TestPermissions._

  protected implicit def convertCategoryPermissionPairToCategorizedPermissionsMap(
      pair: (TestCategory, Permission.Value)
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
  type CategorizedPermission = Map[TestCategory, Set[Permission.Permission]]
}
