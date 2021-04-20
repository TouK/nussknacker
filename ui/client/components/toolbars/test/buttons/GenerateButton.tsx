import React from "react"
import {useTranslation} from "react-i18next"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import Dialogs from "../../../modals/Dialogs"
import {toggleModalDialog} from "../../../../actions/nk/modal"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getTestCapabilities, isLatestProcessVersion} from "../../../../reducers/selectors/graph"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/generate.svg"

type Props = StateProps

function GenerateButton(props: Props) {
  const {processIsLatestVersion, testCapabilities, toggleModalDialog} = props
  const {t} = useTranslation()

  return (
    <CapabilitiesToolbarButton write
      name={t("panels.actions.test-generate.button", "generate")}
      icon={<Icon/>}
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
