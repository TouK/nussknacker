package pl.touk.nussknacker.ui.api.helpers

import cats.syntax.semigroup._
import cats.instances.all._
import pl.touk.nussknacker.ui.security.api.Permission

import scala.language.implicitConversions
trait TestPermissions {
  import TestPermissions._

  protected implicit def convertCategoryPermissoinPairToCategorizedPermissionsMap(pair: (String, Permission.Value)): CategorizedPermission = pair match {
    case (name, permission) => Map(name -> Set(permission))
  }

  val testPermissionEmpty: CategorizedPermission = Map.empty
  val testPermissionDeploy: CategorizedPermission = testCategoryName -> Permission.Deploy
  val testPermissionRead: CategorizedPermission = testCategoryName -> Permission.Read
  val testPermissionWrite: CategorizedPermission = testCategoryName -> Permission.Write
  val testPermissionAdmin: CategorizedPermission = testCategoryName -> Permission.Admin
  val testPermissionAll: CategorizedPermission = testPermissionDeploy |+| testPermissionRead |+| testPermissionWrite |+| testPermissionAdmin

}

object TestPermissions {
  type CategorizedPermission = Map[String, Set[Permission.Permission]]

  val testCategoryName = "TESTCAT"

}
