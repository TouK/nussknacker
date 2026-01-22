import i18next from "i18next";
import { curry, isEqual } from "lodash";
import React, { useCallback, useMemo, useRef, useState } from "react";
import { useDebounceFn } from "rooks";

import type { VariableTypes } from "../../../../../../types/validation";
import { DndItems } from "../../../../../common/dndItems/DndItems";
import { getAst } from "../../../aggregate/pareserHelpers";
import { RowFieldLabel } from "../../../aggregate/rowFieldLabel";
import type { WithUuid } from "../../../appendUuid";
import { withUuid } from "../../../appendUuid";
import { FieldsRow } from "../../../fragment-input-definition/FieldsRow";
import { FieldsControl } from "../../../node-row-fields-provider/FieldsControl";
import { EditableEditor } from "../../EditableEditor";
import type { FieldError, Validator } from "../../Validators";
import { extendErrors, HandledErrorType, mandatoryValueValidator } from "../../Validators";
import { prepareEditor } from "../Editor";
import { editorsParameters } from "../editorsParameters";
import { FormatterType, spelFormatters } from "../Formatter";
import { EditorType, ExpressionLang } from "../types";
import { deserialize, type NameValueRecord, serialize } from "./nameValueRecordsListHelpers";

function lineColumnToIndex(text: string, { row, column }: { row: number; column: number }): number {
    let currentLine = 0;
    let currentColumn = 0;
    let i = 0;

    while (i < text.length) {
        if (currentLine === row && currentColumn === column) {
            return i;
        }

        if (text[i] === "\n") {
            currentLine++;
            currentColumn = 0;
        } else {
            currentColumn++;
        }

        i++;
    }

    return -1;
}

const emptyMandatoryValueValidator: Validator = {
    ...mandatoryValueValidator,
    message: () => "",
    description: () => "",
};

const mappedValueValidator = (errors: { error: FieldError; row: string }[], item: WithUuid<NameValueRecord>): Validator => ({
    ...emptyMandatoryValueValidator,
    isValid: (value) => {
        if (!errors) return true;
        if (!item) return true;
        return errors.find((e) => e.row === item.uuid)?.error || true;
    },
});

const spelValuePreValidator: Validator = {
    isValid: (value) => Boolean(getAst(value, false)),
    message: () => i18next.t("spelValuePreValidator.message", "This field have to contain valid SpEL expression"),
    description: () => i18next.t("validator.spelValue.description", "Please enter valid SpEL"),
    handledErrorType: HandledErrorType.InvalidSpelExpressionParameter,
};

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
            onValueChange={(value) => {
                const expression = value.expression.trim();
                if (value.language === ExpressionLang.SpEL) {
                    return onChangeItem({ name: expression });
                }
                if (!expression) {
                    return onChangeItem({ name: "" });
                }
                return onChangeItem({ name: spelFormatters[FormatterType.String].encode(expression) });
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
            const list = deserialize(expression, true);
            return list ? list.filter(Boolean).map(withUuid) : [];
        });

        const pendingList = useRef(list);
        const mappedErrors = useMemo<{ error: FieldError; row: string }[]>(() => {
            if (!fieldErrors) return [];
            const expression = serialize(pendingList.current);
            const ast = getAst(expression);
            return fieldErrors.map(({ details, ...error }) => {
                if (ast?.type !== "InlineList" || details?.type !== "CoordinatesBasedTextRange") {
                    return { error, row: null };
                }
                const start = lineColumnToIndex(expression, details.start);
                const end = lineColumnToIndex(expression, details.end);
                const row = ast.elements.findIndex((el) => {
                    return (el.start <= start && start <= el.end) || (el.start <= end && end <= el.end);
                });
                return { error, row: pendingList.current[row]?.uuid };
            });
        }, [fieldErrors]);

        const update = useCallback(
            (next: WithUuid<NameValueRecord>[]) => {
                onValueChange?.({ expression: serialize(next), language });
            },
            [language, onValueChange],
        );
        const [updateFn] = useDebounceFn(update, 100);
        const setData = useCallback<typeof setList>(
            (value) => {
                setList((prev) => {
                    const next = typeof value === "function" ? value(prev) : value;
                    if (isEqual(prev, next)) return prev;
                    pendingList.current = next;
                    updateFn(next);
                    return next;
                });
            },
            [updateFn],
        );

        const uuidWaitingForFocus = useRef(null);
        const onAdd = useCallback(() => {
            setData((prevData) => {
                const added = withUuid({ name: "", value: "" });
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
                    showLabels: hovered == 0,
                    showSwitch: false,
                };

                const el = (
                    <FieldsRow key={item.uuid} uuid={item.uuid} index={index} ref={focusField(item.uuid)}>
                        <NameField
                            value={item.name}
                            fieldErrors={extendErrors([], item.name, null, [spelValuePreValidator, emptyMandatoryValueValidator])}
                            showValidation={showValidation && Boolean(item.value)}
                            {...fieldProps}
                        />
                        <ValueField
                            value={item.value}
                            fieldErrors={extendErrors([], item.value, null, [
                                spelValuePreValidator,
                                mandatoryValueValidator,
                                mappedValueValidator(mappedErrors, item),
                            ])}
                            showValidation={showValidation && Boolean(item.name)}
                            {...fieldProps}
                        />
                    </FieldsRow>
                );
                return { item, el };
            });
        }, [focusField, hovered, list, mappedErrors, onChangeItem, showValidation, variableTypes]);

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
            const records = deserialize(expression);
            if (!records) return false;
            return !records.some((v) => !v);
        },
        notSwitchableToHint: () =>
            i18next.t(
                "editors.NameValueListEditor.notSwitchableToHint",
                "Expression must valid List of Records to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.NAME_VALUE_LIST_EDITOR].displayName },
            ),
        parseValueOnEditorChange: ({ language, expression }, nextLanguage) => {
            if (language === ExpressionLang.SpEL) {
                const nextExpression = deserialize(expression, true);
                return { expression: serialize(nextExpression), language: nextLanguage };
            }
            return { expression, language: nextLanguage };
        },
    },
);
