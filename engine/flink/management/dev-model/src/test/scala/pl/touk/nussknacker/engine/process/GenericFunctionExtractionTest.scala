package pl.touk.nussknacker.engine.process

import cats.implicits.catsSyntaxValidatedId
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.generics.NoVarArgumentTypeError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.TypeInfo.{MethodInfo, Parameter}
import pl.touk.nussknacker.engine.flink.test.ClassExtractionBaseTest
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData

class GenericFunctionExtractionTest extends ClassExtractionBaseTest {

  protected override val model: LocalModelData = LocalModelData(ConfigFactory.load(), new DevProcessConfigCreator)
  protected override val outputResource = "/extractedTypes/devCreator.json"

}
