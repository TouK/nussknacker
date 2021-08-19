import i18next from "i18next"
import React, {useCallback} from "react"
import {Scrollbars} from "react-custom-scrollbars"
import {useSelector} from "react-redux"
import {v4 as uuid4} from "uuid"
import ProcessUtils from "../../common/ProcessUtils"
import {getProcessToDisplay, getTestResults} from "../../reducers/selectors/graph"
import {getUi} from "../../reducers/selectors/ui"
import {useWindows} from "../../windowManager"
import {CollapsibleToolbar} from "../toolbarComponents/CollapsibleToolbar"
import {DragHandle} from "../toolbarComponents/DragHandle"
import Errors from "./Errors"
import ValidTips from "./ValidTips"
import Warnings from "./Warnings"

export default function Tips(): JSX.Element {
  const {editNode} = useWindows()

  const showDetails = useCallback((event, node) => {
    event.preventDefault()
    editNode(node)
  }, [editNode])

  const currentProcess = useSelector(getProcessToDisplay)
  const {isToolTipsHighlighted: isHighlighted} = useSelector(getUi)
  const testResults = useSelector(getTestResults)

  const {errors, warnings} = currentProcess.validationResult || {}

  return (
    <CollapsibleToolbar title={i18next.t("panels.tips.title", "Tips")} id="TIPS-PANEL">
      <DragHandle>
        <div id="tipsPanel" className={isHighlighted ? "tipsPanelHighlighted" : "tipsPanel"}>
          <Scrollbars
            renderThumbVertical={props => <div key={uuid4()} {...props} className="thumbVertical"/>}
            hideTracksWhenNotNeeded={true}
          >
            {<ValidTips
              testing={!!testResults}
              hasNeitherErrorsNorWarnings={ProcessUtils.hasNeitherErrorsNorWarnings(currentProcess)}
            />}
            {!ProcessUtils.hasNoErrors(currentProcess) && (
              <Errors
                errors={errors}
                showDetails={showDetails}
                currentProcess={currentProcess}
              />
            )}
            {!ProcessUtils.hasNoWarnings(currentProcess) && (
              <Warnings
                warnings={ProcessUtils.extractInvalidNodes(warnings.invalidNodes)}
                showDetails={showDetails}
                currentProcess={currentProcess}
              />
            )}
          </Scrollbars>
        </div>
      </DragHandle>
    </CollapsibleToolbar>
  )
}
