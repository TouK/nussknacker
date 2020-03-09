import React from "react"
import {useTranslation} from "react-i18next"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import Dialogs from "../../../../modals/Dialogs"
import {toggleModalDialog} from "../../../../../actions/nk/modal"
import {ToolbarButton} from "../../../ToolbarButton"
import {getTestCapabilities, isLatestProcessVersion} from "../../../selectors/graph"

type Props = StateProps

function GenerateButton(props: Props) {
  const {processIsLatestVersion, testCapabilities, toggleModalDialog} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.test-generate.button", "generate")}
      icon={"new/generate.svg"}
      disabled={!processIsLatestVersion || !testCapabilities.canGenerateTestData}
      onClick={() => toggleModalDialog(Dialogs.types.generateTestData)}
    />
  )
}

const mapState = (state: RootState) => ({
  testCapabilities: getTestCapabilities(state),
  processIsLatestVersion: isLatestProcessVersion(state),
})

const mapDispatch = {
  toggleModalDialog,
}

export type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(GenerateButton)
