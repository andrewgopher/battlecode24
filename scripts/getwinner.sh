#!/bin/bash
./gradlew run -Pmaps=$1 -PteamA=$2 -PteamB=$3 | grep -o -e "(B) wins" -e "(A) wins"
