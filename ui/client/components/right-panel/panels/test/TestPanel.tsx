import React, {memo} from "react"
import {useTranslation} from "react-i18next"
import {connect} from "react-redux"
import {RootState} from "../../../../reducers/index"
import {isSubprocess} from "../../selectors/graph"
import {getFeatureSettings} from "../../selectors/settings"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import CountsButton from "./buttons/CountsButton"
import FromFileButton from "./buttons/FromFileButton"
import GenerateButton from "./buttons/GenerateButton"
import HideButton from "./buttons/HideButton"
import {getCapabilities} from "../../selectors/other"

function TestPanel(props: StateProps) {
  const {capabilities, isSubprocess, featuresSettings} = props
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="TEST-PANEL" title={t("panels.test.title", "Test")} isHidden={isSubprocess}>
      {capabilities.write ? <FromFileButton/> : null}
      {capabilities.write ? <GenerateButton/> : null}
      {/*//TODO: counts and metrics should not be visible in archived process*/}
      {featuresSettings?.counts && !isSubprocess ? <CountsButton/> : null}
      {capabilities.write ? <HideButton/> : null}
    </CollapsibleToolbar>

  )
}

const mapState = (state: RootState) => ({
  featuresSettings: getFeatureSettings(state),
  isSubprocess: isSubprocess(state),
  capabilities: getCapabilities(state),
})

export type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(TestPanel))
