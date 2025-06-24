package pl.touk.nussknacker.engine.kafka.source

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.test.TestRecord

case class KafkaTestParametersInfo(parametersDefinition: List[Parameter], createTestRecord: Any => TestRecord)
