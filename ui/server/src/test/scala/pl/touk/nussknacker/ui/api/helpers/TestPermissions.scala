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
  val testPermissionDeploy: CategorizedPermission = Map(testCategoryName -> Set(Permission.Deploy), secondTestCategoryName -> Set(Permission.Deploy))
  val testPermissionRead: CategorizedPermission = Map(testCategoryName -> Set(Permission.Read), secondTestCategoryName -> Set(Permission.Read))
  val testPermissionWrite: CategorizedPermission = Map(testCategoryName -> Set(Permission.Write), secondTestCategoryName -> Set(Permission.Write))
  val testPermissionAll: CategorizedPermission = testPermissionDeploy |+| testPermissionRead |+| testPermissionWrite
}

object TestPermissions {
  type CategorizedPermission = Map[String, Set[Permission.Permission]]

  val testCategoryName = "TESTCAT"

  val secondTestCategoryName = "TESTCAT2"
}
