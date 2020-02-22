/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import Dialogs from "../../../../modals/Dialogs"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {toggleModalDialog} from "../../../../../actions/nk/modal"
import {isSaveDisabled} from "../../../selectors"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function SaveButton(props: Props) {
  const {
    saveDisabled, toggleModalDialog,
  } = props

  return (
    <ButtonWithIcon
      name={`save${!saveDisabled ? "*" : ""}`}
      icon={InlinedSvgs.buttonSave}
      disabled={saveDisabled}
      onClick={() =>
        //TODO: Checking permission to archiwization should be done by check action from state - we should add new action type
        toggleModalDialog(Dialogs.types.saveProcess)}
    />
  )
}

const mapState = (state: RootState) => ({
  saveDisabled: isSaveDisabled(state),
})

const mapDispatch = {
  toggleModalDialog,
}
type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(SaveButton)
