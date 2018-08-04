FROM mhart/alpine-node:latest

MAINTAINER Your Name <you@example.com>

# Create app directory
RUN mkdir -p /gcp_bot
WORKDIR /gcp_bot

# Install app dependencies
COPY package.json /gcp_bot
RUN npm install pm2 -g
RUN npm install

# Bundle app source
COPY target/release/gcp_bot.js /gcp_bot/gcp_bot.js
COPY public /gcp_bot/public

ENV HOST 0.0.0.0

EXPOSE 3000
CMD [ "pm2-docker", "/gcp_bot/gcp_bot.js" ]
