/* eslint-disable i18next/no-literal-string */
import i18next from "i18next"
import LanguageDetector from "i18next-browser-languagedetector"
import intervalPlural from "i18next-intervalplural-postprocessor"
import Backend from "i18next-xhr-backend"
import moment from "moment"
import {initReactI18next} from "react-i18next"
import {BACKEND_STATIC_URL} from "./config"

const i18n = i18next
  .use(intervalPlural)
  .use(Backend)
  .use(LanguageDetector)
  .use(initReactI18next)

i18n.init({
  ns: "main",
  defaultNS: "main",
  fallbackLng: "en",
  backend: {
    loadPath: `${BACKEND_STATIC_URL}/assets/locales/{{lng}}/{{ns}}.json`,
  },
  whitelist: ["en", "pl"],
  interpolation: {
    escapeValue: false,
    format: function(value, format: string) {
      if (value instanceof Date || value instanceof moment) return moment(value).format(format)
      return value
    },
  },
})

export default i18n
