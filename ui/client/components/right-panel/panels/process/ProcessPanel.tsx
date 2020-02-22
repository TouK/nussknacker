/* eslint-disable i18next/no-literal-string */
import React from "react"
import {OwnProps as PanelOwnProps} from "../../UserRightPanel"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {isEmpty} from "lodash"
import {getFeatureSettings} from "../../selectors"
import {RightPanel} from "../../RightPanel"
import ArchiveButton from "./buttons/ArchiveButton"
import PDFButton from "./buttons/PDFButton"
import JSONButton from "./buttons/JSONButton"
import ImportButton from "./buttons/ImportButton"
import CompareButton from "./buttons/CompareButton"
import MigrateButton from "./buttons/MigrateButton"
import SaveButton from "./buttons/SaveButton"

type OwnPropsPick = Pick<PanelOwnProps,
  | "capabilities"
  | "isStateLoaded"
  | "processState"
  | "exportGraph">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function ProcessPanel(props: Props) {
  const {
    capabilities, exportGraph, featuresSettings,
    isStateLoaded, processState,
  } = props

  const deployAllowed = capabilities.deploy
  const writeAllowed = capabilities.write
  const panelName = "Process"
  return (
    <RightPanel title={panelName}>
      {writeAllowed ? <SaveButton/> : null}
      {deployAllowed && !isEmpty(featuresSettings?.remoteEnvironment) ? (
        <MigrateButton processState={processState} isStateLoaded={isStateLoaded}/>
      ) : null}
      <CompareButton/>
      {writeAllowed ? <ImportButton/> : null}
      <JSONButton/>
      <PDFButton exportGraph={exportGraph}/>
      {writeAllowed ? <ArchiveButton isStateLoaded={isStateLoaded} processState={processState}/> : null}
    </RightPanel>
  )
}

const mapState = (state: RootState) => ({
  featuresSettings: getFeatureSettings(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(ProcessPanel)
