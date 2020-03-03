import React, {memo} from "react"
import {useSelector} from "react-redux"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import Deploy from "./buttons/DeployButton"
import Cancel from "./buttons/CancelDeployButton"
import Metrics from "./buttons/MetricsButton"
import {useTranslation} from "react-i18next"
import {getCapabilities} from "../../selectors/other"
import {isSubprocess} from "../../selectors/graph"

function DeploymentPanel() {
  const capabilities = useSelector(getCapabilities)
  const isHidden = useSelector(isSubprocess)
  const {t} = useTranslation()
  return (
    <CollapsibleToolbar id="DEPLOYMENT-PANEL" title={t("panels.deploy.title", "Deployment")} isHidden={isHidden}>
      {capabilities.deploy ? <Deploy/> : null}
      {capabilities.deploy ? <Cancel/> : null}
      <Metrics/>
    </CollapsibleToolbar>
  )
}

export default memo(DeploymentPanel)
