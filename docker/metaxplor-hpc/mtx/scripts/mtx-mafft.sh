#!/bin/bash
if [[ $(command -v module) != "" ]]; then
        module load "bioinfo/mafft/7.313"
fi

mafft --adjustdirection --$1 $2 --thread -1 --6merpair $3 >$4 2>stdout.txt # we need to force output to stdout because it writes to stderr by default
sed -i 's/>_R_/>/g' $4