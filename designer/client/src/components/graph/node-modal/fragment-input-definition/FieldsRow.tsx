import React, { PropsWithChildren, useCallback } from "react";
import { useFieldsContext } from "../node-row-fields-provider";
import { NodeRow, NodeValue } from "../node/";
import { RemoveButton } from "./buttons/RemoveButton";
import { cx } from "@emotion/css";
import { styled } from "@mui/material";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";

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

export function FieldsRow({ index, uuid, className, children }: PropsWithChildren<FieldsRow>): JSX.Element {
    const { readOnly, remove } = useFieldsContext();
    const onClick = useCallback(() => remove?.(uuid), [uuid, remove]);
    return (
        <StyledFieldsRow className={cx("movable-row", className)} data-testid={`fieldsRow:${index}`}>
            {children}
            {!readOnly && remove && (
                <NodeValue className="fieldRemove">
                    <RemoveButton onClick={onClick} />
                </NodeValue>
            )}
        </StyledFieldsRow>
    );
}
