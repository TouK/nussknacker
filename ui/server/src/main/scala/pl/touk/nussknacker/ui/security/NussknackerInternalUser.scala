package pl.touk.nussknacker.ui.security

import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

object NussknackerInternalUser extends LoggedUser("Nussknacker", Map("Default"->Set(Permission.Write, Permission.Admin)))
