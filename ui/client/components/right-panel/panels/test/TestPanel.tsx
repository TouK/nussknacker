/* eslint-disable i18next/no-literal-string */
import React from "react"
import {CapabilitiesType} from "../../UserRightPanel"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {getFeatureSettings, isSubprocess} from "../../selectors"
import {RightPanel} from "../../RightPanel"
import CountsButton from "./buttons/CountsButton"
import GenerateButton from "./buttons/GenerateButton"
import HideButton from "./buttons/HideButton"
import FromFileButton from "./buttons/FromFileButton"

type OwnProps = {
  capabilities: CapabilitiesType,
}
type Props = OwnProps & StateProps

function TestPanel(props: Props) {
  const {capabilities, isSubprocess, featuresSettings} = props
  const writeAllowed = capabilities.write

  return (
    <RightPanel title={"Test"} isHidden={isSubprocess}>
      {writeAllowed ? <FromFileButton/> : null}
      {writeAllowed ? <HideButton/> : null}
      {writeAllowed ? <GenerateButton/> : null}
      {/*//TODO: counts and metrics should not be visible in archived process*/}
      {featuresSettings?.counts && !isSubprocess ? <CountsButton/> : null}
    </RightPanel>

  )
}

const mapState = (state: RootState) => ({
  featuresSettings: getFeatureSettings(state),
  isSubprocess: isSubprocess(state),
})

export type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(TestPanel)
