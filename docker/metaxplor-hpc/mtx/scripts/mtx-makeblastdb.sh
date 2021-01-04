#!/bin/bash
# create a nucl and a prot db from a fasta file

# load module ncbi-blast (makeblastdb), EMBOSS (transeq), and diamond
if [[ $(command -v module) != "" ]]; then
	module load bioinfo/ncbi-blast/2.6.0
	module load bioinfo/EMBOSS/6.6.0
	module load bioinfo/diamond/2.0.4
fi

# only locations contained in this base folder are allowed to be fed by this script
export basepath=/gs7k1/projects/metaXplor

mkdir -p $basepath/$2

# create a blast nucl db
nohup makeblastdb -in $1.fna -dbtype nucl -parse_seqids -out $basepath/$2/$1_nucl & > nucl.log

# translate nucl fasta into prot fasta, 3 frame 2 sens
# .fna -> nucl fasta 
# .faa -> prot fasta
# get the 6 prot frames. Use pearson format to keep the exact sequence name even if it contains ':' or '|' 
transeq -frame 6 -table 0 -sequence $1.fna -outseq $1.faa -sformat pearson

# create a diamond prot db
nohup diamond makedb --in $1.faa -d $basepath/$2/$1 & > diamond.log

# create a blast prot db
makeblastdb -in $1.faa -dbtype prot -parse_seqids -out $basepath/$2/$1_prot