/* eslint-disable i18next/no-literal-string */
import React from "react"
import {ExtractedPanel} from "../ExtractedPanel"
import {Props as PanelProps} from "../UserRightPanel"
import {RootState} from "../../../reducers/index"
import ProcessStateUtils from "../../Process/ProcessStateUtils"
import {connect} from "react-redux"
import HttpService from "../../../http/HttpService"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import {disableToolTipsHighlight, enableToolTipsHighlight} from "../../../actions/nk/tooltips"
import {showMetrics} from "../../../actions/nk/showMetrics"
import {toggleProcessActionDialog} from "../../../actions/nk/toggleProcessActionDialog"
import {hasError, isLatestProcessVersion, isSubprocess, getProcessId, isPristine} from "../selectors"

type PropsPick = Pick<PanelProps,
  | "capabilities"
  | "fetchedProcessState">

type OwnProps = PropsPick
type Props = OwnProps & StateProps

function DeploymentPanel(props: Props) {
  const {capabilities, isSubprocess, fetchedProcessState, processId, deployPossible, hasErrors, saveDisabled} = props
  const {disableToolTipsHighlight, enableToolTipsHighlight, showMetrics, toggleProcessActionDialog} = props
  const cancelPossible = () => ProcessStateUtils.canCancel(fetchedProcessState)
  const deployToolTip = hasErrors ? "Cannot deploy due to errors. Please look at the left panel for more details." : !saveDisabled ? "You have unsaved changes." : null
  const deployMouseOver = hasErrors ? enableToolTipsHighlight : null
  const deployMouseOut = hasErrors ? disableToolTipsHighlight : null

  const buttons = [
    {
      name: "deploy",
      isHidden: !capabilities.deploy,
      disabled: !deployPossible,
      icon: InlinedSvgs.buttonDeploy,
      title: deployToolTip,
      onClick: () => toggleProcessActionDialog("Deploy process", HttpService.deploy, true),
      onMouseOver: deployMouseOver,
      onMouseOut: deployMouseOut,
    },
    {
      name: "cancel",
      isHidden: !capabilities.deploy,
      disabled: !cancelPossible(),
      onClick: () => toggleProcessActionDialog("Cancel process", HttpService.cancel, false),
      icon: InlinedSvgs.buttonCancel,
    },
    {
      name: "metrics",
      onClick: () => showMetrics(processId),
      icon: InlinedSvgs.buttonMetrics,
    },
  ]

  return (
    <ExtractedPanel panelName={"Deployment"} buttons={buttons} isHidden={isSubprocess}/>
  )
}

function mapState(state: RootState, props: OwnProps) {
  const {fetchedProcessState} = props
  const saveDisabled = isLatestProcessVersion(state) && isPristine(state)
  const hasErrors = hasError(state)
  return {
    processId: getProcessId(state),
    isSubprocess: isSubprocess(state),
    deployPossible: saveDisabled && !hasErrors && ProcessStateUtils.canDeploy(fetchedProcessState),
    saveDisabled,
    hasErrors,
  }
}

const mapDispatch = {
  disableToolTipsHighlight,
  enableToolTipsHighlight,
  showMetrics,
  toggleProcessActionDialog,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(DeploymentPanel)
