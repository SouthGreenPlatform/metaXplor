#!/bin/bash

# This script takes a BLAST format 7 output -i.e., "Hit Table (text)" export format in NCBI interface- and turns it into a valid metaXplor assignment file
# WARNING: this version sets the qseqid's best-hit assignment to be the first match found in the BLAST output. If this does not match what you expect, feel free to amend this script or modify outputs manually
# Usage: blastToMtxh.sh <blast_output_file.txt>

db=$(grep -m 1 "^# Database" $1 | awk '{print $3;}')
if [ $db == "nr" ]; then
	prefix="p:"
elif [ $db == "nt" ]; then
	prefix="n:"
fi
if [ -z "$prefix" ]; then
	echo "Unable to determine accession type prefix"
	exit 1
fi

assignMethod=$(head -1 $1 | awk '{print $2;}')
grep -m 1 "^# Fields" $1 | cut -d\  -f3- | sed 's/, /\t/g;s/query acc.ver/qseqid/g;s/subject acc.ver/sseqid/g;s/$/\tassignment_method\tbest_hit/g' > $1_assignments.tsv
grep . $1 | grep -v '^# .*' | sed 's/$/\t'"${assignMethod}"'/g' | awk -v pfx=$prefix '{ {for(i=1; i<=NF; i++) printf "%s\t", (i == 2 ? pfx : "")($i);} if (!seen[$1]++) printf "Y"; printf "\n" }' >> $1_assignments.tsv