import React, {PropsWithChildren, useCallback} from "react"
import {Field, NodeType} from "../../../types"
import LabeledInput from "./editors/field/LabeledInput"
import LabeledTextarea from "./editors/field/LabeledTextarea"
import {Error, errorValidator, mandatoryValueValidator} from "./editors/Validators"

export interface NodeDetailsProps<F extends Field> {
  isMarked: (paths: string) => boolean,
  node: NodeType<F>,
  onChange: (propToMutate: string, newValue: unknown) => void,
  readOnly?: boolean,
  showValidation: boolean,
  renderFieldLabel: (label: string) => React.ReactNode,
  errors: Error[],
}

interface NodeCommonDetailsDefinitionProps<F extends Field> extends PropsWithChildren<NodeDetailsProps<F>> {
  outputName?: string,
  outputField?: string,
}

export function NodeCommonDetailsDefinition<F extends Field>({children, ...props}: NodeCommonDetailsDefinitionProps<F>): JSX.Element {
  const {
    isMarked, node, onChange, readOnly,
    showValidation, renderFieldLabel, errors,
    outputField,
    outputName,
  } = props

  const onInputChange = useCallback((path: string, event: React.ChangeEvent<HTMLInputElement>) => {
    onChange(path, event.target.value)
  }, [onChange])

  return (
    <div className="node-table-body node-variable-builder-body">
      <LabeledInput
        renderFieldLabel={() => renderFieldLabel("Name")}
        value={node.id}
        onChange={(event) => onInputChange("id", event)}
        isMarked={isMarked("id")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator]}
      />

      {outputField && outputName && (
        <LabeledInput
          renderFieldLabel={() => renderFieldLabel(outputName)}
          value={node[outputField]}
          onChange={(event) => onInputChange(outputField, event)}
          isMarked={isMarked(outputField)}
          readOnly={readOnly}
          showValidation={showValidation}
          validators={[mandatoryValueValidator, errorValidator(errors, outputField)]}
        />
      )}

      {children}

      <LabeledTextarea
        renderFieldLabel={() => renderFieldLabel("Description")}
        value={node.additionalFields?.description || ""}
        onChange={(event) => onInputChange("additionalFields.description", event)}
        isMarked={isMarked("additionalFields.description")}
        readOnly={readOnly}
        className={"node-input"}
      />
    </div>
  )
}
