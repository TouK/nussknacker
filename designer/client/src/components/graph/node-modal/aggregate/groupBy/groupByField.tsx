import React, { useContext, useMemo } from "react";
import { useSelector } from "react-redux";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { NodeRow, NodeValue } from "../../node";
import { getFindAvailableVariables } from "../../NodeDetailsContent/selectors";
import { FieldWrapperProps } from "../../ParameterExpressionField";
import { AggregateContext } from "../aggregateContext";
import { CollectionField } from "./collectionField";

export function GroupByField({ node, isEditMode }: FieldWrapperProps) {
    const { groupBy } = useContext(AggregateContext);

    const findAvailableVariables = useSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(node.id), [findAvailableVariables, node.id]);

    return (
        <NodeRow label={"groupBy"}>
            <NodeValue>
                <CollectionField value={groupBy.values} onChange={groupBy.onChange} variableTypes={variableTypes} disabled={isEditMode} />
                <ValidationLabels fieldErrors={groupBy.fieldErrors} />
            </NodeValue>
        </NodeRow>
    );
}
