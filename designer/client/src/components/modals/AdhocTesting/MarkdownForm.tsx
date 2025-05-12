import type { PropsOf } from "@emotion/react";
import type { ReactElement } from "react";
import React from "react";

import type { MarkdownStyledProps } from "../../graph/node-modal/MarkdownStyled";
import { MarkdownStyled } from "../../graph/node-modal/MarkdownStyled";
import { FormField } from "./FormField";
import { FormFields } from "./FormFields";

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
