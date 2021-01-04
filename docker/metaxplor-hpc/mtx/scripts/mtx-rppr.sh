#!/bin/bash
if [[ $(command -v module) != "" ]]; then
	module load "bioinfo/pplacer/1.1.alpha19"
fi

rppr "$@"