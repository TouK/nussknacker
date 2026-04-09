import React, { useCallback, useMemo } from "react";

import type { TypingResult, UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import { makeField, parseSpelToFields, refClazzToNuType, genSpelFromFields } from "../../dataMapper/dataMapperUtils";
import type { FieldDef, NuType } from "../../dataMapper/dataMapperUtils";
import { DataMapperComponent } from "./DataMapperComponent";
import { EditorType, ExpressionLang } from "./editors/expression/types";
import { findParameters } from "./NodeDetailsContent/helpers";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

function resolveParamNuType(typ: UIParameter["typ"]): NuType {
    if ("union" in typ && typ.union?.length) {
        const types = new Set(typ.union.map((u) => refClazzToNuType(u.refClazzName)));
        if (types.size === 1) return [...types][0] as NuType;
    }
    return refClazzToNuType(typ?.refClazzName);
}

function getTypFields(typ: TypingResult): Record<string, TypingResult> | null {
    if ("fields" in typ && typ.fields && typeof typ.fields === "object") {
        return typ.fields as Record<string, TypingResult>;
    }
    return null;
}

/** Builds a FieldDef leaf, expanding Record types into isRecord children. */
function buildLeafField(name: string, typ: TypingResult, expression: string): FieldDef {
    const recordFields = getTypFields(typ);
    if (recordFields) {
        const field = makeField(name, "Map");
        field.isRecord = true;
        const existingChildren = expression ? parseSpelToFields(expression) ?? [] : [];
        field.children = Object.entries(recordFields).map(([childName, childTyp]) => {
            const childField = makeField(childName, refClazzToNuType((childTyp as { refClazzName?: string }).refClazzName));
            const existing = existingChildren.find((c) => c.name === childName);
            childField.expression = existing?.expression ?? "";
            return childField;
        });
        return field;
    }
    const field = makeField(name, resolveParamNuType(typ));
    field.expression = expression;
    return field;
}

/** Inserts a leaf parameter into a nested FieldDef tree at the given remaining path segments. */
function insertNestedParam(parent: FieldDef, segments: string[], typ: TypingResult, expression: string): void {
    const [first, ...rest] = segments;
    if (rest.length === 0) {
        parent.children.push(buildLeafField(first, typ, expression));
        return;
    }
    let intermediate = parent.children.find((c) => c.name === first);
    if (!intermediate) {
        intermediate = makeField(first, "Map");
        intermediate.isRecord = true;
        parent.children.push(intermediate);
    }
    insertNestedParam(intermediate, rest, typ, expression);
}

/**
 * Groups flat params (possibly with dotted names like "combinedState.tool_record") into a nested
 * FieldDef tree. Parameters with a Record type are expanded into children using typ.fields.
 */
function groupByDottedPrefix(params: UIParameter[], currentParams: ReturnType<typeof findParameters>): FieldDef[] {
    const rootMap = new Map<string, FieldDef>();

    for (const p of params) {
        const expression = currentParams.find((np) => np.name === p.name)?.expression?.expression?.trim() ?? "";
        const segments = p.name.split(".");

        if (segments.length === 1) {
            rootMap.set(p.name, buildLeafField(p.name, p.typ, expression));
        } else {
            const [first, ...rest] = segments;
            if (!rootMap.has(first)) {
                const intermediate = makeField(first, "Map");
                intermediate.isRecord = true;
                rootMap.set(first, intermediate);
            }
            insertNestedParam(rootMap.get(first)!, rest, p.typ, expression);
        }
    }

    return Array.from(rootMap.values());
}

/**
 * Flattens a nested FieldDef tree back to a map of { dottedParamName → expression }.
 * When a node's dotted path matches a known parameter, its expression (or its children's
 * SpEL record) is collected. Intermediate nodes without a matching parameter are traversed.
 */
function flattenToParams(fields: FieldDef[], knownParamNames: Set<string>, prefix = ""): Map<string, string> {
    const result = new Map<string, string>();
    for (const f of fields) {
        const key = prefix ? `${prefix}.${f.name}` : f.name;
        if (knownParamNames.has(key)) {
            let expr: string;
            if (f.isRecord) {
                expr = genSpelFromFields(f.children);
            } else {
                expr = f.defaultValue !== undefined ? `${f.expression} ?: ${f.defaultValue}` : f.expression;
            }
            result.set(key, expr);
        } else if (f.isRecord && f.children.length > 0) {
            const nested = flattenToParams(f.children, knownParamNames, key);
            nested.forEach((v, k) => result.set(k, v));
        }
    }
    return result;
}

interface Props {
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setProperty: SetProperty;
    open: boolean;
    focusFieldName?: string;
    onClose: () => void;
}

export function NamedParamsDataMapper({
    node,
    parameterDefinitions,
    setProperty,
    open,
    focusFieldName,
    onClose,
}: Props): React.JSX.Element | null {
    const parametersBasePath = node.type === "Sink" ? "ref.parameters" : "service.parameters";
    const mappableParams = useMemo(
        () =>
            parameterDefinitions.filter(
                (p) => !p.changesCanReloadParameters && p.editors?.some((e) => e.type === EditorType.SPEL_PARAMETER_EDITOR),
            ),
        [parameterDefinitions],
    );

    const initialFields = useMemo(() => {
        if (mappableParams.length === 0) return undefined;
        const currentParams = findParameters(node);
        return groupByDottedPrefix(mappableParams, currentParams);
    }, [mappableParams, node]);

    const handleInsert = useCallback(
        (spel: string) => {
            const parsed = parseSpelToFields(spel);
            if (!parsed) return;
            const currentParams = findParameters(node);
            const knownParamNames = new Set(mappableParams.map((p) => p.name));
            const exprByParam = flattenToParams(parsed, knownParamNames);
            for (const [paramName, expr] of exprByParam) {
                if (!expr) continue;
                const paramIndex = currentParams.findIndex((p) => p.name === paramName);
                if (paramIndex < 0) continue;
                setProperty(`${parametersBasePath}[${paramIndex}].expression`, {
                    expression: expr,
                    language: ExpressionLang.SpEL,
                });
            }
        },
        [node, mappableParams, parametersBasePath, setProperty],
    );

    if (mappableParams.length === 0) return null;

    return (
        <DataMapperComponent
            node={node}
            onInsert={handleInsert}
            initialFields={initialFields}
            initialFocusFieldName={focusFieldName}
            hideFieldControls
            open={open}
            onClose={onClose}
        />
    );
}
