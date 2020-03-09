import React from "react"
import {useTranslation} from "react-i18next"
import {connect} from "react-redux"
import {toggleModalDialog} from "../../../../../actions/nk/modal"
import {RootState} from "../../../../../reducers/index"
import Dialogs from "../../../../modals/Dialogs"
import {ToolbarButton} from "../../../ToolbarButton"

type Props = StateProps

function CountsButton(props: Props) {
  const {toggleModalDialog} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.test-counts.button", "counts")}
      icon={"new/counts.svg"}
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
