package pl.touk.nussknacker.engine.dict

import cats.data.Validated
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.dict.DictRegistry.DictEntryWithKeyNotExists
import pl.touk.nussknacker.engine.api.dict._
import pl.touk.nussknacker.engine.api.dict.static.StaticDictRegistry

/**
 * This is simple implementation of DictRegistry which handles only StaticDictDefinition
 */
class SimpleDictRegistry(protected val declarations: Map[String, DictDefinition]) extends StaticDictRegistry {

  override protected def handleNotStaticUiKeyBeLabel(definition: DictDefinition, label: String): Validated[DictRegistry.DictEntryWithLabelNotExists, String] =
    throw new IllegalStateException(s"Not supported dict definition: $definition")

  override protected def handleNotStaticLabelByKey(definition: DictDefinition, key: String): Validated[DictEntryWithKeyNotExists, Option[String]] =
    throw new IllegalStateException(s"Not supported dict definition: $definition")

}

class SimpleDictQueryService(dictRegistry: DictRegistry) extends DictQueryService {
  // FIXME
  override def queryEntriesByLabel(dictId: String, labelPattern: String): List[DictEntry] = ???
}


object SimpleDictServicesFactory extends DictServicesFactory {

  override def createUiDictServices(declarations: Map[String, DictDefinition], config: Config): UiDictServices = {
    val dictRegistry = createRegistry(declarations)
    UiDictServices(dictRegistry, new SimpleDictQueryService(dictRegistry))
  }

  override def createEngineDictRegistry(declarations: Map[String, DictDefinition]): EngineDictRegistry =
    createRegistry(declarations).toEngineRegistry

  private def createRegistry(declarations: Map[String, DictDefinition]) = {
    new SimpleDictRegistry(declarations)
  }
}