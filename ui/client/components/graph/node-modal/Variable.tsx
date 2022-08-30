import React, {useCallback, useMemo} from "react"
import {Error, errorValidator, mandatoryValueValidator} from "./editors/Validators"
import EditableEditor from "./editors/EditableEditor"
import LabeledInput from "./editors/field/LabeledInput"
import LabeledTextarea from "./editors/field/LabeledTextarea"
import {NodeType, VariableTypes} from "../../../types"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"
import {useDiffMark} from "./PathsToMark"
import {useSelector} from "react-redux"
import {getExpressionType, getNodeTypingInfo} from "./NodeDetailsContent/selectors"
import ProcessUtils from "../../../common/ProcessUtils"
import {IdField} from "./IdField"

const DEFAULT_EXPRESSION_ID = "$expression"

interface Props {
  isEditMode?: boolean,
  node: NodeType,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showValidation: boolean,
  showSwitch?: boolean,
  variableTypes: VariableTypes,
  renderFieldLabel: (paramName: string) => JSX.Element,
  fieldErrors?: Error[],
}

export default function Variable({
  node,
  setProperty,
  isEditMode,
  showValidation,
  fieldErrors,
  variableTypes,
  renderFieldLabel,
}: Props): JSX.Element {
  const onExpressionChange = useCallback((value: string) => setProperty("value.expression", value), [setProperty])
  const [isMarked] = useDiffMark()
  const expressionType = useSelector(getExpressionType)
  const nodeTypingInfo = useSelector(getNodeTypingInfo)
  const inferredVariableType = useMemo(() => {
    const varExprType = expressionType(node.id) || nodeTypingInfo(node.id)?.[DEFAULT_EXPRESSION_ID]
    return ProcessUtils.humanReadableType(varExprType)
  }, [expressionType, node.id, nodeTypingInfo])
  const readOnly = !isEditMode
  return (
    <NodeTableBody className="node-variable-builder-body">
      <IdField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
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
