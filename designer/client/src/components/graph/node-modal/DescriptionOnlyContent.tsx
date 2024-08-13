import { Box } from "@mui/material";
import { get } from "lodash";
import React from "react";
import { DescriptionView } from "../../../containers/DescriptionView";
import { FieldType } from "./editors/field/Field";
import { rowAceEditor } from "./NodeDetailsContent/NodeTableStyled";
import { NodeField } from "./NodeField";
import { NodeTypeDetailsContentProps, useNodeTypeDetailsContentLogic } from "./NodeTypeDetailsContent";

export function DescriptionOnlyContent({
    preview,
    ...props
}: Pick<NodeTypeDetailsContentProps, "node" | "onChange"> & {
    preview?: boolean;
}): JSX.Element {
    const { setProperty, node } = useNodeTypeDetailsContentLogic({ ...props, errors: [] });
    const fieldName = "additionalFields.description";

    return (
        <>
            {!preview ? (
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
                        fieldName={fieldName}
                    />
                </Box>
            ) : (
                <DescriptionView>{get(node, fieldName)}</DescriptionView>
            )}
        </>
    );
}
