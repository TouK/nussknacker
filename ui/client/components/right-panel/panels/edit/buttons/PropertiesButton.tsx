import React from "react"
import {RootState} from "../../../../../reducers/index"
import ProcessUtils from "../../../../../common/ProcessUtils"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {displayModalNodeDetails} from "../../../../../actions/nk/modal"
import {ToolbarButton} from "../../../ToolbarButton"
import {isPristine, getProcessToDisplay} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"
import cn from "classnames"

type Props = StateProps

function PropertiesButton(props: Props) {
  const {hasErrors, processToDisplay, displayModalNodeDetails} = props
  const {t} = useTranslation()

  const propertiesBtnClass = cn({"esp-button-error": hasErrors && !ProcessUtils.hasNoPropertiesErrors(processToDisplay)})

  return (
    <ToolbarButton
      name={t("panels.actions.edit-properties.button", "properties")}
      icon={InlinedSvgs.buttonSettings}
      className={propertiesBtnClass}
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
  hasErrors: isPristine(state),
})

const mapDispatch = {
  displayModalNodeDetails,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(PropertiesButton)
