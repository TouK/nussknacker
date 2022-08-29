/* eslint-disable i18next/no-literal-string */
import React, {SetStateAction, useMemo} from "react"
import {Edge, NodeType, NodeValidationError} from "../../../types"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import {useSelector} from "react-redux"
import {getCurrentErrors,} from "./NodeDetailsContent/selectors"
import {RootState} from "../../../reducers"
import {NodeTable} from "./NodeDetailsContent/NodeTable"
import {partition} from "lodash"
import NodeErrors from "./NodeErrors"
import {TestResultsWrapper} from "./TestResultsWrapper"
import {NodeTypeDetailsContent} from "./NodeTypeDetailsContent"

export const NodeDetailsContent = ({
  node,
  edges,
  onChange,
  nodeErrors,
  showValidation,
  showSwitch,
}: {
  node: NodeType,
  edges?: Edge[],
  onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void,
  nodeErrors?: NodeValidationError[],
  showValidation?: boolean,
  showSwitch?: boolean,
}): JSX.Element => {
  const currentErrors = useSelector((state: RootState) => getCurrentErrors(state)(node.id, nodeErrors))
  const [fieldErrors, otherErrors] = useMemo(() => partition(currentErrors, error => !!error.fieldName), [currentErrors])

  return (
    <NodeTable editable={!!onChange}>
      <NodeErrors errors={otherErrors} message="Node has errors"/>
      <TestResultsWrapper nodeId={node.id}>
        <NodeTypeDetailsContent
          node={node}
          edges={edges}
          onChange={onChange}
          fieldErrors={fieldErrors}
          showValidation={showValidation}
          showSwitch={showSwitch}
        />
      </TestResultsWrapper>
      <NodeAdditionalInfoBox node={node}/>
    </NodeTable>
  )
}

