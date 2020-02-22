/* eslint-disable i18next/no-literal-string */
import React from "react"
import {Props as PanelProps} from "../../../UserRightPanel"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import HttpService from "../../../../../http/HttpService"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {disableToolTipsHighlight, enableToolTipsHighlight} from "../../../../../actions/nk/tooltips"
import {toggleProcessActionDialog} from "../../../../../actions/nk/toggleProcessActionDialog"
import {hasError, isSaveDisabled, isDeployPossible} from "../../../selectors"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type PropsPick = Pick<PanelProps,
  | "isStateLoaded"
  | "processState">

type OwnProps = PropsPick
type Props = OwnProps & StateProps

function DeployButton(props: Props) {
  const {deployPossible, hasErrors, saveDisabled} = props
  const {disableToolTipsHighlight, enableToolTipsHighlight, toggleProcessActionDialog} = props
  const deployToolTip = hasErrors ? "Cannot deploy due to errors. Please look at the left panel for more details." : !saveDisabled ? "You have unsaved changes." : null
  const deployMouseOver = hasErrors ? enableToolTipsHighlight : null
  const deployMouseOut = hasErrors ? disableToolTipsHighlight : null

  return (
    <ButtonWithIcon
      name={"deploy"}
      disabled={!deployPossible}
      icon={InlinedSvgs.buttonDeploy}
      title={deployToolTip}
      onClick={() => toggleProcessActionDialog("Deploy process", HttpService.deploy, true)}
      onMouseOver={deployMouseOver}
      onMouseOut={deployMouseOut}
    />
  )
}

const mapState = (state: RootState, props: OwnProps) => ({
  deployPossible: isDeployPossible(state, props),
  saveDisabled: isSaveDisabled(state),
  hasErrors: hasError(state),
})

const mapDispatch = {
  disableToolTipsHighlight,
  enableToolTipsHighlight,
  toggleProcessActionDialog,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(DeployButton)
