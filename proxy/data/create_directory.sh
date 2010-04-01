#!/bin/bash

# Extracted from torrc - 19.10.2005
#DirServer 18.244.0.188:9031 FFCB 46DB 1339 DA84 674C 70D7 CB58 6434 C437 0441
#DirServer 18.244.0.114:80 719B E45D E224 B607 C537 07D0 E214 3E2D 423E 74CF
#DirServer 86.59.5.130:80 847B 1F85 0344 D787 6491 A548 92F9 0493 4E4E B85D

OUT=directory.example.data

wget -O $OUT http://18.244.0.188:9031/ || wget -O $OUT http://18.244.0.114:80/ || wget -O $OUT http://86.59.5.130:80

#wget -O dir.moria http://18.244.0.188:9031/

#head -n 11 dir.moria > $OUT
#cat router.desc | sed 's/137.226.12.212/127.0.0.1/' >>$OUT
#cat router.desc  >>$OUT
#tail +12 dir.moria >>$OUT

