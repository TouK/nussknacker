import React, { createContext, type PropsWithChildren, useContext, useMemo } from "react";

import { AddButton } from "./buttons/AddButton";

interface FieldsContext {
    add?: () => void;
    remove?: (uuid: string) => void;
    readOnly: boolean;
}

const Context = createContext<FieldsContext>(null);

export function useFieldsControl(): FieldsContext {
    const fieldsContext = useContext(Context);
    if (!fieldsContext) {
        throw new Error(`Used outside <NodeRowFields>!`);
    }
    return fieldsContext;
}

export interface FieldsControlProps {
    path: string;
    onFieldAdd?: (namespace: string) => void;
    onFieldRemove?: (namespace: string, uuid: string) => void;
    readOnly?: boolean;
}

export function FieldsControl(props: PropsWithChildren<FieldsControlProps>) {
    const { path, onFieldAdd, onFieldRemove, readOnly, children } = props;

    const ctx = useMemo<FieldsContext>(
        () => ({
            add: !readOnly && onFieldAdd ? () => onFieldAdd(path) : null,
            remove: !readOnly && onFieldRemove ? (uuid: string) => onFieldRemove(path, uuid) : null,
            readOnly,
        }),
        [onFieldAdd, onFieldRemove, path, readOnly],
    );

    return (
        <Context.Provider value={ctx}>
            {children}
            <AddButton />
        </Context.Provider>
    );
}
