import React, {useCallback} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {displayModalNodeDetails} from "../../../../actions/nk"
import {events} from "../../../../analytics/TrackingEvents"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/properties.svg"
import ProcessUtils from "../../../../common/ProcessUtils"
import {getProcessToDisplay, hasError, isSubprocess} from "../../../../reducers/selectors/graph"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"

function PropertiesButton(): JSX.Element {
  const {t} = useTranslation()
  const dispatch = useDispatch()

  const processToDisplay = useSelector(getProcessToDisplay)
  const hasErrors = useSelector(hasError)

  const onClick = useCallback(
    () => {
      const {properties, id} = processToDisplay
      const eventInfo = {
        category: events.categories.rightPanel,
        name: t("panels.actions.edit-properties.dialog", "properties"),
      }
      dispatch(displayModalNodeDetails({id, ...properties}, false, eventInfo))
    },
    [dispatch, processToDisplay, t],
  )

  const subprocess = useSelector(isSubprocess)
  if (subprocess) {
    return null
  }

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
