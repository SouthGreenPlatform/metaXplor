#!/bin/bash

# only locations contained in this base folder are allowed to be cleand up by this script
export basepath=/gs7k1/projects/metaXplor

if [[ $1 = *".."* ]]; then
  echo ".. is not allowed in first argument!"
  exit 1
fi

if [ -d "$basepath/$1" ]; then
	rm -v $basepath/$1/*_nucl.*
	rm -v $basepath/$1/*_prot.*
    rm -v $basepath/$1/*.dmnd
fi

if [ -d "$basepath/$2" ]; then
	ls $basepath/$2
fi