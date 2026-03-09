import { Box, Dialog, DialogContent } from "@mui/material";
import React, { useCallback, useRef, useState } from "react";

import ProcessUtils from "../../../common/ProcessUtils";
import { useUserSettings } from "../../../common/useUserSettings";
import HttpService from "../../../http/HttpService/instance";
import type { RootState } from "../../../reducers";
import { getProcessingType } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { TypedObjectTypingResult, TypingInfo, TypingResult } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
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
import { FieldAddons } from "./fieldAddons";
import { IdField } from "./IdField";
import { StyledLoadingButton } from "./node-action-buttons/StyledLoadingButton";
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

function getTypingResult(expressionType: TypingResult, nodeTypingInfo: TypingInfo): TypedObjectTypingResult | TypingResult {
    return expressionType || nodeTypingInfo?.[DEFAULT_EXPRESSION_ID];
}

interface Props {
    isEditMode?: boolean;
    node: NodeType;
    setProperty: SetProperty;
    showValidation: boolean;
    errors: NodeValidationError[];
    showSwitch?: boolean;
    variableTypes: VariableTypes;
}

export default function Variable({ node, setProperty, isEditMode, showValidation, errors, variableTypes }: Props): React.JSX.Element {
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
    const [showDataMapper] = useUserSettings("node.showDataMapper");
    const [dataMapperContext, setDataMapperContext] = useState<ContextData>({});
    const contextFetched = useRef(false);

    const handleInsert = useCallback(
        (spel: string) => {
            onExpressionChange({ expression: spel, language: ExpressionLang.SpEL });
            setMapperOpen(false);
        },
        [onExpressionChange],
    );

    const handleOpenMapper = useCallback(async () => {
        if (!contextFetched.current) {
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
    }, [processingType, variableTypes]);

    return (
        <>
            <IdField node={node} isEditMode={isEditMode} showValidation={showValidation} setProperty={setProperty} errors={errors} />
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
            />
            {isEditMode && showDataMapper && (
                <FieldAddons hasError={showValidation && getValidationErrorsForField(errors, "$expression").length > 0}>
                    <Box display="flex" flexDirection="column" alignItems="flex-end" width="100%">
                        <StyledLoadingButton title="Data Mapper" action={handleOpenMapper} />
                    </Box>
                </FieldAddons>
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
