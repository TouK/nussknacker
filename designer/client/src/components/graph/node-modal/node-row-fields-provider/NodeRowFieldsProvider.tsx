import React, { createContext, PropsWithChildren, useCallback, useContext, useMemo, useState } from "react";
import { FieldsControl } from "./FieldsControl";
import { NodeRow, NodeValue } from "../node";
import { FieldError } from "../editors/Validators";
import ValidationLabels from "../../../modals/ValidationLabels";

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
    errors?: FieldError[];
}

const Context = createContext<FieldsContext>(null);

export function useFieldsContext(): FieldsContext {
    const fieldsContext = useContext(Context);
    if (!fieldsContext) {
        throw new Error(`Used outside <NodeRowFields>!`);
    }
    return fieldsContext;
}

export function NodeRowFieldsProvider({ children, errors = [], ...props }: PropsWithChildren<NodeRowFieldsProps>): JSX.Element {
    const { label, path, onFieldAdd, onFieldRemove, readOnly } = props;
    const [isOpen, setIsOpen] = useState<Record<string, boolean>>({});

    const remove = useMemo(() => {
        if (onFieldRemove) {
            return (uuid: string) => {
                setIsOpen((prevState) => {
                    delete prevState[uuid];

                    return prevState;
                });
                return onFieldRemove(path, uuid);
            };
        }
    }, [onFieldRemove, path]);

    const add = useMemo(() => {
        if (onFieldAdd) {
            return () => onFieldAdd(path);
        }
    }, [onFieldAdd, path]);

    const getIsOpen = useCallback((uuid: string) => isOpen[uuid] || false, [isOpen]);

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
                <ValidationLabels fieldErrors={errors} />
            </NodeValue>
        </NodeRow>
    );
}
