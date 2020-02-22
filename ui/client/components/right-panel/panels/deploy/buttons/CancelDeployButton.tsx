/* eslint-disable i18next/no-literal-string */
import React from "react"
import {Props as PanelProps} from "../../../UserRightPanel"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import HttpService from "../../../../../http/HttpService"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {toggleProcessActionDialog} from "../../../../../actions/nk/toggleProcessActionDialog"
import {isCancelPossible} from "../../../selectors"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type PropsPick = Pick<PanelProps,
  | "isStateLoaded"
  | "processState">

type OwnProps = PropsPick
type Props = OwnProps & StateProps

function CancelDeployButton(props: Props) {
  const {cancelPossible, toggleProcessActionDialog} = props

  return (
    <ButtonWithIcon
      name={"cancel"}
      disabled={!cancelPossible}
      icon={InlinedSvgs.buttonCancel}
      onClick={() => toggleProcessActionDialog("Cancel process", HttpService.cancel, false)}
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
