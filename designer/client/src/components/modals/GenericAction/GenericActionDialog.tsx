import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { ElementType, ReactElement, useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { UIParameter, VariableTypes } from "../../../types";
import { WindowContent, WindowKind } from "../../../windowManager";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../../graph/node-modal/nodeDetails/SubHeader";
import { MarkdownForm } from "./MarkdownForm";
import { isEmpty, omitBy } from "lodash";
import { ActionValues, GenericActionFormContext } from "./GenericActionFormContext";
import { Box } from "@mui/material";
import { useGenericActionValidation } from "./useGenericActionValidation";

type DocsLink = {
    url: string;
    label?: string;
};

export type GenericActionViewParams = {
    confirmText?: string;
    cancelText?: string;
    Icon?: ElementType;
    docs?: DocsLink;
    // may contain a <Fields/> or <Field name=""> components
    markdownContent?: string;
};

export interface GenericActionParameters {
    parameters: UIParameter[];
}

export interface GenericAction extends GenericActionParameters {
    variableTypes: VariableTypes;
    processingType: string;
    initialValues: ActionValues;
    onConfirmAction: (values: ActionValues) => void;
}

export interface GenericActionData {
    view: GenericActionViewParams;
    action: GenericAction;
}

function omitEmpty(value: ActionValues) {
    return omitBy(value, ({ expression }) => {
        return isEmpty(expression);
    });
}

function GenericActionDialog(props: WindowContentProps<WindowKind, GenericActionData>): ReactElement {
    const { t } = useTranslation();
    const { data, close } = props;
    const {
        meta: { action, view },
        kind,
    } = data;
    const { variableTypes, parameters = [], initialValues, onConfirmAction } = action;

    const [value, setValue] = useState(initialValues);
    const { errors, isValid } = useGenericActionValidation(action, value);

    const confirm = useCallback(async () => {
        const values = omitEmpty(value);
        onConfirmAction(values);
        close();
    }, [close, onConfirmAction, value]);

    const buttons: WindowButtonProps[] = useMemo(() => {
        const cancelText = view.cancelText ? view.cancelText : "cancel";
        const confirmText = view.confirmText ? view.confirmText : "confirm";
        return [
            {
                title: t(`dialog.generic.button.${cancelText}`, cancelText),
                action: () => close(),
                classname: LoadingButtonTypes.secondaryButton,
            },
            {
                title: t(`dialog.generic.button.${confirmText}`, confirmText),
                action: () => confirm(),
                disabled: !isValid,
            },
        ];
    }, [close, confirm, isValid, view.cancelText, view.confirmText, t]);

    return (
        <WindowContent
            {...props}
            buttons={buttons}
            icon={<WindowHeaderIconStyled as={view.Icon} type={kind} />}
            subheader={<NodeDocs name={view.docs?.label} href={view.docs?.url} />}
        >
            <ContentSize>
                <Box mx={3}>
                    <GenericActionFormContext.Provider
                        value={{
                            value,
                            setValue,
                            parameters,
                            variableTypes,
                            errors,
                        }}
                    >
                        <MarkdownForm content={view.markdownContent} />
                    </GenericActionFormContext.Provider>
                </Box>
            </ContentSize>
        </WindowContent>
    );
}

export default GenericActionDialog;
