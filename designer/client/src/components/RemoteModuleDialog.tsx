import { ModuleUrl } from "@touk/federated-component";
import { WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import { WindowContent, WindowKind } from "../windowManager";
import { LoadingButtonTypes } from "../windowManager/LoadingButton";
import { RemoteComponent } from "./RemoteComponent";

export type RemoteModuleDialogProps = NonNullable<unknown>;
export type RemoteModuleDialogRef = NonNullable<{
    closeAction?: () => Promise<void>;
    confirmAction?: () => Promise<void>;
}>;

export function RemoteModuleDialog<P extends NonNullable<unknown>>(
    props: WindowContentProps<
        WindowKind,
        {
            url: ModuleUrl;
        } & P
    >,
): JSX.Element {
    const {
        data: { meta: passProps },
    } = props;

    const ref = useRef<RemoteModuleDialogRef>();

    const close = useCallback(() => {
        Promise.all([ref.current?.closeAction?.()]).then(() => {
            props.close();
        });
    }, [props]);

    const { t } = useTranslation();

    const buttons = useMemo(
        () => [
            {
                title: t("dialog.button.cancel", "cancel"),
                action: () => {
                    close();
                },
                className: LoadingButtonTypes.secondaryButton,
            },
            {
                title: t("dialog.button.ok", "OK"),
                action: () => {
                    Promise.all([ref.current?.confirmAction?.()]).then(() => {
                        props.close();
                    });
                },
                disabled: !ref.current?.confirmAction,
            },
        ],
        [close, props, t],
    );

    return (
        <WindowContent {...props} close={close} buttons={buttons}>
            <RemoteComponent<RemoteModuleDialogProps, RemoteModuleDialogRef> ref={ref} {...passProps} />
        </WindowContent>
    );
}

export default RemoteModuleDialog;
