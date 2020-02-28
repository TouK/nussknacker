import React, {memo} from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import Deploy from "./buttons/DeployButton"
import Cancel from "./buttons/CancelDeployButton"
import Metrics from "./buttons/MetricsButton"
import {isSubprocess} from "../../selectors/graph"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../UserRightPanel"

type PropsPick = Pick<PassedProps,
  | "capabilities"
  | "isStateLoaded"
  | "processState">

type OwnProps = PropsPick
type Props = OwnProps & StateProps

function DeploymentPanel(props: Props) {
  const {capabilities: {deploy: deployEnabled}, isSubprocess, ...passProps} = props
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="DEPLOYMENT-PANEL" title={t("panels.deploy.title", "Deployment")} isHidden={isSubprocess}>
      {deployEnabled ? <Deploy  {...passProps}/> : null}
      {deployEnabled ? <Cancel  {...passProps}/> : null}
      <Metrics/>
    </CollapsibleToolbar>
  )
}

const mapState = (state: RootState) => ({
  isSubprocess: isSubprocess(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(DeploymentPanel))
