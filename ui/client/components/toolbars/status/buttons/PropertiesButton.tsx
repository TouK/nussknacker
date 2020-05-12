import React from "react"
import {RootState} from "../../../../reducers"
import ProcessUtils from "../../../../common/ProcessUtils"
import {connect} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {displayModalNodeDetails} from "../../../../actions/nk/modal"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {getProcessToDisplay, hasError} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/properties.svg"

type Props = StateProps

function PropertiesButton(props: Props) {
  const {hasErrors, processToDisplay, displayModalNodeDetails} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.edit-properties.button", "properties")}
      hasError={hasErrors && !ProcessUtils.hasNoPropertiesErrors(processToDisplay)}
      icon={<Icon/>}
      onClick={() => displayModalNodeDetails(
        processToDisplay?.properties,
        undefined,
        {
          category: events.categories.rightPanel,
          name: t("panels.actions.edit-properties.dialog", "properties"),
        },
      )}
    />
  )
}

const mapState = (state: RootState) => ({
  processToDisplay: getProcessToDisplay(state),
  hasErrors: hasError(state),
})

const mapDispatch = {
  displayModalNodeDetails,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(PropertiesButton)
