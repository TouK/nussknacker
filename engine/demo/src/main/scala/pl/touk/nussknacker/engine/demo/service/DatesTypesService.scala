package pl.touk.nussknacker.engine.demo.service

import java.time.temporal.ChronoUnit
import java.time._

import com.cronutils.model.Cron
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}

import scala.concurrent.Future

class DatesTypesService extends Service with Serializable {
  @MethodToInvoke
  def invoke(@ParamName("MeetingDate") meetingDate: LocalDateTime,
             @ParamName("CreateDate") createDate: LocalDate,
             @ParamName("CreateTime") createTime: LocalTime,
             @ParamName("MeetingDuration")
             @DualEditor(
               simpleEditor = new SimpleEditor(
                 `type` = SimpleEditorType.DURATION_EDITOR,
                 timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS)
               ),
               defaultMode = DualEditorMode.SIMPLE
             )
             meetingDuration: Duration,

             @ParamName("MeetingPeriod")
             @DualEditor(
               simpleEditor = new SimpleEditor(
                 `type` = SimpleEditorType.PERIOD_EDITOR,
                 timeRangeComponents = Array(ChronoUnit.YEARS, ChronoUnit.MONTHS)
               ),
               defaultMode = DualEditorMode.SIMPLE
             )
             meetingPeriod: Period,

             @ParamName("Scheduler")
             @DualEditor(
               simpleEditor = new SimpleEditor (
                  `type` = SimpleEditorType.CRON_EDITOR
               ),
               defaultMode = DualEditorMode.SIMPLE
             )
             cronScheduler: Cron
            ): Future[Unit]
  = Future.successful(Unit)
}
