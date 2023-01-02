package pl.touk.nussknacker.restmodel.process

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}

final case class ProcessIdWithName(id: ProcessId, name: ProcessName)
