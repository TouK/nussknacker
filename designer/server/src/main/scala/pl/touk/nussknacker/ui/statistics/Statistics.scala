package pl.touk.nussknacker.ui.statistics

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.statistics.Encryption.{createSymmetricKey, nuPublicKey}
import pl.touk.nussknacker.ui.statistics.Encryption.EncryptionTypes.{AES, RSA}
import pl.touk.nussknacker.ui.util.IterableExtensions.Chunked

import java.net.{URI, URL, URLEncoder}
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.{Failure, Success, Try}

sealed trait Statistics {
  def prepareURLs(cfg: StatisticUrlConfig): Either[StatisticError, List[URL]]
}

object Statistics extends LazyLogging {

  final class NonEmpty(fingerprint: Fingerprint, requestId: RequestId, rawStatistics: Map[String, String])
      extends Statistics
      with LazyLogging {

    private lazy val queryParamsForEveryURL = List(
      encodeQueryParam(NuFingerprint.name -> fingerprint.value),
      encodeQueryParam(RequestIdStat.name -> requestId.value)
    )

    private val encryptedParamsQueryParamKey = "encryptedParams"
    private val encryptionKeyQueryParamKey   = "encryptionKey"

    override def prepareURLs(cfg: StatisticUrlConfig): Either[StatisticError, List[URL]] =
      rawStatistics.toList
        // Sorting for purpose of easier testing
        .sortBy(_._1)
        .map(encodeQueryParam)
        .groupByMaxChunkSize(cfg.urlBytesSizeLimit)
        .flatMap(queryParams => prepareUrlString(queryParams, cfg))
        .traverse(toURL)

    private def encodeQueryParam(entry: (String, String)): String =
      s"${URLEncoder.encode(entry._1, StandardCharsets.UTF_8)}=${URLEncoder.encode(entry._2, StandardCharsets.UTF_8)}"

    private def prepareUrlString(queryParams: Iterable[String], cfg: StatisticUrlConfig): Option[String] = {
      val joinedQueryParams = if (queryParams.nonEmpty && cfg.encryptQueryParams) {
        val joinedQueryParams = joinQueryParamsToString(queryParams, cfg)
        val encryptedQuery    = encryptQueryParams(joinedQueryParams)
        Some(encryptedQuery)
      } else if (queryParams.nonEmpty) {
        Some(joinQueryParamsToString(queryParams, cfg))
      } else {
        None
      }
      joinedQueryParams.map(jqp => prependWithAddress(jqp, cfg))
    }

    private def encryptQueryParams(queryParams: String): String = {
      val symmetricKey         = createSymmetricKey
      val encryptedQueryParams = Encryption.encode(queryParams, AES, symmetricKey)
      val encryptedSecretKey =
        Encryption.encode(Base64.getEncoder.encodeToString(symmetricKey.getEncoded), RSA, nuPublicKey)

      s"$encryptedParamsQueryParamKey=$encryptedQueryParams&$encryptionKeyQueryParamKey=$encryptedSecretKey"
    }

    private def joinQueryParamsToString(queryParams: Iterable[String], cfg: StatisticUrlConfig): String = {
      val queryParamsWithFingerprint = queryParams ++ queryParamsForEveryURL
      queryParamsWithFingerprint.mkString(cfg.queryParamsSeparator)
    }

    private def prependWithAddress(joinedQueryParams: String, cfg: StatisticUrlConfig): String = {
      s"${cfg.nuStatsUrl}$joinedQueryParams"
    }

  }

  final object Empty extends Statistics {
    override def prepareURLs(cfg: StatisticUrlConfig): Either[StatisticError, List[URL]] = Right(Nil)
  }

  private[statistics] def toURL(urlString: String): Either[StatisticError, URL] =
    Try(new URI(urlString).toURL) match {
      case Failure(ex) => {
        logger.warn(s"Exception occurred while creating URL from string: [$urlString]", ex)
        Left(CannotGenerateStatisticsError)
      }
      case Success(value) => Right(value)
    }

}
