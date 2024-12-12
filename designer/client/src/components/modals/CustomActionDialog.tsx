import { css, cx } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { loadProcessState } from "../../actions/nk";
import HttpService, { CustomActionValidationRequest } from "../../http/HttpService";
import { CustomAction, NodeValidationError } from "../../types";
import { UnknownRecord } from "../../types/common";
import { WindowContent, WindowKind } from "../../windowManager";
import { ChangeableValue } from "../ChangeableValue";
import { editors, ExtendedEditor, SimpleEditor } from "../graph/node-modal/editors/expression/Editor";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import { FormControl, FormHelperText, FormLabel } from "@mui/material";
import { getProcessName } from "../graph/node-modal/NodeDetailsContent/selectors";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { nodeValue } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { getValidationErrorsForField } from "../graph/node-modal/editors/Validators";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import CommentInput from "../comment/CommentInput";
import { getProcessVersionId } from "../../reducers/selectors/graph";

interface CustomActionFormProps extends ChangeableValue<UnknownRecord> {
    action: CustomAction;
}

function CustomActionForm(props: CustomActionFormProps): JSX.Element {
    const { onChange, action } = props;

    const [state, setState] = useState(() =>
        (action?.parameters || []).reduce(
            (obj, param) => ({
                ...obj,
                [param.name]: "",
            }),
            {},
        ),
    );

    const setParam = useCallback((name: string) => (value) => setState((current) => ({ ...current, [name]: value })), []);

    useEffect(() => onChange(state), [onChange, state]);

    const [errors, setErrors] = useState<NodeValidationError[]>([]);

    const processName = useSelector(getProcessName);
    const validationRequest: CustomActionValidationRequest = useMemo(() => {
        return {
            actionName: action.name,
            params: state,
        };
    }, [action, state]);

    useEffect(() => {
        const timer = setTimeout(async () => {
            const data = await HttpService.validateCustomAction(processName, validationRequest);
            setErrors(data.validationErrors);
        }, 400);

        return () => {
            clearTimeout(timer);
        };
    }, [processName, validationRequest]);

    return (
        <NodeTable>
            {(action?.parameters || []).map((param) => {
                const editorType = param.editor.type;
                const Editor: SimpleEditor | ExtendedEditor = editors[editorType];
                const fieldName = param.name;

                return (
                    <FormControl key={param.name}>
                        <FormLabel title={fieldName}>{fieldName}:</FormLabel>
                        <Editor
                            editorConfig={param?.editor}
                            className={nodeValue}
                            fieldErrors={getValidationErrorsForField(errors, param.name)}
                            formatter={null}
                            expressionInfo={null}
                            onValueChange={setParam(fieldName)}
                            expressionObj={{ language: ExpressionLang.String, expression: state[fieldName] }}
                            readOnly={false}
                            key={fieldName}
                            showSwitch={false}
                            showValidation={true}
                            variableTypes={{}}
                        />
                    </FormControl>
                );
            })}
        </NodeTable>
    );
}

export function CustomActionDialog(props: WindowContentProps<WindowKind, CustomAction>): JSX.Element {
    const processName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const dispatch = useDispatch();
    const action = props.data.meta;
    const [validationError, setValidationError] = useState("");

    // TODO: Further changes:
    //  - rename deploymentCommentSettings (see also DeploymentComment, it serves as a comment validator for all actions)
    //  - provide better dialog for actions (e.g. GenericActionDialog) with action comment and action parameters
    const [comment, setComment] = useState("");
    const featureSettings = useSelector(getFeatureSettings);
    const deploymentCommentSettings = featureSettings.deploymentCommentSettings;

    const [value, setValue] = useState<UnknownRecord>();

    const confirmAction = useCallback(async () => {
        await HttpService.customAction(processName, action.name, value, comment).then((response) => {
            if (response.isSuccess) {
                dispatch(loadProcessState(processName, processVersionId));
                props.close();
            } else {
                setValidationError(response.msg);
            }
        });
    }, [processName, action.name, value, props, dispatch]);

    const { t } = useTranslation();
    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("dialog.button.confirm", "Ok"), action: () => confirmAction() },
        ],
        [confirmAction, props, t],
    );

    return (
        <WindowContent {...props} buttons={buttons}>
            <div className={cx("modalContentDark", css({ padding: "1em", minWidth: 600 }))}>
                <CommentInput
                    onChange={(e) => setComment(e.target.value)}
                    value={comment}
                    defaultValue={deploymentCommentSettings?.exampleComment}
                    className={cx(
                        css({
                            minWidth: 600,
                            minHeight: 80,
                        }),
                    )}
                    autoFocus
                />
                <FormHelperText title={validationError} error>
                    {validationError}
                </FormHelperText>
                <CustomActionForm action={action} value={value} onChange={setValue} />
            </div>
        </WindowContent>
    );
}

export default CustomActionDialog;
