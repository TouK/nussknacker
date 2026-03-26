import type { ReactElement } from "react";
import React from "react";

import { MarkdownStyled } from "../../graph/node-modal/MarkdownStyled";
import { FormField } from "./FormField";
import { FormFields } from "./FormFields";

export function MarkdownForm({ content }: { content: string }): ReactElement {
    const components = {
        "form-fields": FormFields,
        "form-field": FormField,
    };

    const hasFields = Object.keys(components).some((name) => RegExp(`:${name}`).test(content));

    return (
        <>
            <MarkdownStyled components={components}>{content}</MarkdownStyled>
            {hasFields ? null : <FormFields />}
        </>
    );
}
