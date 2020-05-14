Pushing docs to github
*** This should be done by github action after push to master, here we describe manual process just in case ***
- install gitbook: npm install gitbook-cli -g
- edit files in this repo
- test changes running gitbook serve (sometimes gitbook install is also needed...)
- build using gitbook build
- push contents of _book folder to gh_pages branch