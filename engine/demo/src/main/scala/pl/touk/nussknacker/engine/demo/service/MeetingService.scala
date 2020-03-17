package pl.touk.nussknacker.engine.demo.service

import java.time.temporal.ChronoUnit
import java.time._

import com.cronutils.model.Cron
import javax.annotation.Nullable
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}

import scala.concurrent.Future

class MeetingService extends Service with Serializable {
  @MethodToInvoke
  def invoke(@ParamName("Date") date: LocalDateTime,
             @ParamName("EndTime") endTime: LocalTime,
             @ParamName("Duration")
             @DualEditor(
               simpleEditor = new SimpleEditor(
                 `type` = SimpleEditorType.DURATION_EDITOR,
                 timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS)
               ),
               defaultMode = DualEditorMode.SIMPLE
             )
             duration: Duration,

             @ParamName("Period")
             @DualEditor(
               simpleEditor = new SimpleEditor(
                 `type` = SimpleEditorType.PERIOD_EDITOR,
                 timeRangeComponents = Array(ChronoUnit.YEARS, ChronoUnit.MONTHS)
               ),
               defaultMode = DualEditorMode.SIMPLE
             )
             period: Period,

             @ParamName("NextMeeting")
             @Nullable
             nextMeeting: LocalDate,

             @ParamName("Scheduler")
             @DualEditor(
               simpleEditor = new SimpleEditor (
                  `type` = SimpleEditorType.CRON_EDITOR
               ),
               defaultMode = DualEditorMode.SIMPLE
             )
             @Nullable
             cronScheduler: Cron
            ): Future[Unit]
  = Future.successful(Unit)
}
