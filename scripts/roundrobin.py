#!/usr/bin/python3
import sys, subprocess

maps="""AceOfSpades
Alien
Ambush
Battlecode24
BigDucksBigPond
CH3353C4K3F4CT0RY
Canals
DefaultHuge
DefaultLarge
DefaultMedium
DefaultSmall
Duck
Fountain
Hockey
HungerGames
MazeRunner
Rivers
Snake
Soccer
SteamboatMickey
Yinyang""".split("\n")

def getResult(a,b):
    aWins = 0
    bWins = 0
    for map in maps:
        winner=str(subprocess.check_output(["scripts/getwinner.sh", map, a, b]))
        if "A" in winner:
            aWins+=1
        else:
            bWins+=1
    return (aWins,bWins)

players=[]
for i in range(1,len(sys.argv)):
    players.append(sys.argv[i])

numPlayers=len(players)

table=[]

for i in range(numPlayers):
    table.append([])
    for j in range(0,i):
            table[i].append(getResult(players[i],players[j]))
    print(players[i].ljust(20, " "), " ".join([str(x).ljust(20," ") for x in table[i]]))
print(maps)