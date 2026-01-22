import type { PropsWithChildren } from "react";
import React from "react";

import ValidationLabels from "../../../modals/ValidationLabels";
import type { FieldError } from "../editors/Validators";
import { NodeRow } from "../node/NodeRow";
import { NodeValue } from "../node/NodeValue";
import type { FieldsControlProps } from "./FieldsControl";
import { FieldsControl } from "./FieldsControl";

interface NodeRowFieldsProps extends FieldsControlProps {
    label: string;
    errors?: FieldError[];
}

export function NodeRowFieldsProvider(props: PropsWithChildren<NodeRowFieldsProps>) {
    const { label, path, onFieldAdd, onFieldRemove, readOnly, children, errors = [] } = props;
    return (
        <NodeRow label={label}>
            <NodeValue>
                <FieldsControl path={path} onFieldAdd={onFieldAdd} onFieldRemove={onFieldRemove} readOnly={readOnly}>
                    {children}
                </FieldsControl>
                <ValidationLabels fieldErrors={errors} />
            </NodeValue>
        </NodeRow>
    );
}
