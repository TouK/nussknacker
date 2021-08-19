import React, {useCallback, useMemo} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/properties.svg"
import {getProcessToDisplay, getProcessUnsavedNewName, hasError, hasPropertiesErrors} from "../../../../reducers/selectors/graph"
import {useWindows} from "../../../../windowManager"
import NodeUtils from "../../../graph/NodeUtils"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {ToolbarButtonProps} from "../../types"

function PropertiesButton(props: ToolbarButtonProps): JSX.Element {
  const {t} = useTranslation()
  const {editNode} = useWindows()
  const {disabled} = props
  const processToDisplay = useSelector(getProcessToDisplay)
  const name = useSelector(getProcessUnsavedNewName)
  const propertiesErrors = useSelector(hasPropertiesErrors)
  const errors = useSelector(hasError)

  const processProperties = useMemo(() => NodeUtils.getProcessProperties(processToDisplay, name), [name, processToDisplay])
  const eventInfo = useMemo(() => ({
    category: events.categories.rightPanel,
    name: t("panels.actions.edit-properties.dialog", "properties"),
  }), [t])

  const onClick = useCallback(
    () => editNode(processProperties, false, eventInfo),
    [editNode, eventInfo, processProperties],
  )

  return (
    <ToolbarButton
      name={t("panels.actions.edit-properties.button", "properties")}
      hasError={errors && propertiesErrors}
      icon={<Icon/>}
      disabled={disabled}
      onClick={onClick}
    />
  )
}

export default PropertiesButton
