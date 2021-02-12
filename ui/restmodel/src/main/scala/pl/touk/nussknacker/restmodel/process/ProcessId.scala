package pl.touk.nussknacker.restmodel.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.ProcessName

@JsonCodec final case class ProcessId(value: Long) extends AnyVal

@JsonCodec final case class ProcessIdWithName(id: ProcessId, name: ProcessName)

final case class ProcessIdWithNameAndCategory(id: ProcessId, name: ProcessName, category: String)
