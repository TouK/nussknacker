import React, { createContext, PropsWithChildren, useContext, useMemo, useState } from "react";
import { FieldsControl } from "./FieldsControl";
import { NodeRow } from "./NodeRow";
import { NodeValue } from "./NodeValue";

interface FieldsContext {
    add?: () => void;
    remove?: (uuid: string) => void;
    readOnly: boolean;
    getIsOpen: (uuid: string) => boolean;
    toggleIsOpen: (uuid: string) => void;
}

interface NodeRowFieldsProps {
    label: string;
    path: string;
    onFieldAdd?: (namespace: string) => void;
    onFieldRemove?: (namespace: string, uuid: string) => void;
    readOnly?: boolean;
}

const Context = createContext<FieldsContext>(null);

export function useFieldsContext(): FieldsContext {
    const fieldsContext = useContext(Context);
    if (!fieldsContext) {
        throw new Error(`Used outside <NodeRowFields>!`);
    }
    return fieldsContext;
}

export function NodeRowFields({ children, ...props }: PropsWithChildren<NodeRowFieldsProps>): JSX.Element {
    const { label, path, onFieldAdd, onFieldRemove, readOnly } = props;
    const [isOpen, setIsOpen] = useState<Record<string, boolean>>({});

    const remove = useMemo(() => {
        if (onFieldRemove) {
            return (uuid: string) => {
                return onFieldRemove(path, uuid);
            };
        }
    }, [onFieldRemove, path]);

    const add = useMemo(() => {
        if (onFieldAdd) {
            return () => onFieldAdd(path);
        }
    }, [onFieldAdd, path]);

    const getIsOpen = (uuid: string) => isOpen[uuid] || false;

    const toggleIsOpen = (uuid: string) => {
        setIsOpen((prevState) => {
            return { ...prevState, [uuid]: !prevState[uuid] };
        });
    };

    const ctx = useMemo(() => ({ add, remove, readOnly, getIsOpen, toggleIsOpen }), [add, remove, readOnly, getIsOpen]);

    return (
        <NodeRow label={label}>
            <NodeValue>
                <Context.Provider value={ctx}>
                    <FieldsControl readOnly={readOnly}>{children}</FieldsControl>
                </Context.Provider>
            </NodeValue>
        </NodeRow>
    );
}
