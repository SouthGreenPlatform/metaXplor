#!/bin/bash
if [[ $(command -v module) != "" ]]; then
	module load "system/sqlite3/3.24.0"
	module load "bioinfo/pplacer/1.1.alpha19"
fi

rppr prep_db --sqlite ${2/.jplace/.db} -c $1
guppy classify --multiclass-min 0 --cutoff 0.5 -c $1 --sqlite ${2/.jplace/.db} $2
sqlite3 -separator $'\t' ${2/.jplace/.db} 'select * from (select name,tax_id,likelihood from multiclass where want_rank=(select rank from ranks where rank_order=(select MAX(rank_order) from ranks)) order by name,likelihood desc) group by name;' > classification.tsv

guppy fat -c $1 $2