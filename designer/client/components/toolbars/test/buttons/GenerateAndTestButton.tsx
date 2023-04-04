import React from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/generate-and-test.svg"
import {getTestCapabilities, isLatestProcessVersion} from "../../../../reducers/selectors/graph"
import {useWindows} from "../../../../windowManager"
import {WindowKind} from "../../../../windowManager/WindowKind"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {ToolbarButtonProps} from "../../types"

type Props = ToolbarButtonProps

function GenerateAndTestButton(props: Props) {
  const {disabled} = props
  const {t} = useTranslation()
  const testCapabilities = useSelector(getTestCapabilities)
  const processIsLatestVersion = useSelector(isLatestProcessVersion)
  const available = !disabled && processIsLatestVersion && testCapabilities.canGenerateTestData
  const {open} = useWindows()

  return (
      <CapabilitiesToolbarButton write
                                 name={t("panels.actions.generate-and-test.button", "test")}
                                 icon={<Icon />}
                                 disabled={!available}
                                 onClick={() => open({
                                   kind: WindowKind.generateDataAndTest,
                                 })}
      />
  )
}

export default GenerateAndTestButton
