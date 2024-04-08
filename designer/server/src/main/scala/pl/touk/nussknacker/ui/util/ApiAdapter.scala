package pl.touk.nussknacker.ui.util

trait ApiAdapter[D] {
  def liftVersion: D => D
  def downgradeVersion: D => D
}
