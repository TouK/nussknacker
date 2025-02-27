package pl.touk.nussknacker.engine.management.sample.service

import com.cronutils.model.Cron
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period, ZonedDateTime}
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class DatesTypesService extends Service with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("dateTimeParam") dateTimeParam: LocalDateTime,
      @ParamName("dateParam") dateParam: LocalDate,
      @ParamName("timeParam") timeParam: LocalTime,
      @ParamName("zonedDataTimeParam") zonedDataTimeParam: ZonedDateTime,
      @ParamName("durationParam")
      @DualEditor(
        simpleEditor = new SimpleEditor(
          `type` = SimpleEditorType.DURATION_EDITOR,
          timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS)
        ),
        defaultMode = DualEditorMode.SIMPLE
      )
      duration: Duration,
      @ParamName("periodParam")
      @DualEditor(
        simpleEditor = new SimpleEditor(
          `type` = SimpleEditorType.PERIOD_EDITOR,
          timeRangeComponents = Array(ChronoUnit.YEARS, ChronoUnit.MONTHS)
        ),
        defaultMode = DualEditorMode.SIMPLE
      )
      period: Period,
      @ParamName("cronScheduleParam")
      @SimpleEditor(`type` = SimpleEditorType.CRON_EDITOR)
      cronScheduleParam: Cron
  ): Future[Unit] = {
    ???
  }

}
