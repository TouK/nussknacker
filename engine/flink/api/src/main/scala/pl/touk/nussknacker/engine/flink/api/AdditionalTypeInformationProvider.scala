package pl.touk.nussknacker.engine.flink.api

import org.apache.flink.api.common.typeinfo.TypeInformation

trait AdditionalTypeInformationProvider {

  def additionalTypeInformation: Set[TypeInformation[_]]

}
