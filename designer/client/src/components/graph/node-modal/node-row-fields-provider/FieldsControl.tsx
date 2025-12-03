import type { PropsWithChildren } from "react";
import React from "react";

import { AddButton } from "./buttons/AddButton";

interface FieldsControlProps {
    readOnly?: boolean;
}

export function FieldsControl(props: PropsWithChildren<FieldsControlProps>): React.JSX.Element {
    const { readOnly, children } = props;

    return (
        <>
            {children}
            {!readOnly && <AddButton />}
        </>
    );
}
