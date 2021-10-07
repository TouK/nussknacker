package pl.touk.nussknacker.engine.api.dict

import com.typesafe.config.Config

trait DictServicesFactory {

  /**
   * Create dict registry on engine side. It implements only key->label transformation and it can be done loose
   * (without some checks in external services) because label is resolved to key on UI side.
   */
  def createEngineDictRegistry(declarations: Map[String, DictDefinition]): EngineDictRegistry

  /**
   * Create UI dict services including `DictRegistry` and `DictQueryService`. DictRegistry must be restrictive
   * in checking label->key because we don't want to save process with invalid dict's key.
   */
  def createUiDictServices(declarations: Map[String, DictDefinition], config: Config): UiDictServices

}

case class UiDictServices(dictRegistry: DictRegistry, dictQueryService: DictQueryService) {

  def close(): Unit = {
    dictRegistry.close();
    dictQueryService.close();
  }

}
