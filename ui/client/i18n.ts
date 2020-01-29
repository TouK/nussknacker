/* eslint-disable i18next/no-literal-string */
import i18n from "i18next"
import LanguageDetector from "i18next-browser-languagedetector"
import Backend from "i18next-xhr-backend"
import {initReactI18next} from "react-i18next"
import moment from "moment"

i18n
    .use(Backend)
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
      ns: "main",
      defaultNS: "main",
      fallbackLng: "en",
      backend: {
        loadPath: "/static/assets/locales/{{lng}}/{{ns}}.json",
      },
      whitelist: ["en", "pl"],
      interpolation: {
        format: function (value, format: string) {
          if (value instanceof Date || value instanceof moment) return moment(value).format(format)
          return value
        },
      },
    })
