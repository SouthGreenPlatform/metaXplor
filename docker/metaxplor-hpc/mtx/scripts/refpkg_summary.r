
library(BoSSA)

args <- commandArgs(TRUE)

refpkg_path <- as.character(args[1])

sink(file=gsub("$","/../summary.txt",refpkg_path))
refpkg(refpkg_path,out_krona=gsub("$","/../krona.txt",refpkg_path))
sink()

refpkg(refpkg_path,type="krona",out_krona=gsub("$","/../krona.txt",refpkg_path))
