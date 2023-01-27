package pl.touk.nussknacker.ui.api.helpers

import cats.syntax.semigroup._
import cats.instances.all._
import pl.touk.nussknacker.security.Permission

import scala.language.implicitConversions

trait TestPermissions {

  import TestPermissions._
  import TestCategories._

  protected implicit def convertCategoryPermissionPairToCategorizedPermissionsMap(pair: (String, Permission.Value)): CategorizedPermission = pair match {
    case (name, permission) => Map(name -> Set(permission))
  }

  val testPermissionEmpty: CategorizedPermission = Map.empty
  val testPermissionDeploy: CategorizedPermission = Map(TestCat -> Set(Permission.Deploy), TestCat2 -> Set(Permission.Deploy))
  val testPermissionRead: CategorizedPermission = Map(TestCat -> Set(Permission.Read), TestCat2 -> Set(Permission.Read))
  val testPermissionWrite: CategorizedPermission = Map(TestCat -> Set(Permission.Write), TestCat2 -> Set(Permission.Write))
  val testPermissionAll: CategorizedPermission = testPermissionDeploy |+| testPermissionRead |+| testPermissionWrite

  val allCategoriesWithReadPermissions: CategorizedPermission = AllCategories.map(_ -> Set(Permission.Read)).toMap
  val userCategoryWithReadPermissions: CategorizedPermission = Map(UserCategory -> Set(Permission.Read))
}

object TestPermissions {
  type CategorizedPermission = Map[String, Set[Permission.Permission]]
}
