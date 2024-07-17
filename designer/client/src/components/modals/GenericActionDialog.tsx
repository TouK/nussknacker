import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { getProcessName } from "../../reducers/selectors/graph";
import { Expression, NodeValidationError, UIParameter, VariableTypes } from "../../types";
import { WindowContent, WindowKind } from "../../windowManager";
import { editors, ExtendedEditor, SimpleEditor } from "../graph/node-modal/editors/expression/Editor";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import { ContentSize } from "../graph/node-modal/node/ContentSize";
import { ParamFieldLabel } from "../graph/node-modal/FieldLabel";
import { validateGenericActionParameters } from "../../actions/nk/genericAction";
import { getGenericActionValidation } from "../../reducers/selectors/genericActionState";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import { spelFormatters } from "../graph/node-modal/editors/expression/Formatter";
import { isEmpty } from "lodash";
import { getValidationErrorsForField } from "../graph/node-modal/editors/Validators";
import { FormControl } from "@mui/material";
import ErrorBoundary from "../common/ErrorBoundary";
import { ButtonsVariant } from "../toolbarComponents/toolbarButtons";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { nodeValue } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";

export type GenericActionLayout = {
    name: string;
    icon?: string;
    confirmText?: string;
    cancelText?: string;
};

export interface GenericActionParameters {
    parameters?: UIParameter[];
    parametersValues: { [key: string]: Expression };
    onParamUpdate?: (name: string) => (value: any) => void;
}

interface GenericAction extends GenericActionParameters {
    layout: GenericActionLayout;
    variableTypes: VariableTypes;
    onConfirmAction: (parmValues) => void;
    processingType: string;
}

interface GenericActionDialogProps {
    action: GenericAction;
    errors: NodeValidationError[];
    value: { [p: string]: Expression };
    setValue: (value: ((prevState: { [p: string]: Expression }) => { [p: string]: Expression }) | { [p: string]: Expression }) => void;
}

function GenericActionForm(props: GenericActionDialogProps): JSX.Element {
    const { value, setValue, action, errors } = props;
    const dispatch = useDispatch();
    const setParam = useCallback(
        (name: string) => (value: any) => {
            action.onParamUpdate(name)(value);
            setValue((current) => ({ ...current, [name]: { expression: value, language: current[name].language } }));
        },
        [dispatch, value],
    );

    useEffect(() => {
        dispatch(
            validateGenericActionParameters(action.processingType, {
                parameters: action.parameters.map((uiParam) => {
                    return {
                        name: uiParam.name,
                        typ: uiParam.typ,
                        expression: value[uiParam.name],
                    };
                }),
                variableTypes: action.variableTypes,
            }),
        );
    }, [value]);

    useEffect(() => setValue(value), [setValue, value]);
    return (
        <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
            <ContentSize>
                <NodeTable>
                    {(action?.parameters || []).map((param) => {
                        const Editor: SimpleEditor | ExtendedEditor = editors[param.editor.type];
                        const fieldName = param.name;
                        const formatter =
                            param.defaultValue.language === ExpressionLang.SpEL ? spelFormatters[param?.typ?.refClazzName] : null;
                        return (
                            <FormControl key={param.name}>
                                <ParamFieldLabel parameterDefinitions={action.parameters} paramName={param.name} />
                                <ErrorBoundary>
                                    <Editor
                                        editorConfig={param?.editor}
                                        className={nodeValue}
                                        fieldErrors={getValidationErrorsForField(errors, fieldName)}
                                        formatter={formatter}
                                        expressionInfo={null}
                                        onValueChange={setParam(fieldName)}
                                        expressionObj={value[fieldName]}
                                        readOnly={false}
                                        key={fieldName}
                                        showSwitch={true}
                                        showValidation={true}
                                        variableTypes={action.variableTypes}
                                    />
                                </ErrorBoundary>
                            </FormControl>
                        );
                    })}
                </NodeTable>
            </ContentSize>
        </div>
    );
}

export function GenericActionDialog(props: WindowContentProps<WindowKind, GenericAction>): JSX.Element {
    const processName = useSelector(getProcessName);
    const { t } = useTranslation();
    const action = props.data.meta;
    const [value, setValue] = useState(() =>
        (action?.parameters || []).reduce(
            (obj, param) => ({
                ...obj,
                [param.name]: action.parametersValues[param.name],
            }),
            {},
        ),
    );
    const { validationErrors } = useSelector(getGenericActionValidation);
    const isValid = isEmpty(validationErrors);

    const confirm = useCallback(async () => {
        action.onConfirmAction(value);
        props.close();
    }, [processName, action.layout.name, value, props]);

    const cancelText = action.layout.cancelText ? action.layout.cancelText : "cancel";
    const confirmText = action.layout.confirmText ? action.layout.confirmText : "confirm";
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            {
                title: t(`dialog.generic.button.${cancelText}`, cancelText),
                action: () => props.close(),
                classname: LoadingButtonTypes.secondaryButton,
            },
            { title: t(`dialog.generic.button.${confirmText}`, confirmText), action: () => confirm(), disabled: !isValid },
        ],
        [cancelText, confirm, confirmText, isValid, props, t],
    );

    return (
        <WindowContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ padding: "1em", minWidth: 600 }))}>
                <GenericActionForm action={action} errors={validationErrors} value={value} setValue={setValue} />
            </div>
        </WindowContent>
    );
}

export default GenericActionDialog;
