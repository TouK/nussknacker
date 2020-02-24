import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import HttpService from "../../../../../http/HttpService"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {toggleProcessActionDialog} from "../../../../../actions/nk/toggleProcessActionDialog"
import {ToolbarButton} from "../../../ToolbarButton"
import {isCancelPossible} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../../UserRightPanel"

type PropsPick = Pick<PassedProps,
  | "isStateLoaded"
  | "processState">

type OwnProps = PropsPick
type Props = OwnProps & StateProps

function CancelDeployButton(props: Props) {
  const {t} = useTranslation()
  const {cancelPossible, toggleProcessActionDialog} = props

  return (
    <ToolbarButton
      name={t("panels.actions.deploy-canel.button", "cancel")}
      disabled={!cancelPossible}
      icon={InlinedSvgs.buttonCancel}
      onClick={() => toggleProcessActionDialog(t("panels.actions.deploy-canel.dialog","Cancel process"), HttpService.cancel, false)}
    />
  )
}

const mapState = (state: RootState, props: OwnProps) => ({
  cancelPossible: isCancelPossible(state, props),
})

const mapDispatch = {
  toggleProcessActionDialog,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(CancelDeployButton)
