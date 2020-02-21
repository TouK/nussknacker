/* eslint-disable i18next/no-literal-string */
import React from "react"
import {Props as PanelProps} from "../UserRightPanel"
import {RootState} from "../../../reducers/index"
import {connect} from "react-redux"
import HttpService from "../../../http/HttpService"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import {disableToolTipsHighlight, enableToolTipsHighlight} from "../../../actions/nk/tooltips"
import {showMetrics} from "../../../actions/nk/showMetrics"
import {toggleProcessActionDialog} from "../../../actions/nk/toggleProcessActionDialog"
import {hasError, isSubprocess, getProcessId, isSaveDisabled, isDeployPossible, isCancelPossible} from "../selectors"
import {RightPanel} from "../RightPanel"
import {ButtonWithIcon} from "../ButtonWithIcon"
import cn from "classnames"

type PropsPick = Pick<PanelProps,
  | "capabilities"
  | "isStateLoaded"
  | "processState">

type OwnProps = PropsPick
type Props = OwnProps & StateProps

function DeploymentPanel(props: Props) {
  const {capabilities, isSubprocess, processId, deployPossible, hasErrors, saveDisabled, cancelPossible} = props
  const {disableToolTipsHighlight, enableToolTipsHighlight, showMetrics, toggleProcessActionDialog} = props
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
      disabled: !cancelPossible,
      onClick: () => toggleProcessActionDialog("Cancel process", HttpService.cancel, false),
      icon: InlinedSvgs.buttonCancel,
    },
    {
      name: "metrics",
      onClick: () => showMetrics(processId),
      icon: InlinedSvgs.buttonMetrics,
    },
  ]

  const panelName = "Deployment"
  return (
    <RightPanel title={panelName} isHidden={isSubprocess}>
      {buttons.map(({name, title, isHidden, ...props}) => isHidden ? null : (
        <ButtonWithIcon
          {...props}
          key={name}
          name={name}
          title={title || name}
          className={cn("espButton", "right-panel")}
        />
      ))}
    </RightPanel>
  )
}

const mapState = (state: RootState, props: OwnProps) => ({
  processId: getProcessId(state),
  isSubprocess: isSubprocess(state),
  deployPossible: isDeployPossible(state, props),
  saveDisabled: isSaveDisabled(state),
  hasErrors: hasError(state),
  cancelPossible: isCancelPossible(state, props),
})

const mapDispatch = {
  disableToolTipsHighlight,
  enableToolTipsHighlight,
  showMetrics,
  toggleProcessActionDialog,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(DeploymentPanel)
