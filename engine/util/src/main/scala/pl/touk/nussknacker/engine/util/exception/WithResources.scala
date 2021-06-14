package pl.touk.nussknacker.engine.util.exception

object WithResources {

  // based on Oracle try-with-resources explanation from
  // https://www.oracle.com/technical-resources/articles/java/trywithresources.html
  def use[Resource <: AutoCloseable, Result](resource: Resource)
                                            (consumer: Resource => Result): Result = {

    var maybeThrown: Throwable = null
    try {
      consumer(resource)
    } catch {
      case thrown: Throwable =>
        maybeThrown = thrown
        throw maybeThrown
    } finally {
      if (maybeThrown != null) {
        try {
          resource.close()
        } catch {
          case closeThrown: Throwable =>
            maybeThrown.addSuppressed(closeThrown)
        }
      } else {
        resource.close()
      }
    }
  }
}
