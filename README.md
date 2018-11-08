## Welcome to gcp_bot

### Prequisites

[Node.js](https://nodejs.org/en/) needs to be installed to run the application.

### running in development mode

```
export GCP_AUTH_KEY="some secret value for the totp"
export GOOGLE_API_KEY="your google API token"
cp <gcp cred file> ./creds.json
cp <slack token data file> ./slack_token.dat
```

run the following command in the terminal to install NPM modules and start Figwheel:

```
lein build
```

run `node` in another terminal:

```
npm start
# or
GCP_AUTH_KEY="some secret value for the totp" npm start
```

#### configuring the REPL

Once Figwheel and node are running, you can connect to the remote REPL at `localhost:7000`.

Type `(cljs)` in the REPL to connect to Figwheel ClojureScript REPL.


### building the release version

```
lein package
```

Run the release version:

```
npm start
```

### Running using Docker

The template comes with a `Dockerfile` for running the application using Docker

Once you've run `lein package`, you can build and run a Docker container as follows:

```
docker build -t gcp_bot:latest .
docker run -p 3000:3000 gcp_bot:latest
```

### Setup

make sure you have:
- creds.json -> put your google creds here
- slack_token.dat -> put your slack token in here
- scripts/start_up.sh -> script that will get executed at the start of your VM to install apps

#### Deploying

use the `release.sh` script to release to your cf account.

### Issuing commands

To provision VMs:
`provision <number> <name with no spaces> <TOPT token>`

You can validate your token via the auth command:
`auth <TOPT token>`


### TODO:


[x] show ip,
[x] create more than one vm
[ ] query all vms group by *-num and count nums
[x] generate ssh certs on the fly and remove current one
[X] persist info in memory,
[ ] allow deletion,
[ ] ask for otp code, 
[ ] allow slack integration
    [ ] create cmd with otp and name and qty
    [ ] delete group by name
    [ ] show ips of given group
    [ ] show cert of given group
[x] put the script in a separte file and read it in
[x] connect to slack
[x] connect to Google Sheets
[ ] pass channel into handler for async/sync niceness
[ ] post into the Google Sheet
[ ] connect Slack command to google sheet

# Requirements

gcp credentials json file nam creds.json

