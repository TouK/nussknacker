import i18next from "i18next";
import { isEqual } from "lodash";
import React, { useCallback, useMemo, useRef, useState } from "react";
import { useDebounceFn } from "rooks";

import { DndItems } from "../../../../../common/dndItems/DndItems";
import { RowFieldLabel } from "../../../aggregate/rowFieldLabel";
import type { WithUuid } from "../../../appendUuid";
import { withUuid } from "../../../appendUuid";
import { FieldsRow } from "../../../fragment-input-definition/FieldsRow";
import { FieldsControl } from "../../../node-row-fields-provider/FieldsControl";
import { EditableEditor } from "../../EditableEditor";
import { prepareEditor } from "../Editor";
import { editorsParameters } from "../editorsParameters";
import { FormatterType, spelFormatters } from "../Formatter";
import { EditorType, ExpressionLang } from "../types";
import { deserialize, type NameValueRecord, serialize } from "./nameValueRecordsListHelpers";

export const NameValueListEditor = prepareEditor(
    ({ expressionObj, onValueChange, variableTypes, readOnly }) => {
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

        const uuidWaitingFor = useRef(null);
        const onAdd = useCallback(() => {
            setData((prevData) => {
                const added = withUuid({ name: '""', value: "" });
                uuidWaitingFor.current = added.uuid;
                return [...prevData, added];
            });
        }, [setData]);

        const onRemove = useCallback(
            (_: string, uuid: string) => {
                setData((prevData) => prevData.filter((item) => item.uuid !== uuid));
            },
            [setData],
        );

        const onChangeItem = useCallback(
            (uuid: string, updated: Partial<NameValueRecord>) => {
                setData((prevData) =>
                    prevData.map((item) => {
                        if (item.uuid !== uuid) {
                            return item;
                        }
                        return { ...item, ...updated };
                    }),
                );
            },
            [setData],
        );

        const [hovered, setHovered] = useState<number | null>(null);

        const items = useMemo(
            () =>
                list.map((item, index) => {
                    const { name, uuid, value } = item;
                    const showLabels = hovered == 0;
                    return {
                        item,
                        el: (
                            <FieldsRow
                                key={uuid}
                                uuid={uuid}
                                index={index}
                                ref={(el) => {
                                    if (uuid !== uuidWaitingFor.current) return;
                                    uuidWaitingFor.current = null;
                                    (el.querySelector("input, textarea") as HTMLElement)?.focus();
                                }}
                            >
                                <RowFieldLabel flexBasis="30%" label="name" showLabel={showLabels}>
                                    <EditableEditor
                                        variableTypes={variableTypes}
                                        editors={[{ type: EditorType.STATIC_STRING_PARAMETER_EDITOR }]}
                                        expressionObj={{
                                            expression: name,
                                            language: ExpressionLang.SpEL,
                                        }}
                                        onValueChange={({ expression, language }) => {
                                            const name =
                                                language === ExpressionLang.SpEL
                                                    ? expression
                                                    : spelFormatters[FormatterType.String].encode(expression);
                                            onChangeItem(uuid, { name });
                                        }}
                                        // showValidation
                                        // fieldErrors={fieldErrors}
                                        showSwitch={false}
                                    />
                                </RowFieldLabel>
                                <RowFieldLabel flexBasis="70%" label="value" showLabel={showLabels}>
                                    <EditableEditor
                                        variableTypes={variableTypes}
                                        expressionObj={{ expression: value, language: ExpressionLang.SpEL }}
                                        onValueChange={({ expression }) => {
                                            onChangeItem(uuid, { value: expression });
                                        }}
                                        // showValidation
                                        // fieldErrors={fieldErrors}
                                        showSwitch={false}
                                    />
                                </RowFieldLabel>
                            </FieldsRow>
                        ),
                    };
                }),
            [hovered, list, onChangeItem, variableTypes],
        );

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
