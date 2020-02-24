import React from "react"
import {useTranslation} from "react-i18next"
import {connect} from "react-redux"
import {toggleModalDialog} from "../../../../../actions/nk/modal"
import {RootState} from "../../../../../reducers/index"
import Dialogs from "../../../../modals/Dialogs"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function CountsButton(props: Props) {
  const {toggleModalDialog} = props
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.actions.test-counts.button", "counts")}
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
