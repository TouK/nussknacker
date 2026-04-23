import React from "react";

import type { Edge, EdgeType } from "../../../../types/edge";
import { EdgeKind } from "../../../../types/edge";
import BaseModalContent from "../BaseModalContent";
import EditableEditor from "../editors/EditableEditor";
import { FieldLabelProvider } from "../editors/RenderFieldLabel";
import { useDiffMark } from "../PathsToMark";

interface Props {
    edge: Edge;
    readOnly?: boolean;
    changeEdgeTypeValue: (type: EdgeType["type"]) => void;
    changeEdgeTypeCondition: (condition: EdgeType["condition"]["expression"]) => void;
    showValidation?: boolean;
    showSwitch?: boolean;
    variableTypes;
}

export default function EdgeDetailsContent({
    edge,
    readOnly,
    changeEdgeTypeValue,
    changeEdgeTypeCondition,
    showValidation,
    showSwitch,
    variableTypes,
}: Props): React.JSX.Element | null {
    const [isMarked] = useDiffMark();

    switch (edge.edgeType?.type) {
        case EdgeKind.switchDefault: {
            return (
                <BaseModalContent
                    edge={edge}
                    edgeErrors={[]}
                    readOnly={readOnly}
                    isMarked={isMarked}
                    changeEdgeTypeValue={changeEdgeTypeValue}
                />
            );
        }
        case EdgeKind.switchNext: {
            const expressionObj = {
                expression: edge.edgeType.condition.expression,
                language: edge.edgeType.condition.language,
            };
            return (
                <BaseModalContent
                    edge={edge}
                    edgeErrors={[]}
                    readOnly={readOnly}
                    isMarked={isMarked}
                    changeEdgeTypeValue={changeEdgeTypeValue}
                >
                    <FieldLabelProvider>
                        <EditableEditor
                            variableTypes={variableTypes}
                            fieldLabel={"Expression"}
                            expressionObj={expressionObj}
                            readOnly={readOnly}
                            isMarked={isMarked("edgeType.condition.expression")}
                            showValidation={showValidation}
                            showSwitch={showSwitch}
                            onValueChange={({ expression }) => changeEdgeTypeCondition(expression)}
                            fieldErrors={[]}
                            isValidating={false}
                        />
                    </FieldLabelProvider>
                </BaseModalContent>
            );
        }
        default:
            return null;
    }
}
