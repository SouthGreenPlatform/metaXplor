#!/bin/bash
if [[ $(command -v module) != "" ]]; then
	module load "compiler/gcc/4.9.2"
	module load "bioinfo/ncbi-blast/2.6.0"
fi

# only banks in locations contained in this base folder are allowed to be blasted by this script
export basepath=/gs7k1/projects/metaXplor

argArray=("$@")

for i in "${!argArray[@]}"; do
	if [[ $prev = "-db" ]]; then
		argArray[$i]=$basepath/${argArray[$i]}
  	fi
	prev=${argArray[$i]}
done

# for i in "${!argArray[@]}"; do
# 	echo ${argArray[$i]}
# done

modifiedArrayAsString=`echo $(echo ${argArray[@]}) | tr ' ' ' '`

# echo $modifiedArrayAsString

tblastn $modifiedArrayAsString