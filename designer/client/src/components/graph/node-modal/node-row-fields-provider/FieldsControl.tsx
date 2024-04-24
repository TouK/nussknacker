import React, { PropsWithChildren } from "react";
import { AddButton } from "./buttons/AddButton";

interface FieldsControlProps {
    readOnly?: boolean;
}

export function FieldsControl(props: PropsWithChildren<FieldsControlProps>): JSX.Element {
    const { readOnly, children } = props;

    return (
        <>
            {children}
            {!readOnly && <AddButton />}
        </>
    );
}
