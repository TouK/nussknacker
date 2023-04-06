import React, {useCallback, useMemo} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/test-with-schema.svg"
import {
  getProcessToDisplay, getProcessUnsavedNewName,
  getTestCapabilities,
  hasError,
  hasPropertiesErrors,
  isLatestProcessVersion
} from "../../../../reducers/selectors/graph"
import {useWindows} from "../../../../windowManager"
import {ToolbarButtonProps} from "../../types"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton";
import NodeUtils from "../../../graph/NodeUtils";

type Props = ToolbarButtonProps

function TestWithSchemaButton(props: Props) {
  const {disabled} = props
  const {t} = useTranslation()
  const processToDisplay = useSelector(getProcessToDisplay)
  const processIsLatestVersion = useSelector(isLatestProcessVersion)
  const testCapabilities = useSelector(getTestCapabilities)
  const name = useSelector(getProcessUnsavedNewName)
  const available = !disabled && processIsLatestVersion && testCapabilities && testCapabilities.canCreateTestView
  const {openNodeWindow} = useWindows()

  const processProperties
    = useMemo(() => NodeUtils.getProcessProperties(processToDisplay, name), [name, processToDisplay])


  const propertiesErrors = useSelector(hasPropertiesErrors)
  const errors = useSelector(hasError)

  const onClick = useCallback(
    () => {
      console.log(processProperties)
      return openNodeWindow(processProperties, processToDisplay)
    },
        [openNodeWindow, processProperties, processToDisplay]
  )

  return (
    <ToolbarButton
      name={t("panels.actions.test-with-schema.button", "test window")}
      hasError={errors && propertiesErrors}
      icon={<Icon/>}
      disabled={!available || disabled}
      onClick={onClick}
    />
  )
}

export default TestWithSchemaButton
