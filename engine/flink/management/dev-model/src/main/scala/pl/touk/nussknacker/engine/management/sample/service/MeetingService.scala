package pl.touk.nussknacker.engine.management.sample.service

import com.cronutils.model.Cron
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{ParameterEditor, ParameterEditorType, SpelEditor}
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
      @ParameterEditor(
        `type` = ParameterEditorType.DURATION_EDITOR,
        timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS)
      )
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      duration: Duration,
      @ParamName("Period")
      @ParameterEditor(
        `type` = ParameterEditorType.PERIOD_EDITOR,
        timeRangeComponents = Array(ChronoUnit.YEARS, ChronoUnit.MONTHS)
      )
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
      period: Period,
      @ParamName("NextMeeting")
      @Nullable
      nextMeeting: LocalDate,
      @ParamName("Scheduler")
      @ParameterEditor(`type` = ParameterEditorType.CRON_EDITOR)
      @ParameterEditor(`type` = ParameterEditorType.SPEL_EDITOR)
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
