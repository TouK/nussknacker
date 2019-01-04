package pl.touk.nussknacker.restmodel

import pl.touk.nussknacker.engine.api.process.ProcessName

package object process {
  final case class ProcessId(value: Long) extends AnyVal

  final case class ProcessIdWithName(id: ProcessId, name: ProcessName)
}
