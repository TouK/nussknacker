package pl.touk.nussknacker.engine.management.periodic

import java.time.{Clock, Instant, LocalDateTime, ZoneId, ZonedDateTime}
import cats.Alternative.ops.toAllAlternativeOps
import com.cronutils.model.{Cron, CronType}
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

import scala.util.Try

object ScheduleProperty {
  implicit val configuration: Configuration = Configuration.default.withDefaults.withDiscriminator("type")
}

@ConfiguredJsonCodec sealed trait ScheduleProperty

object SingleScheduleProperty {
  implicit val configuration: Configuration = Configuration.default.withDefaults
}

@ConfiguredJsonCodec sealed trait SingleScheduleProperty extends ScheduleProperty {

  /**
    * If Left is returned it means periodic property is invalid, e.g. it cannot be parsed.
    * If Right(None) is returned it means process should not be run in future anymore e.g. was specified to run once.
    * Right(Some(date)) specifies date when process should start.
    */
  def nextRunAt(clock: Clock): Either[String, Option[LocalDateTime]]
}

@JsonCodec case class MultipleScheduleProperty(schedules: Map[String, SingleScheduleProperty]) extends ScheduleProperty


@JsonCodec case class CronScheduleProperty(labelOrCronExpr: String) extends SingleScheduleProperty {
  import pl.touk.nussknacker.engine.management.periodic.CronScheduleProperty._
  import cats.implicits._

  private lazy val cronsOrError: Either[String, List[Cron]] = {
    val (errors, crons) = labelOrCronExpr
      .split(CronExpressionSeparator)
      .toList
      .map(_.trim)
      .map(expr => Try(parser.parse(expr)).toOption.toRight(s"Expression '$expr' is not a valid cron expression"))
      .separate
    if (errors.nonEmpty) {
      Left(errors.mkString(", "))
    } else {
      Right(crons)
    }
  }

  override def nextRunAt(clock: Clock): Either[String, Option[LocalDateTime]] = {
    val now = ZonedDateTime.now(clock)
    cronsOrError
      .map { crons =>
        crons
          .map(expr => determineNextDate(expr, now))
          .minBy {
            case Some(x) => x.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
            case None => Long.MaxValue
          }
      }
  }

  private def determineNextDate(cron: Cron, zonedDateTime: ZonedDateTime): Option[LocalDateTime] = {
    import scala.compat.java8.OptionConverters._
    val compiledCron = ExecutionTime.forCron(cron)
    val nextTime = compiledCron.nextExecution(zonedDateTime)
    nextTime.asScala.map(_.toLocalDateTime)
  }
}

object CronScheduleProperty {
  private lazy val parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))
  final val CronExpressionSeparator: Char = '|'
}
