import React, {useCallback} from "react"
import {Error, errorValidator, mandatoryValueValidator} from "./editors/Validators"
import EditableEditor from "./editors/EditableEditor"
import LabeledInput from "./editors/field/LabeledInput"
import LabeledTextarea from "./editors/field/LabeledTextarea"
import {NodeId, NodeType, TypedObjectTypingResult, TypingInfo, TypingResult, VariableTypes} from "../../../types"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"
import {useDiffMark} from "./PathsToMark"
import {useSelector} from "react-redux"
import {RootState} from "../../../reducers"
import {getExpressionType, getNodeTypingInfo} from "./NodeDetailsContent/selectors"
import ProcessUtils from "../../../common/ProcessUtils"

const DEFAULT_EXPRESSION_ID = "$expression"

function getTypingResult(expressionType: TypedObjectTypingResult, nodeTypingInfo: TypingInfo): TypedObjectTypingResult | TypingResult {
  return expressionType || nodeTypingInfo?.[DEFAULT_EXPRESSION_ID]
}

interface Props {
  isEditMode?: boolean,
  node: NodeType,
  setProperty: (fieldName: string, value: string) => void,
  showValidation: boolean,
  showSwitch?: boolean,
  variableTypes: VariableTypes,
  renderFieldLabel: (label: string) => React.ReactNode,
  fieldErrors?: Error[],
  originalNodeId: NodeId,
}

export default function Variable({
  node,
  setProperty,
  isEditMode,
  showValidation,
  fieldErrors,
  variableTypes,
  renderFieldLabel,
  originalNodeId,
}: Props): JSX.Element {
  const onExpressionChange = useCallback((value: string) => setProperty("value.expression", value), [setProperty])
  const [isMarked] = useDiffMark()
  const inferredVariableType = useSelector((state: RootState) => {
    const expressionType = getExpressionType(state)(originalNodeId)
    const nodeTypingInfo = getNodeTypingInfo(state)(originalNodeId)
    const varExprType = getTypingResult(expressionType, nodeTypingInfo)
    return ProcessUtils.humanReadableType(varExprType)
  })
  const readOnly = !isEditMode
  return (
    <NodeTableBody className="node-variable-builder-body">
      <LabeledInput
        value={node.id}
        onChange={(event) => setProperty("id", event.target.value)}
        isMarked={isMarked("id")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator]}
      >
        {renderFieldLabel("Name")}
      </LabeledInput>
      <LabeledInput
        value={node.varName}
        onChange={(event) => setProperty("varName", event.target.value)}
        isMarked={isMarked("varName")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator, errorValidator(fieldErrors, "varName")]}
      >
        {renderFieldLabel("Variable Name")}
      </LabeledInput>
      <EditableEditor
        fieldName="expression"
        fieldLabel={"Expression"}
        renderFieldLabel={renderFieldLabel}
        expressionObj={node.value}
        onValueChange={onExpressionChange}
        readOnly={readOnly}
        showValidation={showValidation}
        showSwitch={false}
        errors={fieldErrors}
        variableTypes={variableTypes}
        validationLabelInfo={inferredVariableType}
      />
      <LabeledTextarea
        value={node?.additionalFields?.description || ""}
        onChange={(event) => setProperty("additionalFields.description", event.target.value)}
        isMarked={isMarked("additionalFields.description")}
        readOnly={readOnly}
        className={"node-input"}
      >
        {renderFieldLabel("Description")}
      </LabeledTextarea>
    </NodeTableBody>
  )
}
