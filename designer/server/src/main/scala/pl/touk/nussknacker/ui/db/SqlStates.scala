package pl.touk.nussknacker.ui.db

// This is a copy of necessary sql states based od PSQLState class but without binding to psql classes
object SqlStates {

  val UniqueViolation      = "23505"
  val SerializationFailure = "40001"

}
