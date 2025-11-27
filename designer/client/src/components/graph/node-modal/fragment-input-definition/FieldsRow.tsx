import { cx } from "@emotion/css";
import { styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useCallback } from "react";

import { useFieldsContext } from "../node-row-fields-provider/NodeRowFieldsProvider";
import { NodeRow } from "../node/NodeRow";
import { NodeValue } from "../node/NodeValue";
import { movableRow, nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import { RemoveButton } from "./buttons/RemoveButton";

const StyledFieldsRow = styled(NodeRow)`
    .fieldName {
        width: 28%;
    }
    .${nodeValue} {
        &.fieldName {
            flex-basis: 30%;
            max-width: 20em;
        }
        &.fieldRemove {
            flex: 0;
        }
    }
`;

interface FieldsRow {
    index: number;
    uuid: string;
    className?: string;
}

export function FieldsRow({ index, uuid, className, children }: PropsWithChildren<FieldsRow>): React.JSX.Element {
    const { readOnly, remove } = useFieldsContext();
    const onClick = useCallback(() => remove?.(uuid), [uuid, remove]);
    return (
        <StyledFieldsRow className={cx(movableRow, className)} data-testid={`fieldsRow:${index}`}>
            {children}
            {!readOnly && remove && (
                <NodeValue className="fieldRemove">
                    <RemoveButton onClick={onClick} />
                </NodeValue>
            )}
        </StyledFieldsRow>
    );
}
