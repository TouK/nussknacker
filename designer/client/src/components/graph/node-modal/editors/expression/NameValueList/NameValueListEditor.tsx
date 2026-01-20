import i18next from "i18next";
import { curry, isEqual } from "lodash";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useDebounceFn } from "rooks";

import type { VariableTypes } from "../../../../../../types/validation";
import { DndItems } from "../../../../../common/dndItems/DndItems";
import { RowFieldLabel } from "../../../aggregate/rowFieldLabel";
import type { WithUuid } from "../../../appendUuid";
import { withUuid } from "../../../appendUuid";
import { FieldsRow } from "../../../fragment-input-definition/FieldsRow";
import { FieldsControl } from "../../../node-row-fields-provider/FieldsControl";
import { EditableEditor } from "../../EditableEditor";
import type { FieldError } from "../../Validators";
import { prepareEditor } from "../Editor";
import { editorsParameters } from "../editorsParameters";
import { FormatterType, spelFormatters } from "../Formatter";
import { EditorType, ExpressionLang } from "../types";
import { deserialize, type NameValueRecord, serialize } from "./nameValueRecordsListHelpers";

type FieldProps = {
    fieldErrors: FieldError[];
    onChangeItem: (updated: Partial<NameValueRecord>) => void;
    showLabels: boolean;
    showValidation: boolean;
    showSwitch: boolean;
    variableTypes: VariableTypes;
    value: string;
};

const NameField = ({ showLabels, value, onChangeItem, ...props }: FieldProps) => (
    <RowFieldLabel flexBasis="30%" label="name" showLabel={showLabels}>
        <EditableEditor
            editors={[{ type: EditorType.STATIC_STRING_PARAMETER_EDITOR }]}
            expressionObj={{ expression: value, language: ExpressionLang.SpEL }}
            onValueChange={({ expression, language }) => {
                const name = language === ExpressionLang.SpEL ? expression : spelFormatters[FormatterType.String].encode(expression);
                onChangeItem({ name });
            }}
            {...props}
        />
    </RowFieldLabel>
);

const ValueField = ({ showLabels, value, onChangeItem, ...props }: FieldProps) => (
    <RowFieldLabel flexBasis="70%" label="value" showLabel={showLabels}>
        <EditableEditor
            expressionObj={{ expression: value, language: ExpressionLang.SpEL }}
            onValueChange={({ expression }) => {
                onChangeItem({ value: expression });
            }}
            {...props}
        />
    </RowFieldLabel>
);

export const NameValueListEditor = prepareEditor(
    ({ expressionObj, onValueChange, variableTypes, readOnly, fieldErrors = [], showValidation }) => {
        const { expression, language } = expressionObj;

        const [list, setList] = useState<WithUuid<NameValueRecord>[]>(() => {
            const list = deserialize(expression);
            return list ? list.filter(Boolean).map(withUuid) : [];
        });

        const update = useCallback(
            (next: WithUuid<NameValueRecord>[]) => {
                onValueChange?.({ expression: serialize(next), language });
            },
            [language, onValueChange],
        );
        const [updateFn] = useDebounceFn(update, 200);
        const setData = useCallback<typeof setList>(
            (value) => {
                setList((prev) => {
                    const next = typeof value === "function" ? value(prev) : value;
                    if (isEqual(prev, next)) return prev;
                    updateFn(next);
                    return next;
                });
            },
            [updateFn],
        );

        const uuidWaitingForFocus = useRef(null);
        const onAdd = useCallback(() => {
            setData((prevData) => {
                const added = withUuid({ name: '""', value: "" });
                uuidWaitingForFocus.current = added.uuid;
                return [...prevData, added];
            });
        }, [setData]);

        const onRemove = useCallback(
            (_: string, uuid: string) => {
                setData((prevData) => prevData.filter((item) => item.uuid !== uuid));
            },
            [setData],
        );

        const onChangeItem = useMemo(
            () =>
                curry((uuid: string, updated: Partial<NameValueRecord>) => {
                    setData((prevData) =>
                        prevData.map((item) => {
                            if (item.uuid !== uuid) {
                                return item;
                            }
                            return { ...item, ...updated };
                        }),
                    );
                }),
            [setData],
        );

        const [hovered, setHovered] = useState<number | null>(null);

        const focusField = useCallback(
            (uuid: string) => (el: HTMLElement) => {
                if (uuid !== uuidWaitingForFocus.current) return;
                uuidWaitingForFocus.current = null;
                (el.querySelector("input, textarea") as HTMLElement)?.focus();
            },
            [],
        );

        const items = useMemo(() => {
            return list.map((item, index) => {
                const fieldProps = {
                    onChangeItem: onChangeItem(item.uuid),
                    variableTypes,
                    fieldErrors,
                    showLabels: hovered == 0,
                    showValidation,
                    showSwitch: false,
                };
                const el = (
                    <FieldsRow key={item.uuid} uuid={item.uuid} index={index} ref={focusField(item.uuid)}>
                        <NameField value={item.name} {...fieldProps} />
                        <ValueField value={item.value} {...fieldProps} />
                    </FieldsRow>
                );
                return { item, el };
            });
        }, [fieldErrors, focusField, hovered, list, onChangeItem, showValidation, variableTypes]);

        return (
            <FieldsControl path={null} onFieldRemove={list.length >= 1 && onRemove} onFieldAdd={onAdd} readOnly={readOnly}>
                <DndItems
                    disabled={readOnly || list.length <= 1}
                    items={items}
                    onChange={setData}
                    onDestinationChange={setHovered}
                    sx={{
                        paddingTop: items.length > 0 ? 2 : null,
                    }}
                />
            </FieldsControl>
        );
    },
    {
        isSwitchableTo: ({ expression, language }, editorConfig) => {
            if (!expression) return true;
            if (language !== ExpressionLang.SpEL) return false;
            return !deserialize(expression)?.some((v) => !v);
        },
        notSwitchableToHint: () =>
            i18next.t(
                "editors.NameValueListEditor.notSwitchableToHint",
                "Expression must valid List of Records to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.NAME_VALUE_LIST_EDITOR].displayName },
            ),
    },
);
