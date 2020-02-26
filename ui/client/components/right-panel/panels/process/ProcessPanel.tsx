import React, {memo} from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {isEmpty} from "lodash"
import {RightToolPanel} from "../RightToolPanel"
import ArchiveButton from "./buttons/ArchiveButton"
import PDFButton from "./buttons/PDFButton"
import JSONButton from "./buttons/JSONButton"
import ImportButton from "./buttons/ImportButton"
import CompareButton from "./buttons/CompareButton"
import MigrateButton from "./buttons/MigrateButton"
import SaveButton from "./buttons/SaveButton"
import {getFeatureSettings} from "../../selectors/settings"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../UserRightPanel"

type OwnPropsPick = Pick<PassedProps,
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
  const {t} = useTranslation()

  const deployAllowed = capabilities.deploy
  const writeAllowed = capabilities.write
  return (
    <RightToolPanel title={t("panels.process.title", "Process")}>
      {writeAllowed ? <SaveButton/> : null}
      {deployAllowed && !isEmpty(featuresSettings?.remoteEnvironment) ? (
        <MigrateButton processState={processState} isStateLoaded={isStateLoaded}/>
      ) : null}
      <CompareButton/>
      {writeAllowed ? <ImportButton/> : null}
      <JSONButton/>
      <PDFButton exportGraph={exportGraph}/>
      {writeAllowed ? <ArchiveButton isStateLoaded={isStateLoaded} processState={processState}/> : null}
    </RightToolPanel>
  )
}

const mapState = (state: RootState) => ({
  featuresSettings: getFeatureSettings(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(ProcessPanel))
