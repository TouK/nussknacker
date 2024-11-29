import { ComponentType, ForwardRefExoticComponent, PropsWithChildren, PropsWithoutRef, RefAttributes } from "react";
import { Module } from "@touk/federated-component";

interface RedirectState {
    targetPath: string;
    action: string;
}

interface Props {
    /**
     * override default redirect after login
     * @param state
     */
    onRedirect?: <S extends RedirectState = RedirectState>(state?: S) => void;
    onInit?: <U>(auth: AuthClient<U>) => void;
}

export interface AuthClient<User = any> {
    user: User;
    isLoading: boolean;
    isAuthenticated: boolean;
    /**
     * login with redirects
     * @param options
     */
    login: (options?: any) => void;
    /**
     * login without redirects (popup)
     * @param options
     */
    loginWithPopup: (options?: any) => Promise<void>;
    logout: (options?: any) => void;
    getToken: () => Promise<string>;
}

/**
 * Types for external (module federation) auth module based on NkCloud Auth0 module
 */
export interface ExternalAuthModule extends Module {
    /**
     * provides auth context for hooks
     */
    default: ForwardRefExoticComponent<PropsWithoutRef<PropsWithChildren<Props>> & RefAttributes<unknown>>;
    /**
     * returns auth client from context
     */
    useAuth: () => AuthClient;
    /**
     * tries to login on init and returns auth client from context
     */
    useLogin: () => AuthClient;
}
