package pl.touk.nussknacker.ui.security

object GlobalPermission extends Enumeration {
  type GlobalPermission = Value
  val AdminTab = Value("AdminTab")

  final val ALL_PERMISSIONS = Set(AdminTab)
}
