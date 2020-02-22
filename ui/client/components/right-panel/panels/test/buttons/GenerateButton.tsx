/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import Dialogs from "../../../../modals/Dialogs"
import {toggleModalDialog} from "../../../../../actions/nk/modal"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getTestCapabilities, isLatestProcessVersion} from "../../../selectors/graph"

type Props = StateProps

function GenerateButton(props: Props) {
  const {processIsLatestVersion, testCapabilities, toggleModalDialog} = props

  return (
    <ButtonWithIcon
      name={"generate"}
      icon={"generate.svg"}
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
