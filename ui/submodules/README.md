## req

-   node >=14
-   npm >=7
-   verify/update `.env` (or https://stackoverflow.com/questions/43069087/pass-shell-environment-variable-as-argument-in-npm-script)

## dev

1. `npm install` or `npm ci`
2. `npm start` or `NU_FE_CORE_URL=http://... npm start` 
3. read logs

Note: `NU_FE_CORE_URL` should point to:
- NU backend (e.g. `http://localhost:8080`) if you want to use definitions from production build in NU Backend 
- `http://localhost:3000` if you want to use definitions from development mode


## prod

1. `npm ci`
2. `cp -r client/.federated-types/nussknackerUi submodules/types/@remote` and `CI=true npm run build` or `NU_FE_CORE_URL=http://... npm run build`
3. wait for `dist(s)`
