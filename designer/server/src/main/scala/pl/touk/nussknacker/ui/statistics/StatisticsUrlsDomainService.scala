package pl.touk.nussknacker.ui.statistics

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.statistics.StatisticsUrlsDomainService.{
  emptyString,
  nuStatsUrl,
  queryParamsSeparator,
  toURL,
  urlBytesSizeLimit
}

import java.net.{URI, URL, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

class StatisticsUrlsDomainService(private val fingerprint: Fingerprint, private val statistics: Map[String, String])
    extends LazyLogging {

  private lazy val queryParamsForEveryURL = List(
    encodeQueryParam(NuFingerprint.name -> fingerprint.value)
  )

  def prepareUrls(): Either[StatisticError, List[URL]] = {
    val JoinedQueryParams = statistics.toList
      // Sorting for purpose of easier testing
      .sortBy(_._1)
      .map(encodeQueryParam)
    splitUrlsByBytesSizeLimit(JoinedQueryParams)
      .map(toURL)
      .sequence
  }

  private def encodeQueryParam(entry: (String, String)): String =
    s"${URLEncoder.encode(entry._1, StandardCharsets.UTF_8)}=${URLEncoder.encode(entry._2, StandardCharsets.UTF_8)}"

  private def splitUrlsByBytesSizeLimit(queryParamsWithSize: List[String]): List[String] = {
    val (_, queryParamsAcc, urlsAcc) = queryParamsWithSize
      .foldLeft((0, ArrayBuffer[String](), ArrayBuffer[Option[String]]())) {
        case ((bytesSizeAcc, queryParamAcc, urlAcc), queryParam) =>
          val paramSizeInBytes = queryParam.getBytes(StandardCharsets.UTF_8).length
          val newBytesSizeAcc  = bytesSizeAcc + paramSizeInBytes
          if (newBytesSizeAcc > urlBytesSizeLimit) {
            (
              paramSizeInBytes,
              ArrayBuffer[String](queryParam),
              urlAcc :+ prepareUrlString(queryParamAcc, queryParamsForEveryURL)
            )
          } else {
            (
              newBytesSizeAcc,
              queryParamAcc :+ queryParam,
              urlAcc
            )
          }
      }
    val maybeLastUrl = prepareUrlString(queryParamsAcc, queryParamsForEveryURL)
    (urlsAcc :+ maybeLastUrl).flatten.toList
  }

  private def prepareUrlString(queryParams: Iterable[String], paramsForEveryURL: Iterable[String]): Option[String] = {
    if (queryParams.nonEmpty) {
      val queryParamsWithFingerprint = queryParams ++ paramsForEveryURL
      Some(queryParamsWithFingerprint.mkString(nuStatsUrl, queryParamsSeparator, emptyString))
    } else {
      None
    }
  }

}

object StatisticsUrlsDomainService extends LazyLogging {
  // https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cloudfront-limits.html#limits-general
  private val urlBytesSizeLimit: Long = 7000L
  private val nuStatsUrl              = "https://stats.nussknacker.io/?"
  private val queryParamsSeparator    = "&"
  private val emptyString             = ""

  private[statistics] def toURL(urlString: String): Either[StatisticError, URL] =
    Try(new URI(urlString).toURL) match {
      case Failure(ex) => {
        logger.warn(s"Exception occurred while creating URL from string: [$urlString]", ex)
        Left(CannotGenerateStatisticsError)
      }
      case Success(value) => Right(value)
    }

}
