import { Dialog, DialogContent } from "@mui/material";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { v4 as uuid4 } from "uuid";

import ProcessUtils from "../../../common/ProcessUtils";
import { useUserSettings } from "../../../common/useUserSettings";
import HttpService from "../../../http/HttpService/instance";
import type { RootState } from "../../../reducers";
import { getProcessingType } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { TypedObjectTypingResult, TypingInfo, TypingResult } from "../../../types/definition";
import type { Field, NodeType } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import type { ContextData } from "../../dataMapper/DataMapper";
import { DataMapper } from "../../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../../dataMapper/DataMapperDialogTitle";
import { DescriptionField } from "./DescriptionField";
import EditableEditor from "./editors/EditableEditor";
import type { ExpressionObj } from "./editors/expression/types";
import { EditorType, ExpressionLang } from "./editors/expression/types";
import LabeledInput from "./editors/field/LabeledInput";
import { FieldLabelConsumer } from "./editors/RenderFieldLabel";
import { getValidationErrorsForField } from "./editors/Validators";
import { FieldsRow } from "./fragment-input-definition/FieldsRow";
import type { Option } from "./fragment-input-definition/TypeSelect";
import { TypeSelect } from "./fragment-input-definition/TypeSelect";
import { useInputOutputContext } from "./io/InputOutputContext";
import { NameField } from "./NameField";
import { BuilderIconButton } from "./node-action-buttons/StyledLoadingButton";
import { NodeRowFieldsProvider } from "./node-row-fields-provider/NodeRowFieldsProvider";
import { NodeRow } from "./node/NodeRow";
import { NodeValue } from "./node/NodeValue";
import { getExpressionType, getNodeTypingInfo } from "./NodeDetailsContent/selectors";
import { useDiffMark } from "./PathsToMark";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

const NU_UTILS_PREFIX = "pl.touk.nussknacker.engine.util.functions";
const EXCLUDED_VARIABLES = new Set(["meta"]);

type SuggestionRefClazz = { refClazzName?: string; fields?: Record<string, SuggestionRefClazz>; params?: SuggestionRefClazz[] };

function refClazzToValue(clazz: SuggestionRefClazz): unknown {
    if (clazz.fields && Object.keys(clazz.fields).length > 0) {
        return Object.fromEntries(Object.entries(clazz.fields).map(([k, v]) => [k, refClazzToValue(v)]));
    }
    const name = clazz.refClazzName;
    if (!name) return null;
    if (name.endsWith("String")) return "";
    if (name.endsWith("Integer") || name.endsWith("Long") || name.endsWith("Short")) return 0;
    if (name.endsWith("Double") || name.endsWith("Float") || name.endsWith("BigDecimal")) return 0.0;
    if (name.endsWith("Boolean")) return false;
    if (name.includes("List") || name.includes("Collection")) {
        const elem = clazz.params?.[0];
        return elem ? [refClazzToValue(elem)] : [];
    }
    if (name.includes("Map")) return {};
    return `<${name.split(".").pop()}>`;
}

const DEFAULT_EXPRESSION_ID = "$expression";
const SET_OPERATION = "SET";
const UNSET_OPERATION = "UNSET";

type VariableOperation = typeof SET_OPERATION | typeof UNSET_OPERATION;

function getTypingResult(expressionType: TypingResult, nodeTypingInfo: TypingInfo): TypedObjectTypingResult | TypingResult {
    return expressionType || nodeTypingInfo?.[DEFAULT_EXPRESSION_ID];
}

interface Props {
    isEditMode?: boolean;
    node: NodeType;
    setProperty: SetProperty;
    addElement: (...args: unknown[]) => unknown;
    removeElement: (property: keyof NodeType, uuid: string) => void;
    showValidation: boolean;
    errors: NodeValidationError[];
    showSwitch?: boolean;
    variableTypes: VariableTypes;
}

