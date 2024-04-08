package pl.touk.nussknacker.ui.util

import scala.annotation.tailrec

sealed trait ApiAdapterServiceError

case class OutOfRangeAdapterRequestError(currentVersion: Int, signedNoOfVersionsLeftToApply: Int)
    extends ApiAdapterServiceError

trait ApiAdapterService[D <: VersionedData] {
  def getAdapters: Map[Int, ApiAdapter[D]]
  def getCurrentApiVersion: Int = getAdapters.keySet.size + 1

  def adaptDown(data: D, noOfVersions: Int): Either[ApiAdapterServiceError, D] =
    adaptN(data, -noOfVersions)

  def adaptUp(data: D, noOfVersions: Int): Either[ApiAdapterServiceError, D] =
    adaptN(data, noOfVersions)

  @tailrec
  private def adaptN(data: D, noOfVersions: Int): Either[ApiAdapterServiceError, D] = {
    val currentVersion = data.currentVersion()
    val adapters       = getAdapters

    noOfVersions match {
      case 0 => Right(data)
      case n if n > 0 =>
        val adapterO = adapters.get(currentVersion)
        adapterO match {
          case Some(adapter) =>
            adaptN(adapter.liftVersion(data), noOfVersions - 1)
          case None => Left(OutOfRangeAdapterRequestError(currentVersion, n))
        }
      case n if n < 0 =>
        val adapterO = adapters.get(currentVersion - 1)
        adapterO match {
          case Some(adapter) =>
            adaptN(adapter.downgradeVersion(data), noOfVersions + 1)
          case None => Left(OutOfRangeAdapterRequestError(currentVersion, n))
        }
    }
  }

  @tailrec
  private def adaptNUnsafe(data: D, noOfVersions: Int): D = {
    val currentVersion = data.currentVersion()
    val adapters       = getAdapters

    noOfVersions match {
      case 0 => data
      case n if n > 0 =>
        val adapter = adapters(currentVersion)
        adaptNUnsafe(adapter.liftVersion(data), noOfVersions - 1)
      case n if n < 0 =>
        val adapter = adapters(currentVersion - 1)
        adaptNUnsafe(adapter.downgradeVersion(data), noOfVersions + 1)
    }
  }

}
