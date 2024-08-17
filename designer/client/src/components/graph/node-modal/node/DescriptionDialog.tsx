import { css } from "@emotion/css";
import { Edit } from "@mui/icons-material";
import { DefaultComponents, WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { get } from "lodash";
import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { NodeType } from "../../../../types";
import { WindowContent, WindowKind } from "../../../../windowManager";
import { LoadingButtonTypes } from "../../../../windowManager/LoadingButton";
import ErrorBoundary from "../../../common/ErrorBoundary";
import { Scenario } from "../../../Process/types";
import { DescriptionOnlyContent } from "../DescriptionOnlyContent";
import { useNodeDetailsButtons, useNodeState } from "./NodeDetails";
import { getReadOnly } from "./selectors";
import { StyledHeader } from "./StyledHeader";

interface DescriptionDialogProps extends WindowContentProps<WindowKind, { node: NodeType; scenario: Scenario }> {
    editMode?: boolean;
}

function DescriptionDialog(props: DescriptionDialogProps): JSX.Element {
    const { t } = useTranslation();
    const { editMode, close, data } = props;
    const readOnly = useSelector(getReadOnly);

    const [previewMode, setPreviewMode] = useState(!editMode || readOnly);

    const { node, editedNode, onChange, isTouched, performNodeEdit } = useNodeState(data.meta);
    const { cancel: _cancel, apply: _apply } = useNodeDetailsButtons({ editedNode, performNodeEdit, close, readOnly });

    const fieldPath = "additionalFields.description";

    const apply = useMemo<WindowButtonProps | false>(() => {
        if (previewMode && !isTouched) return false;
        return _apply;
    }, [_apply, previewMode, isTouched]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        if (previewMode && !isTouched) return false;
        if (!_cancel || !get(node, fieldPath)) return _cancel;
        return {
            ..._cancel,
            action: () => {
                onChange(node);
                setPreviewMode(true);
            },
        };
    }, [previewMode, isTouched, _cancel, onChange, node]);

    const preview = useMemo<WindowButtonProps | false>(() => {
        if (!isTouched) return false;
        return {
            title: previewMode ? t("dialog.button.edit", "edit") : t("dialog.button.preview", "preview"),
            action: () => setPreviewMode((v) => !v),
            className: LoadingButtonTypes.tertiaryButton,
        };
    }, [previewMode, t, isTouched]);

    const componentsOverride = useMemo<Partial<typeof DefaultComponents>>(() => {
        const HeaderTitle = () => <div />;

        if (isTouched || !previewMode) {
            return {};
        }

        const Header = (props) => (
            <StyledHeader
                {...props}
                sx={{
                    fontSize: ".75em",
                    backgroundColor: "transparent",
                    "&:hover, &:active": { backgroundColor: "var(--backgroundColor)" },
                }}
            />
        );
        const HeaderButtonZoom = (props) => (
            <>
                <DefaultComponents.HeaderButton action={() => setPreviewMode(false)} name="edit">
                    <Edit
                        sx={{
                            fontSize: "inherit",
                            width: "unset",
                            height: "unset",
                            padding: ".25em",
                        }}
                    />
                </DefaultComponents.HeaderButton>
                <DefaultComponents.HeaderButtonZoom {...props} />
            </>
        );

        return { Header, HeaderTitle, HeaderButtonZoom };
    }, [isTouched, previewMode]);

    return (
        <WindowContent
            {...props}
            closeWithEsc
            buttons={[preview, cancel, apply]}
            classnames={{
                content: css({ minHeight: "100%", display: "flex", ">div": { flex: 1 }, position: "relative" }),
            }}
            components={componentsOverride}
        >
            <ErrorBoundary>
                <DescriptionOnlyContent fieldPath={fieldPath} node={editedNode} onChange={!readOnly && onChange} preview={previewMode} />
            </ErrorBoundary>
        </WindowContent>
    );
}

export default DescriptionDialog;
