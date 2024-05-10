import React, { ReactElement } from "react";
import { MarkdownStyled } from "../../graph/node-modal/NodeAdditionalInfoBox";
import { FormField } from "./FormField";
import { FormFields } from "./FormFields";

export function MarkdownForm({ content }: { content: string }): ReactElement {
    const overrides = {
        Fields: FormFields,
        Field: FormField,
    };

    const hasFields = Object.keys(overrides).some((name) => RegExp(`<${name}`).test(content));

    return (
        <>
            <MarkdownStyled options={{ overrides }}>{content}</MarkdownStyled>
            {hasFields ? null : <FormFields />}
        </>
    );
}
