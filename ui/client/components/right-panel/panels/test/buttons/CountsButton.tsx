/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import Dialogs from "../../../../modals/Dialogs"
import {toggleModalDialog} from "../../../../../actions/nk/modal"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function CountsButton(props: Props) {
  const {toggleModalDialog} = props

  return (
    <ButtonWithIcon
      name={"counts"}
      icon={"counts.svg"}
      onClick={() => toggleModalDialog(Dialogs.types.calculateCounts)}
    />

  )
}

const mapState = (state: RootState) => ({})

const mapDispatch = {
  toggleModalDialog,
}

export type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(CountsButton)
