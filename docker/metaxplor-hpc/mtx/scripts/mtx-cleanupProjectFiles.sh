#!/bin/bash

# only locations contained in this base folder are allowed to be cleand up by this script
export basepath=/gs7k1/projects/metaXplor

if [[ $1 = *".."* ]]; then
  echo ".. is not allowed in first argument!"
  exit 1
fi

if [ -d "$basepath/$1" ]; then
        rm -v $basepath/$1/$2_nucl.*
        rm -v $basepath/$1/$2_prot.*
        rm -v $basepath/$1/$2.dmnd
fi