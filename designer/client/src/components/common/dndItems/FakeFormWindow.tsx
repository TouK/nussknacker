// provide parents css classes to dragged clone - temporary.
import React, { PropsWithChildren } from "react";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { styled } from "@mui/material";

const ModalContent = styled("div")`
    .node-value {
        &.fieldRemove {
            flex: 0;
        }
    }
`;

export function FakeFormWindow({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        <ModalContent>
            <NodeTable className="fieldsControl">{children}</NodeTable>
        </ModalContent>
    );
}
