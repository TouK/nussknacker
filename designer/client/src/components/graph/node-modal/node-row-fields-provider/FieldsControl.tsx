import React, { createContext, type PropsWithChildren, useContext, useMemo } from "react";
import { useSetState } from "rooks";

import { AddButton } from "./buttons/AddButton";

interface FieldsContext {
    add?: () => void;
    remove?: (id: string) => void;
    readOnly: boolean;
    isExpanded: (id: string) => boolean;
    toggleExpanded: (id: string) => void;
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

    const [exanded, controls] = useSetState(new Set<string>());

    const ctx = useMemo<FieldsContext>(
        () => ({
            add: !readOnly && onFieldAdd ? () => onFieldAdd(path) : null,
            remove: !readOnly && onFieldRemove ? (uuid: string) => onFieldRemove(path, uuid) : null,
            isExpanded: (id: string) => exanded.has(id),
            toggleExpanded: (id: string) => (exanded.has(id) ? controls.delete(id) : controls.add(id)),
            readOnly,
        }),
        [controls, exanded, onFieldAdd, onFieldRemove, path, readOnly],
    );

    return (
        <Context.Provider value={ctx}>
            {children}
            <AddButton />
        </Context.Provider>
    );
}
