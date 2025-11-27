import type { PropsWithChildren } from "react";
import React from "react";
import { I18nextProvider } from "react-i18next";

import i18n from "../../i18n";
import { InitializeError } from "../errors/InitializeError";
import type { AuthErrorCodes } from "./AuthErrorCodes";

export interface InitErrorComponentProps extends PropsWithChildren<unknown> {
    error: AuthErrorCodes;
    retry: () => void;
}

export function InitErrorComponent(props: InitErrorComponentProps): React.JSX.Element {
    return (
        <I18nextProvider i18n={i18n}>
            <InitializeError {...props} />
        </I18nextProvider>
    );
}
