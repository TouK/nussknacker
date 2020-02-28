import React, {memo} from "react"
import {useTranslation} from "react-i18next"
import {connect} from "react-redux"
import {RootState} from "../../../../reducers/index"
import {isSubprocess} from "../../selectors/graph"
import {getFeatureSettings} from "../../selectors/settings"
import {CapabilitiesType} from "../../UserRightPanel"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import CountsButton from "./buttons/CountsButton"
import FromFileButton from "./buttons/FromFileButton"
import GenerateButton from "./buttons/GenerateButton"
import HideButton from "./buttons/HideButton"

type OwnProps = {
  capabilities: CapabilitiesType,
}
type Props = OwnProps & StateProps

function TestPanel(props: Props) {
  const {capabilities, isSubprocess, featuresSettings} = props
  const writeAllowed = capabilities.write
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="TEST-PANEL" title={t("panels.test.title", "Test")} isHidden={isSubprocess}>
      {writeAllowed ? <FromFileButton/> : null}
      {writeAllowed ? <HideButton/> : null}
      {writeAllowed ? <GenerateButton/> : null}
      {/*//TODO: counts and metrics should not be visible in archived process*/}
      {featuresSettings?.counts && !isSubprocess ? <CountsButton/> : null}
    </CollapsibleToolbar>

  )
}

const mapState = (state: RootState) => ({
  featuresSettings: getFeatureSettings(state),
  isSubprocess: isSubprocess(state),
})

export type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(TestPanel))
