/* eslint-disable i18next/no-literal-string */
import { v4 as uuid4 } from "uuid";
import api from "../api";
import { isEmpty, set } from "lodash";
import { PendingPromise } from "./PendingPromise";

class SystemUtils {
    public static AUTHORIZATION_HEADER_NAMESPACE = "Authorization";
    public static ACCESS_TOKEN_NAMESPACE = "accessToken";
    public static USER_ID_NAMESPACE = "userId";
    public static BEARER_CASE = "Bearer";
    public static NONCE = "nonce";

    #tokenPromise: PendingPromise<string> | null;
    get tokenPromise(): PendingPromise<string> {
        if (!this.#tokenPromise) {
            this.#tokenPromise = new PendingPromise<string>();
        }
        return this.#tokenPromise;
    }

    public asyncAuthorizationToken = (): Promise<string> => this.tokenPromise.then(this.authorizationToken);
    public authorizationToken = (): string => `${SystemUtils.BEARER_CASE} ${this.getAccessToken()}`;

    public saveAccessToken = (token: string): void => {
        localStorage.setItem(SystemUtils.ACCESS_TOKEN_NAMESPACE, token);
    };

    public getAccessToken = (): string | null => {
        const token = localStorage.getItem(SystemUtils.ACCESS_TOKEN_NAMESPACE);
        if (token) {
            this.tokenPromise.resolve(token);
        }
        return token;
    };

    public hasAccessToken = (): boolean => this.getAccessToken() !== null;

    public removeAccessToken = () => localStorage.removeItem(SystemUtils.ACCESS_TOKEN_NAMESPACE);

    public saveNonce = (nonce: string): void => localStorage.setItem(SystemUtils.NONCE, nonce);

    public getNonce = (): string => localStorage.getItem(SystemUtils.NONCE);

    public clearAuthorizationToken = (): void => {
        this.#tokenPromise = null;

        api.interceptors.request.use((config) => {
            delete config.headers[SystemUtils.AUTHORIZATION_HEADER_NAMESPACE];
            return config;
        });

        return this.removeAccessToken();
    };

    public setAuthorizationToken = (token: string): void => {
        if (token) {
            this.tokenPromise.resolve(token);
        }

        api.interceptors.request.use((config) => {
            set(config.headers, SystemUtils.AUTHORIZATION_HEADER_NAMESPACE, this.authorizationToken());
            return config;
        });

        return this.saveAccessToken(token);
    };

    public getUserId = (): string => {
        if (isEmpty(localStorage.getItem(SystemUtils.USER_ID_NAMESPACE))) {
            localStorage.setItem(SystemUtils.USER_ID_NAMESPACE, uuid4());
        }

        return localStorage.getItem(SystemUtils.USER_ID_NAMESPACE);
    };
}

export const AUTHORIZATION_HEADER_NAMESPACE = SystemUtils.AUTHORIZATION_HEADER_NAMESPACE;
export default new SystemUtils();
