import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import Dialogs from "../../../../modals/Dialogs"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {toggleModalDialog} from "../../../../../actions/nk/modal"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {isSaveDisabled} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"
import cn from "classnames"
import classes from "./SaveButton.styl"

type Props = StateProps

function SaveButton(props: Props) {
  const {
    saveDisabled, toggleModalDialog,
  } = props
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.process.actions.save.button", "save")}
      icon={InlinedSvgs.buttonSave}
      className={cn(!saveDisabled && classes.saveEnabled)}
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
