import React, { PropsWithChildren } from "react";
import { I18nextProvider } from "react-i18next";
import { QueryClientProvider } from "react-query";
import i18n from "../i18n";
import { NkApiProvider } from "../settings";
import { queryClient } from "../store";

export function DefaultProviders({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        // @see https://github.com/i18next/react-i18next/issues/1379
        <I18nextProvider i18n={i18n as any}>
            <QueryClientProvider client={queryClient}>
                <NkApiProvider>{children}</NkApiProvider>
            </QueryClientProvider>
        </I18nextProvider>
    );
}
