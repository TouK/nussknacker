import type { AxiosResponse } from "axios";
import type { JsonWebTokenError } from "jsonwebtoken";
import { verify } from "jsonwebtoken";
import { nanoid } from "nanoid";
import { parse } from "query-string";

import SystemUtils from "../../../common/SystemUtils";
import { BASE_PATH } from "../../../config";
import HttpService from "../../../http/HttpService/instance";
import type { OAuth2Settings } from "../../../reducers/settings";
import { AuthErrorCodes } from "../AuthErrorCodes";
import type { Strategy, StrategyConstructor } from "../Strategy";

export const OAuth2Strategy: StrategyConstructor = class OAuth2Strategy implements Strategy {
    private onError?: (error: AuthErrorCodes) => void;
    private readonly redirectUri: string | null; // null means it's been configured in the BE and it's not evaluated here
    private readonly authorizeUrl: URL;

    constructor(private settings: OAuth2Settings, onError?: (error: AuthErrorCodes) => void) {
        this.onError = onError;
        this.authorizeUrl = new URL(this.settings.authorizeUrl);
        if (this.authorizeUrl.searchParams.has("redirect_uri")) {
            this.redirectUri = null;
        } else {
            this.redirectUri = window.location.origin + BASE_PATH;
            this.authorizeUrl.searchParams.append("redirect_uri", this.redirectUri);
        }
    }

    redirectToAuthorizeUrl(): void {
        SystemUtils.saveNonce(nanoid());
        window.location.replace(`${this.authorizeUrl.toString()}&nonce=${SystemUtils.getNonce()}`);
    }

    inteceptor(error?: { response?: { status?: AuthErrorCodes } }): Promise<unknown> {
        if (error?.response?.status === AuthErrorCodes.HTTP_UNAUTHORIZED_CODE) {
            if (SystemUtils.hasAccessToken()) {
                // this is needed to avoid errors on stale token
                SystemUtils.clearAuthorizationToken();
            }
            this.redirectToAuthorizeUrl();
        }
        return Promise.resolve();
    }

    handleAuth(): Promise<AxiosResponse<unknown> | void> {
        const { hash, search } = window.location;
        const queryParams = parse(search);
        if (queryParams.code) {
            return HttpService.fetchOAuth2AccessToken<{ accessToken: string }>(this.settings.provider, queryParams.code, this.redirectUri)
                .then((response) => {
                    SystemUtils.setAuthorizationToken(response.data.accessToken);
                    return response;
                })
                .catch((error) => {
                    this.onError(AuthErrorCodes.ACCESS_TOKEN_CODE);
                    return Promise.reject(error);
                })
                .finally(() => {
                    history.replaceState(null, "", "?");
                });
        }

        const queryHashParams = parse(hash);
        if (queryHashParams.access_token && this.settings.implicitGrantEnabled) {
            if (!this.verifyTokens(queryHashParams)) {
                return Promise.reject("token not verified");
            } else {
                SystemUtils.setAuthorizationToken(queryHashParams.access_token.toString());
                history.replaceState(null, "", "#");
            }
        }

        if (SystemUtils.hasAccessToken() || this.settings.anonymousAccessAllowed) {
            return Promise.resolve();
        }

        if (queryParams.error == "invalid_request") {
            // We don't redirect in this case because it will cause redirection loop
            console.warn(
                "OAuth2 authentication server redirected with 'invalid_request' error code. It can be something wrong in authentication configuration or some error on authentication server side.",
            );
        } else {
            this.redirectToAuthorizeUrl();
        }
        return Promise.reject();
    }

    setOnErrorCallback(callback: (error: AuthErrorCodes) => void): void {
        this.onError = callback;
    }

    private verifyTokens(queryHashParams): boolean {
        if (this.settings.jwtAuthServerPublicKey) {
            const verifyAccessToken = () => {
                try {
                    return verify(queryHashParams.access_token, this.settings.jwtAuthServerPublicKey) !== null;
                } catch (error) {
                    this.handleJwtError(error);
                    return false;
                }
            };
            const accessTokenVerificationResult = verifyAccessToken();
            if (accessTokenVerificationResult) {
                if (queryHashParams.id_token) {
                    try {
                        return (
                            verify(queryHashParams.id_token, this.settings.jwtAuthServerPublicKey, {
                                nonce: SystemUtils.getNonce(),
                            }) !== null
                        );
                    } catch (error) {
                        this.handleJwtError(error);
                        return false;
                    }
                } else if (this.settings.jwtIdTokenNonceVerificationRequired === true) {
                    // eslint-disable-next-line i18next/no-literal-string
                    console.warn("jwt.idTokenNonceVerificationRequired=true but id_token missing in the auth server response");
                    this.onError(AuthErrorCodes.ACCESS_TOKEN_CODE);
                    return false;
                }
            }
            return accessTokenVerificationResult;
        } else {
            return true;
        }
    }

    private handleJwtError(error: JsonWebTokenError) {
        if (error.name === "TokenExpiredError") {
            this.redirectToAuthorizeUrl();
        } else {
            this.onError(AuthErrorCodes.ACCESS_TOKEN_CODE);
            return Promise.reject();
        }
    }
};
