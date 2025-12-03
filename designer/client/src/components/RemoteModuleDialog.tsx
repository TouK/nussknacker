import type { ModuleUrl } from "@touk/federated-component";
import type { WindowContentProps } from "@touk/window-manager";
import type { FooterButtonProps } from "@touk/window-manager/cjs/components/window/footer";
import React, { useCallback, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";

import { LoadingButtonTypes } from "../windowManager/LoadingButton";
import { WindowContent } from "../windowManager/WindowContent";
import type { WindowKind } from "../windowManager/WindowKind";
import { RemoteComponent } from "./RemoteComponent";

export type RemoteModuleDialogProps = NonNullable<unknown>;
export type RemoteModuleDialogRef = NonNullable<{
    closeAction?: () => Promise<void>;
    adjustButtons: (buttons: { closeButton: FooterButtonProps; confirmButton: FooterButtonProps }) => FooterButtonProps[];
}>;

export function RemoteModuleDialog<P extends NonNullable<unknown>>({
    close,
    ...props
}: WindowContentProps<WindowKind, { url: ModuleUrl } & P>): React.JSX.Element {
    const {
        data: { meta: passProps },
    } = props;

    const ref = useRef<RemoteModuleDialogRef>(null);

    const closeAction = useCallback(async () => {
        await Promise.all([ref.current?.closeAction?.()]);
        close();
    }, [close]);

    const { t } = useTranslation();

    const closeButton = useMemo<FooterButtonProps>(
        () => ({
            title: t("dialog.button.cancel", "cancel"),
            action: closeAction,
            className: LoadingButtonTypes.secondaryButton,
        }),
        [closeAction, t],
    );

    const confirmButton = useMemo<FooterButtonProps>(
        () => ({
            title: t("dialog.button.ok", "OK"),
            action: closeAction,
        }),
        [closeAction, t],
    );

    return (
        <WindowContent
            {...props}
            close={close}
            buttons={ref.current?.adjustButtons({ closeButton, confirmButton }) || [closeButton, confirmButton]}
        >
            <RemoteComponent<RemoteModuleDialogProps, RemoteModuleDialogRef> ref={ref} {...passProps} />
        </WindowContent>
    );
}

export default RemoteModuleDialog;
