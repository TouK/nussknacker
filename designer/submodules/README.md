## req

-   node >= 16.15.1
-   npm >= 8.11.0
-   verify/update `.env` (or https://stackoverflow.com/questions/43069087/pass-shell-environment-variable-as-argument-in-npm-script)

## dev

1. `npm install` or `npm ci`
2. `npm start` or `NU_FE_CORE_URL=http://... npm start` 
3. read logs

Note: `NU_FE_CORE_URL` should point to NU core frontend application (when running in development mode this is `http://localhost:3000`). By default, it is `/static` which is meant for production build.

## prod

1. `npm ci`
2. `cp -r client/.federated-types/nussknackerUi submodules/types/@remote` and `CI=true npm run build` or `NU_FE_CORE_URL=http://... npm run build`
3. wait for `dist(s)`
