import React, {memo} from "react"
import {useTranslation} from "react-i18next"
import {connect} from "react-redux"
import {RootState} from "../../../reducers/index"
import {isSubprocess} from "../../../reducers/selectors/graph"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import CountsButton from "./buttons/CountsButton"
import FromFileButton from "./buttons/FromFileButton"
import GenerateButton from "./buttons/GenerateButton"
import HideButton from "./buttons/HideButton"
import {ToolbarButtons} from "../../toolbarComponents/ToolbarButtons"

function TestPanel(props: StateProps) {
  const {isSubprocess} = props
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="TEST-PANEL" title={t("panels.test.title", "Test")} isHidden={isSubprocess}>
      <ToolbarButtons>
        <FromFileButton/>
        <GenerateButton/>
        <CountsButton/>
        <HideButton/>
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

const mapState = (state: RootState) => ({
  isSubprocess: isSubprocess(state),
})

export type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(TestPanel))
