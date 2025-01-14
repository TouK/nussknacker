package pl.touk.nussknacker.engine.api.deployment.periodic.model

sealed trait PeriodicScheduleProperty

sealed trait SinglePeriodicScheduleProperty extends PeriodicScheduleProperty

final case class MultiplePeriodicScheduleProperty(
    schedules: Map[String, SinglePeriodicScheduleProperty]
) extends PeriodicScheduleProperty

final case class CronPeriodicScheduleProperty(
    labelOrCronExpr: String
) extends SinglePeriodicScheduleProperty
