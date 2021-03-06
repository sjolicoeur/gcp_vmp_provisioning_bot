#!/bin/bash

set -x

lein package
cp Manifest target/release
cp CF-sandbox-sjolicoeur-74991780beb3.json target/release
cp package.json target/release
cp creds.json target/release
cp slack_token.dat target/release
cp -R scripts target/release
pushd target/release
   cf push gcp_bot -b https://github.com/cloudfoundry/nodejs-buildpack -c "node gcp_bot.js"
popd
