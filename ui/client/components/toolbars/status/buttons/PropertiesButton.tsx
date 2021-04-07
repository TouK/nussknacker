import React, {useCallback} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {displayModalNodeDetails} from "../../../../actions/nk"
import {events} from "../../../../analytics/TrackingEvents"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/properties.svg"
import ProcessUtils from "../../../../common/ProcessUtils"
import {getProcessNewId, getProcessToDisplay, hasError} from "../../../../reducers/selectors/graph"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"

function PropertiesButton(): JSX.Element {
  const {t} = useTranslation()
  const dispatch = useDispatch()

  const processToDisplay = useSelector(getProcessToDisplay)
  const id = useSelector(getProcessNewId)
  const hasErrors = useSelector(hasError)

  const onClick = useCallback(
    () => {
      const {properties} = processToDisplay
      const eventInfo = {
        category: events.categories.rightPanel,
        name: t("panels.actions.edit-properties.dialog", "properties"),
      }
      dispatch(displayModalNodeDetails({id, ...properties}, false, eventInfo))
    },
    [dispatch, id, processToDisplay, t],
  )

  return (
    <ToolbarButton
      name={t("panels.actions.edit-properties.button", "properties")}
      hasError={hasErrors && !ProcessUtils.hasNoPropertiesErrors(processToDisplay)}
      icon={<Icon/>}
      onClick={onClick}
    />
  )
}

export default PropertiesButton
