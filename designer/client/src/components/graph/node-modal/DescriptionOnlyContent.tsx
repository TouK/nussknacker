import { Box } from "@mui/material";
import { get } from "lodash";
import React from "react";
import { DescriptionView } from "../../../containers/DescriptionView";
import { FieldType } from "./editors/field/Field";
import { rowAceEditor } from "./NodeDetailsContent/NodeTableStyled";
import { NodeField } from "./NodeField";
import { NodeTypeDetailsContentProps, useNodeTypeDetailsContentLogic } from "./NodeTypeDetailsContent";

type DescriptionOnlyContentProps = Pick<NodeTypeDetailsContentProps, "node" | "onChange"> & {
    fieldPath: string;
    preview?: boolean;
};

export function DescriptionOnlyContent({ fieldPath, preview, node, onChange }: DescriptionOnlyContentProps) {
    const { setProperty } = useNodeTypeDetailsContentLogic({ node, onChange });

    if (preview) {
        return <DescriptionView>{get(node, fieldPath)}</DescriptionView>;
    }

    return (
        <Box
            sx={{
                [`.${rowAceEditor}, .ace_editor`]: {
                    outline: "none",
                },
                padding: 2,
            }}
        >
            <NodeField
                autoFocus
                renderFieldLabel={() => null}
                setProperty={setProperty}
                node={node}
                isEditMode={true}
                showValidation={false}
                readonly={false}
                errors={[]}
                fieldType={FieldType.markdown}
                fieldName={fieldPath}
            />
        </Box>
    );
}
