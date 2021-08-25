import {includes} from "lodash"
import React, {useCallback} from "react"
import {Edge, EdgeType} from "../../../../types"
import BaseModalContent from "../BaseModalContent"
import EditableEditor from "../editors/EditableEditor"

interface Props {
  edge: Edge,
  readOnly?: boolean,
  changeEdgeTypeValue: (type: EdgeType) => void,
  showValidation?: boolean,
  showSwitch?: boolean,
  updateEdgeProp,
  variableTypes,
  edgeErrors?,
  pathsToMark?,
}

export default function EdgeDetailsContent(props: Props): JSX.Element | null {
  const {edge, edgeErrors, readOnly, updateEdgeProp, showValidation, showSwitch, changeEdgeTypeValue, variableTypes, pathsToMark} = props

  const isMarked = useCallback((path) => includes(pathsToMark, path), [pathsToMark])
  const renderFieldLabel = useCallback((label) => <div className="node-label">{label}</div>, [])

  switch (edge.edgeType?.type) {
    case "SwitchDefault": {
      return (
        <BaseModalContent
          edge={edge}
          edgeErrors={edgeErrors}
          readOnly={readOnly}
          isMarked={isMarked}
          changeEdgeTypeValue={changeEdgeTypeValue}
        />
      )
    }
    case "NextSwitch": {
      const expressionObj = {
        expression: edge.edgeType.condition.expression,
        language: edge.edgeType.condition.language,
      }
      return (
        <BaseModalContent
          edge={edge}
          edgeErrors={edgeErrors}
          readOnly={readOnly}
          isMarked={isMarked}
          changeEdgeTypeValue={changeEdgeTypeValue}
        >
          <EditableEditor
            variableTypes={variableTypes}
            fieldLabel={"Expression"}
            renderFieldLabel={renderFieldLabel}
            expressionObj={expressionObj}
            readOnly={readOnly}
            isMarked={isMarked("edgeType.condition.expression")}
            showValidation={showValidation}
            showSwitch={showSwitch}
            onValueChange={(newValue) => updateEdgeProp("edgeType.condition.expression", newValue)}
          />
        </BaseModalContent>
      )
    }
    default:
      return null
  }
}
