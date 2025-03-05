package pl.touk.nussknacker.engine.management.sample.service

import com.cronutils.model.Cron
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.util.service.TimeMeasuringService

import java.time._
import java.time.temporal.ChronoUnit
import javax.annotation.Nullable
import scala.concurrent.{ExecutionContext, Future}

object MeetingService extends Service with Serializable with TimeMeasuringService {

  override protected def serviceName: String = "meetingService"

  @MethodToInvoke
  def invoke(
      @ParamName("Date") date: LocalDateTime,
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
        simpleEditor = new SimpleEditor(
          `type` = SimpleEditorType.CRON_EDITOR
        ),
        defaultMode = DualEditorMode.SIMPLE
      )
      @Nullable
      cronScheduler: Cron
  )(implicit ec: ExecutionContext): Future[Unit] = measuring {
    Thread.sleep((math.random() * 10).toLong)
    if (math.random() < 0.25) {
      Future.failed(new IllegalArgumentException("Bad luck, your meeting failed..."))
    } else {
      Future.successful(())
    }
  }

}
