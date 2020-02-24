import React from "react"
import {Props as PanelProps} from "../../UserRightPanel"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {RightPanel} from "../RightPanel"
import Deploy from "./buttons/DeployButton"
import Cancel from "./buttons/CancelDeployButton"
import Metrics from "./buttons/MetricsButton"
import {isSubprocess} from "../../selectors/graph"
import {useTranslation} from "react-i18next"

type PropsPick = Pick<PanelProps,
  | "capabilities"
  | "isStateLoaded"
  | "processState">

type OwnProps = PropsPick
type Props = OwnProps & StateProps

function DeploymentPanel(props: Props) {
  const {capabilities: {deploy: deployEnabled}, isSubprocess, ...passProps} = props
  const {t} = useTranslation()

  return (
    <RightPanel title={t("panels.deploy.title", "Deployment")} isHidden={isSubprocess}>
      {deployEnabled ? <Deploy  {...passProps}/> : null}
      {deployEnabled ? <Cancel  {...passProps}/> : null}
      <Metrics/>
    </RightPanel>
  )
}

const mapState = (state: RootState) => ({
  isSubprocess: isSubprocess(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(DeploymentPanel)
