package pl.touk.nussknacker.ui.util

import scala.annotation.tailrec

trait ApiAdapterService[D <: VersionedData] {
  def getAdapters: Map[Int, ApiAdapter[D]]
  def getCurrentApiVersion: Int = getAdapters.keySet.size + 1

  def adaptDown(data: D, noOfVersions: Int): D =
    adaptN(data, -noOfVersions)

  def adaptUp(data: D, noOfVersions: Int): D =
    adaptN(data, noOfVersions)

  @tailrec
  private def adaptN(data: D, noOfVersions: Int): D = {
    val currentVersion = data.currentVersion()
    val adapters       = getAdapters

    noOfVersions match {
      case 0 => data
      case n if n > 0 =>
        val adapter = adapters(currentVersion)
        adaptN(adapter.liftVersion(data), noOfVersions - 1)
      case n if n < 0 =>
        val adapter = adapters(currentVersion - 1)
        adaptN(adapter.downgradeVersion(data), noOfVersions + 1)
    }
  }

}
