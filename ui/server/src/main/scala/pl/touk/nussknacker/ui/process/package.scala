package pl.touk.nussknacker.ui

import pl.touk.nussknacker.engine.api.process.ProcessName

package object process {
  final case class ProcessId(value: Long) extends AnyVal

  final case class ProcessIdWithName(id: ProcessId, name: ProcessName)
}
