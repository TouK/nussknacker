package pl.touk.nussknacker.ui.process.fragment

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.fragment.FragmentWithoutValidatorsDefinitionExtractor
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

// TODO: Should we move it to interpreter?
class FragmentsStaticDefinitionPreparer(
    fragmentRepository: FragmentRepository,
    classLoader: ClassLoader,
    processingType: ProcessingType
)(implicit ec: ExecutionContext) {

  private val definitionExtractor = new FragmentWithoutValidatorsDefinitionExtractor(classLoader)

  def prepareStaticDefinitions(implicit user: LoggedUser): Future[List[(ProcessingType, ComponentStaticDefinition)]] = {
    fragmentRepository.fetchLatestFragments(processingType).map(prepareStaticDefinitions)
  }

  private def prepareStaticDefinitions(
      fragmentsDetails: List[FragmentDetails],
  ): List[(String, ComponentStaticDefinition)] = {
    for {
      details    <- fragmentsDetails
      definition <- definitionExtractor.extractFragmentComponentDefinition(details.canonical).toOption
    } yield {
      details.canonical.name.value -> definition.toStaticDefinition(details.category)
    }
  }

}
