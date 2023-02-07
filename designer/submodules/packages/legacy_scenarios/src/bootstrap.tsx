import React, { Suspense } from "react";
import ReactDOM from "react-dom";
import ErrorBoundary from "./components/common/ErrorBoundary";
import LoaderSpinner from "./components/Spinner";
import { NkThemeProvider } from "./containers/theme";
import "./i18n";
import ProcessTabs from "./containers/ProcessTabs";
import { NkApiProvider } from "./settings/nkApiProvider";
import { Auth } from "./settings/auth";
import { QueryClient, QueryClientProvider } from "react-query";

const queryClient = new QueryClient();

const rootContainer = document.createElement(`div`);
rootContainer.id = "root";
document.body.appendChild(rootContainer);

const Root = () => (
    <Suspense fallback={<LoaderSpinner show />}>
        <ErrorBoundary>
            <NkApiProvider>
                <QueryClientProvider client={queryClient}>
                    <Auth>
                        <NkThemeProvider>
                            <ProcessTabs
                                onFragmentAdd={() => alert("fragment")}
                                onScenarioAdd={() => alert("scenario")}
                                metricsLinkGetter={(id) => `/metrics/${encodeURIComponent(id)}`}
                                scenarioLinkGetter={(id) => `/visualization/${encodeURIComponent(id)}`}
                            />
                        </NkThemeProvider>
                    </Auth>
                </QueryClientProvider>
            </NkApiProvider>
        </ErrorBoundary>
    </Suspense>
);

ReactDOM.render(<Root />, rootContainer);
