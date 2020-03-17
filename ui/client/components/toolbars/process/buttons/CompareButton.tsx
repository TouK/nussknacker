import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import Dialogs from "../../../modals/Dialogs"
import {toggleModalDialog} from "../../../../actions/nk/modal"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {hasOneVersion} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/compare.svg"

type Props = StateProps

function CompareButton(props: Props) {
  const {
    hasOneVersion,
    toggleModalDialog,
  } = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.process-compare.button", "compare")}
      icon={<Icon/>}
      disabled={hasOneVersion}
      onClick={() => toggleModalDialog(Dialogs.types.compareVersions)}
    />
  )
}

const mapState = (state: RootState) => ({
  hasOneVersion: hasOneVersion(state),
})

const mapDispatch = {
  toggleModalDialog,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(CompareButton)
