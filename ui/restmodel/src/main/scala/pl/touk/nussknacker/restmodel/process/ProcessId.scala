package pl.touk.nussknacker.restmodel.process

import pl.touk.nussknacker.engine.api.process.ProcessName

final case class ProcessId(value: Long) extends AnyVal

final case class ProcessIdWithName(id: ProcessId, name: ProcessName)

final case class ProcessIdWithNameAndCategory(id: ProcessId, name: ProcessName, category: String)
