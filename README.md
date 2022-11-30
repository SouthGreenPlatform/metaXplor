
metaXplor
=========    

  ![metaXplor logo](src/main/webapp/img/logo_metaxplor.png "Logo")
  
  Store, share, explore, manipulate metagenomic data
  
**metaXplor** is a scalable, distributable, fully web-interfaced application for managing, sharing and exploring metagenomic data. Being based on a flexible NoSQL data model, it has very few constraints regarding dataset contents, and thus proves useful for handling outputs from both shot-gun and metabarcoding techniques. By supporting incremental data feeding and providing means to combine filters on all imported fields, it allows for exhaustive content browsing, as well as rapid narrowing to find very specific records. The application also features various interactive data visualization tools, ways to query contents by BLASTing external sequences, and an integrated pipeline to enrich assignments with phylogenetic placements.

## Live instance

Try metaXplor online with public datasets at: https://metaxplor.cirad.fr/

## Deployment

**metaXplor** is made available as a set of Docker container images. Two of them are specific (https://hub.docker.com/r/guilhemsempere/metaxplor-webapp and https://hub.docker.com/r/guilhemsempere/metaxplor-hpc), one is the official MongoDB image (https://hub.docker.com/_/mongo).
Deploying **metaXplor** on a single host without compiling it may be achieved by simply downloading docker-compose.yml, setting MongoDB password for security (NCBI_API_KEY and ADMINISTRATOR_EMAIL variables are also recommended to be specified for an optimal configuration), and launching via the following command:
<code>export docker0=$(ip a list docker0 | grep "inet " | sed 's/^ *//g' | awk -F'[ |/]' '{print $2}') && docker-compose up -d && sleep 2 && xdg-open http://$docker0:8090/metaXplor &> /dev/null</code>
The application should open in the local default browser. If not, it should be accessible at http://host.ip:8090/metaXplor where host.ip is the address corresponding to the docker0 network. The default administrator credentials are metadmin / nimda (please change password upon first login by clicking on the "Manage data" -> "Administer existing data and user permissions" link from the menu and then the "Manage users and permissions" button).

Note that files in the docker/metaxplor-hpc/docker-sge directory originate from https://github.com/gawbul/docker-sge and may be amended for fine-tuning SGE. By default the HPC container is configured to allow 3 concurrent jobs, and each may last up to 2 hours (settings slots=3 and h_rt=02:00:00 in docker/metaxplor-hpc/docker-sge/sge_queue.conf).

With some basic configuration, it is also possible to deploy each container on a separate host (by copying this file to each host and disabling irrelevant services / volumes) and / or to suppress the metaxplor-db service if you want to use an existing MongoDB instance instead.

## Developer instructions

This repository contains the main project's source code. It uses Maven for build and dependency management, and requires the following dependencies:

##### https://github.com/GuilhemSempere/metaXplorDB
##### https://github.com/GuilhemSempere/role_manager
##### https://github.com/GuilhemSempere/OpalClient

Building the web application and generating your own container images takes the following steps:
- preferably, amending pom.xml in any project where you changed some code, to set your own version number (will avoid overwriting JARs), and forward those changes to the main project's pom.xml's dependency section
- launching "mvn install" from within the root directory of each above-mentioned dependency project
- launching "mvn install -P prod,docker" from within the metaXplor project root directory

When both images (hpc & webapp) are built you may refer to the Deployment section above.

NB: In the webapp container, Tomcat is configured by default to be able to use up to 6Gb of RAM, which we found to be a good compromise. This setting may be altered by amending the docker/metaxplor-webapp/setenv.sh file prior to launching the final "mvn install" command.
