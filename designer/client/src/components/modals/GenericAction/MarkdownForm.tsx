import React, { ReactElement } from "react";
import { FormField } from "./FormField";
import { FormFields } from "./FormFields";
import { MarkdownStyled, MarkdownStyledProps } from "../../graph/node-modal/MarkdownStyled";
import { PropsOf } from "@emotion/react";

declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace JSX {
        interface IntrinsicElements {
            "form-fields": PropsOf<typeof FormFields>;
            "form-field": PropsOf<typeof FormField>;
        }
    }
}

export function MarkdownForm({ content }: { content: string }): ReactElement {
    const components: MarkdownStyledProps["components"] = {
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
