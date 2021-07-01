import React from "react"
import {useTranslation} from "react-i18next"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import Dialogs from "../../../modals/Dialogs"
import {toggleModalDialog} from "../../../../actions/nk/modal"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getTestCapabilities, isLatestProcessVersion} from "../../../../reducers/selectors/graph"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/generate.svg"
import {ToolbarButtonProps} from "../../types"

type Props = StateProps & ToolbarButtonProps

function GenerateButton(props: Props) {
  const {processIsLatestVersion, testCapabilities, toggleModalDialog, disabled} = props
  const available = !disabled && processIsLatestVersion && testCapabilities.canGenerateTestData
  const {t} = useTranslation()

  return (
    <CapabilitiesToolbarButton write
      name={t("panels.actions.test-generate.button", "generate")}
      icon={<Icon/>}
      disabled={!available}
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
