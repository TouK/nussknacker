import { Box } from "@mui/material";
import { get } from "lodash";
import React from "react";

import { DescriptionView } from "../../../containers/DescriptionView";
import type { NodeType, PropertiesType } from "../../../types/node";
import { FieldType } from "./editors/field/Field";
import { rowAceEditor } from "./NodeDetailsContent/NodeTableStyled";
import { NodeField } from "./NodeField";

type DescriptionOnlyContentProps = {
    onChange: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    properties: PropertiesType;
    fieldPath: string;
    preview?: boolean;
};

export function DescriptionOnlyContent({ fieldPath, preview, properties, onChange }: DescriptionOnlyContentProps) {
    if (preview) {
        return <DescriptionView>{get(properties, fieldPath)}</DescriptionView>;
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
                setProperty={onChange}
                node={properties}
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
