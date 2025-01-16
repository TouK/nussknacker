package pl.touk.nussknacker.engine.api.deployment.scheduler.model

sealed trait ScheduleProperty

object ScheduleProperty {
  sealed trait SingleScheduleProperty extends ScheduleProperty

  final case class MultipleScheduleProperty(
      schedules: Map[String, SingleScheduleProperty]
  ) extends ScheduleProperty

  final case class CronScheduleProperty(
      labelOrCronExpr: String
  ) extends SingleScheduleProperty

}
