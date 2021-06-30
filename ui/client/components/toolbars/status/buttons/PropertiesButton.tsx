import React, {useCallback} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {displayModalNodeDetails} from "../../../../actions/nk"
import {events} from "../../../../analytics/TrackingEvents"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/properties.svg"
import {
  getProcessToDisplay,
  getProcessUnsavedNewName,
  hasError,
  hasPropertiesErrors,
} from "../../../../reducers/selectors/graph"
import NodeUtils from "../../../graph/NodeUtils"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"

function PropertiesButton(): JSX.Element {
  const {t} = useTranslation()
  const dispatch = useDispatch()

  const processToDisplay = useSelector(getProcessToDisplay)
  const name = useSelector(getProcessUnsavedNewName)
  const propertiesErrors = useSelector(hasPropertiesErrors)
  const errors = useSelector(hasError)

  const onClick = useCallback(
    () => {
      const processProperties = NodeUtils.getProcessProperties(processToDisplay, name)
      const eventInfo = {
        category: events.categories.rightPanel,
        name: t("panels.actions.edit-properties.dialog", "properties"),
      }
      dispatch(displayModalNodeDetails(processProperties, false, eventInfo))
    },
    [dispatch, name, processToDisplay, t],
  )

  return (
    <ToolbarButton
      name={t("panels.actions.edit-properties.button", "properties")}
      hasError={errors && propertiesErrors}
      icon={<Icon/>}
      onClick={onClick}
    />
  )
}

export default PropertiesButton
