/* eslint-disable i18next/no-literal-string */
import i18next, { Module } from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import intervalPlural from "i18next-intervalplural-postprocessor";
import moment from "moment";
import { initReactI18next } from "react-i18next";

interface ImportBackend extends Module {
    type: "backend";

    read(language: string, namespace: string, callback: (errorValue: unknown, translations: unknown) => void): void;
}

const importBackend: ImportBackend = {
    type: "backend",
    read: async (language, namespace, callback) => {
        try {
            const url = await import(`../translations/${language}/${namespace}.json`);
            const response = await fetch(url.default);
            const json = await response.json();
            callback(null, json);
        } catch (error) {
            callback(error, null);
        }
    },
};

const i18n = i18next.createInstance().use(intervalPlural).use(importBackend).use(LanguageDetector).use(initReactI18next);

i18n.init({
    ns: "main",
    defaultNS: "main",
    fallbackLng: "en",
    supportedLngs: ["en", "pl"],
    interpolation: {
        escapeValue: false,
        format: function (value, format: string) {
            if (value instanceof Date || value instanceof moment) return moment(value).format(format);
            return value;
        },
    },
});

export default i18n;
