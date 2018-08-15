#!/bin/bash

echo "performing apt update"
apt-get update
echo "performing install of coreutils and git"
apt-get --assume-yes install git  coreutils
echo "performing install of ag stress tmux vim nano ed htop"
apt-get --assume-yes install  stress  silversearcher-ag
apt-get --assume-yes install tmux htop vim nano ed

