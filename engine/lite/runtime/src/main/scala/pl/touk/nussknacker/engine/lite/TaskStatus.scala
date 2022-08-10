package pl.touk.nussknacker.engine.lite

// We need only scenario status in this module - tasks are rather kafka specific. Maybe we should split status task and scenario status?
object TaskStatus extends Enumeration {
  type TaskStatus = Value

  //  Value.id determines the precedence of statuses (i.e. if one of the tasks is Restarting while others are During Deploy, Restarting status should be displayed)
  val Running: Value = Value(0, "RUNNING")
  val DuringDeploy: Value = Value(1, "DURING_DEPLOY")
  val Restarting: Value = Value(2, "RESTARTING")
}
