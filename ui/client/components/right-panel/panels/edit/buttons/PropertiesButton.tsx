/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {getProcessToDisplay, isPristine} from "../../../selectors"
import {displayModalNodeDetails} from "../../../../../actions/nk/modal"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function PropertiesButton(props: Props) {
  const {hasErrors, processToDisplay, displayModalNodeDetails} = props

  const propertiesBtnClass = hasErrors && !ProcessUtils.hasNoPropertiesErrors(processToDisplay) ? "esp-button-error right-panel" : null

  return (
    <ButtonWithIcon
      name={"properties"}
      icon={InlinedSvgs.buttonSettings}
      className={propertiesBtnClass}
      onClick={() => displayModalNodeDetails(
        processToDisplay?.properties,
        undefined,
        {
          category: events.categories.rightPanel,
          name: "properties",
        },
      )}
    />
  )
}

const mapState = (state: RootState) => ({
  processToDisplay: getProcessToDisplay(state),
  hasErrors: isPristine(state),
})

const mapDispatch = {
  displayModalNodeDetails,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(PropertiesButton)
