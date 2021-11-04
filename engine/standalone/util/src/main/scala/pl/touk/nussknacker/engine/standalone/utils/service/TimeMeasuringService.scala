package pl.touk.nussknacker.engine.standalone.utils.service

import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.util.service.GenericTimeMeasuringService

trait TimeMeasuringService extends GenericTimeMeasuringService { self: Service => }
