#/usr/bin/bash
if [[ $(command -v module) != "" ]]; then
	module load bioinfo/R/3.5.2 
	module load bioinfo/pplacer/1.1.alpha19 
	module load bioinfo/Krona/2.7
fi

# test du refpgk avec rppr (programme que tu obtiens avec pplacer). le soft te dit c'est ok
# rppr check -c "$@"

unzip "$@"

# génération du summary et de l'input pour krona sous R
R --slave --vanilla --file=$(dirname `which $0`)/refpkg_summary.r --args ${@/.zip/}

# conversion du krona.txt en html
ImportText.pl krona.txt