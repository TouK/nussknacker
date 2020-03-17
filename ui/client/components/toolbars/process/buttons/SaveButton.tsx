import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import Dialogs from "../../../modals/Dialogs"
import {toggleModalDialog} from "../../../../actions/nk/modal"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {isSaveDisabled} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import cn from "classnames"
import classes from "./SaveButton.styl"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/save.svg"

type Props = StateProps

function SaveButton(props: Props) {
  const {saveDisabled, toggleModalDialog} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.process-save.button", "save")}
      icon={<Icon/>}
      labelClassName={cn("button-label", !saveDisabled && classes.unsaved)}
      disabled={saveDisabled}
      onClick={() =>
        //TODO: Checking permission to archive should be done by check action from state - we should add new action type
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