export default function Variable({
    node,
    setProperty,
    addElement,
    removeElement,
    isEditMode,
    showValidation,
    errors,
    variableTypes,
}: Props): React.JSX.Element {
    const onExpressionChange = useCallback((value: ExpressionObj) => setProperty("value", value), [setProperty]);
    const [isMarked] = useDiffMark();
    const [mapperOpen, setMapperOpen] = useState(false);
    const inferredVariableType = useAppSelector((state: RootState) => {
        const expressionType = getExpressionType(state)(node.id);
        const nodeTypingInfo = getNodeTypingInfo(state)(node.id);
        const varExprType = getTypingResult(expressionType, nodeTypingInfo);
        return ProcessUtils.humanReadableType(varExprType);
    });
    const readOnly = !isEditMode;
    const processingType = useAppSelector(getProcessingType);
    const [showDataMapper] = useUserSettings("node.showFieldExpressionBuilder");
    const [dataMapperContext, setDataMapperContext] = useState<ContextData>({});
    const contextFetched = useRef(false);

    const ioContext = useInputOutputContext();
    const incomingContext = useMemo<ContextData | undefined>(() => {
        const [contexts] = ioContext?.getAvailableContexts("input") ?? [[]];
        if (!contexts.length) return undefined;
        const selected = ioContext?.state.inputDataSetId ? contexts.find((c) => c.id === ioContext.state.inputDataSetId) : undefined;
        const vars = (selected ?? contexts[0]).variables;
        return Object.fromEntries(Object.keys(vars).map((k) => [k, vars[k].pretty]));
    }, [ioContext]);

    const handleInsert = useCallback(
        (spel: string) => {
            onExpressionChange({ expression: spel, language: ExpressionLang.SpEL });
            setMapperOpen(false);
        },
        [onExpressionChange],
    );

    const handleOpenMapper = useCallback(async () => {
        if (incomingContext) {
            setDataMapperContext(incomingContext);
            contextFetched.current = true;
        } else if (!contextFetched.current) {
            contextFetched.current = true;
            try {
                const { data: suggestions } = await HttpService.getExpressionSuggestions(processingType, {
                    expression: { language: ExpressionLang.SpEL, expression: "#" },
                    caretPosition2d: { row: 0, column: 1 },
                    variableTypes,
                });
                const context: ContextData = {};
                for (const s of suggestions) {
                    const refClazz = s.refClazz as unknown as SuggestionRefClazz;
                    if (refClazz.refClazzName?.startsWith(NU_UTILS_PREFIX)) continue;
                    const varName = s.methodName.replace(/^#/, "");
                    if (EXCLUDED_VARIABLES.has(varName)) continue;
                    context[varName] = refClazzToValue(refClazz);
                }
                setDataMapperContext(context);
            } catch {
                // leave context empty — user can paste JSON manually
            }
        }
        setMapperOpen(true);
    }, [processingType, variableTypes, incomingContext]);

    const operation = (node.operation as VariableOperation) === UNSET_OPERATION ? UNSET_OPERATION : SET_OPERATION;
    const operationOptions = useMemo<Option[]>(
        () => [
            { value: SET_OPERATION, label: "Set" },
            { value: UNSET_OPERATION, label: "Unset" },
        ],
        [],
    );

    const selectedOperation = useMemo<Option>(() => {
        return operationOptions.find(({ value }) => value === operation) || operationOptions[0];
    }, [operationOptions, operation]);

    const onOperationChange = useCallback(
        (value: string) => {
            setProperty("operation", value as VariableOperation);
        },
        [setProperty],
    );

    const normalizeUnsetVariableName = useCallback((value: string): string => value.trim().replace(/^#/, ""), []);

    const unsetVariableOptions = useMemo<Option[]>(() => {
        return Object.keys(variableTypes || {})
            .filter((name) => !EXCLUDED_VARIABLES.has(name))
            .sort()
            .map((name) => ({
                value: name,
                label: `#${name}`,
            }));
    }, [variableTypes]);

    const unsetFields = useMemo(() => (node.fields || []) as Field[], [node.fields]);

    const addUnsetVariableField = useCallback(() => {
        const newField: Field = {
            uuid: uuid4(),
            name: "",
            expression: { expression: "", language: ExpressionLang.SpEL },
        };
        addElement("fields", newField);
    }, [addElement]);

    const removeUnsetVariableField = useCallback(
        (_: string, uuid: string) => {
            removeElement("fields", uuid);
        },
        [removeElement],
    );

    const onUnsetVariableChange = useCallback(
        (index: number, value: string) => {
            setProperty(`fields[${index}].name`, normalizeUnsetVariableName(value));
        },
        [normalizeUnsetVariableName, setProperty],
    );

    useEffect(() => {
        if (operation === UNSET_OPERATION && mapperOpen) {
            setMapperOpen(false);
        }
    }, [mapperOpen, operation]);

    return (
        <>
            <NameField node={node} isEditMode={isEditMode} showValidation={showValidation} setProperty={setProperty} errors={errors} />
            <NodeRow label={"Operation"}>
                <NodeValue>
                    <TypeSelect
                        value={selectedOperation}
                        onChange={onOperationChange}
                        options={operationOptions}
                        readOnly={readOnly}
                        fieldErrors={getValidationErrorsForField(errors, "operation")}
                    />
                </NodeValue>
            </NodeRow>
            {operation === SET_OPERATION ? (
                <>
                    <LabeledInput
                        value={node.varName}
                        onChange={(event) => setProperty("varName", event.target.value)}
                        isMarked={isMarked("varName")}
                        readOnly={readOnly}
                        showValidation={showValidation}
                        fieldErrors={getValidationErrorsForField(errors, "varName")}
                    >
                        <FieldLabelConsumer text="Variable Name" />
                    </LabeledInput>
                    <EditableEditor
                        editors={[
                            { type: EditorType.SPEL_PARAMETER_EDITOR },
                            { type: EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR },
                            { type: EditorType.JSON_TEMPLATE_PARAMETER_EDITOR },
                        ]}
                        fieldLabel={"Expression"}
                        expressionObj={node.value}
                        onValueChange={onExpressionChange}
                        readOnly={readOnly}
                        showValidation={showValidation}
                        fieldErrors={getValidationErrorsForField(errors, "$expression")}
                        variableTypes={variableTypes}
                        validationLabelInfo={inferredVariableType}
                        inputAdornmentEnd={
                            isEditMode && showDataMapper ? <BuilderIconButton onClick={handleOpenMapper} label="Data Mapper" /> : undefined
                        }
                    />
                </>
            ) : (
                <NodeRowFieldsProvider
                    label={"Variables to unset"}
                    path={"fields"}
                    onFieldAdd={addUnsetVariableField}
                    onFieldRemove={removeUnsetVariableField}
                    readOnly={readOnly}
                    errors={getValidationErrorsForField(errors, "fields")}
                >
                    {unsetFields.map((field, index) => {
                        const variableName = normalizeUnsetVariableName(field.name || "");
                        const selectedVariable = unsetVariableOptions.find(({ value }) => value === variableName) || {
                            value: variableName,
                            label: variableName ? `#${variableName}` : "",
                        };
                        return (
                            <FieldsRow key={field.uuid || index} uuid={field.uuid || `${index}`} index={index}>
                                <TypeSelect
                                    value={selectedVariable}
                                    onChange={(value) => onUnsetVariableChange(index, value)}
                                    options={unsetVariableOptions}
                                    readOnly={readOnly}
                                    placeholder={"#someVar"}
                                    fieldErrors={getValidationErrorsForField(errors, `$fields-${index}-$key`)}
                                />
                            </FieldsRow>
                        );
                    })}
                </NodeRowFieldsProvider>
            )}
            <DescriptionField
                isEditMode={!readOnly}
                showValidation={showValidation}
                node={node}
                setProperty={setProperty}
                errors={errors}
            />
            {mapperOpen && (
                <Dialog open onClose={() => setMapperOpen(false)} maxWidth="xl" fullWidth>
                    <DataMapperDialogTitle node={node} onClose={() => setMapperOpen(false)} />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <DataMapper
                            onInsert={handleInsert}
                            initialContext={dataMapperContext}
                            initialExpression={node.value?.language === ExpressionLang.SpEL ? node.value?.expression : undefined}
                            variableTypes={variableTypes}
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
