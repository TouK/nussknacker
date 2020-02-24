import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import HttpService from "../../../../../http/HttpService"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {disableToolTipsHighlight, enableToolTipsHighlight} from "../../../../../actions/nk/tooltips"
import {toggleProcessActionDialog} from "../../../../../actions/nk/toggleProcessActionDialog"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {isDeployPossible, isSaveDisabled, hasError} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../../UserRightPanel"

type PropsPick = Pick<PassedProps,
  | "isStateLoaded"
  | "processState">

type OwnProps = PropsPick
type Props = OwnProps & StateProps

function DeployButton(props: Props) {
  const {t} = useTranslation()
  const {deployPossible, hasErrors, saveDisabled} = props
  const {disableToolTipsHighlight, enableToolTipsHighlight, toggleProcessActionDialog} = props
  const deployToolTip = hasErrors ?
    t("panels.actions.deploy.tooltips.error", "Cannot deploy due to errors. Please look at the left panel for more details.") :
    !saveDisabled ?
      t("panels.actions.deploy.tooltips.unsaved", "You have unsaved changes.") :
      null
  const deployMouseOver = hasErrors ? enableToolTipsHighlight : null
  const deployMouseOut = hasErrors ? disableToolTipsHighlight : null

  return (
    <ButtonWithIcon
      name={t("panels.actions.deploy.button", "deploy")}
      disabled={!deployPossible}
      icon={InlinedSvgs.buttonDeploy}
      title={deployToolTip}
      onClick={() => toggleProcessActionDialog(t("panels.actions.deploy.dialog", "Deploy process"), HttpService.deploy, true)}
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
