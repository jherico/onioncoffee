#!/bin/bash

while true ; do
	DATE=`date +'%y%m%d.%H%M'`
	OUT="directory.loaded-$DATE"
	echo "loading directory to $OUT"
	wget -O $OUT http://18.244.0.188:9031/ || wget -O $OUT http://18.244.0.114:80/ || wget -O $OUT http://86.59.5.130:80
	# sleep 24 hours
	sleep $(( 24 * 3600 ))
done

