# metaXplor-hpc container
#-------------------------------
# Apache Tomcat | 8.5.41
# r-base | 3.6.3-1~stretchcran.0
# ncbi-blast+ | 2.6.0-1
# emboss | 6.6.0+dfsg-6
# mafft | 7.313
# pplacer | 1.1.alpha19
# SGE | 8.1.9
# opal2 | 2.5
# Krona | 2.7
# sqlite3 | 3.16.2-5+deb9u1
# diamond | 2.0.4


FROM tomcat:8.5.41-jre8-slim

ARG WEBAPP_IP
ARG WEBAPP_PORT
ARG HPC_IP
ARG HPC_PORT
ARG DB_IP
ARG DB_PORT

ARG HOME=/root
ARG DEBIAN_FRONTEND=noninteractive
ARG SGE_ROOT=/opt/sge
ARG SGE_CELL=default

EXPOSE 6444
EXPOSE 6445
EXPOSE 6446
EXPOSE 8080


RUN rm -rf /usr/local/tomcat/webapps/*

# opal2 webapp
COPY opal2 /usr/local/tomcat/webapps/opal2

COPY mtx/configs /usr/local/bioinfo/opal/configs
COPY mtx/scripts /usr/local/bioinfo/opal/scripts
COPY replace_var_opal_prop.sh /usr/local/tomcat/bin/replace_var_opal_prop.sh
COPY setenv.sh /usr/local/tomcat/bin/setenv.sh

ADD http://archive.ubuntu.com/ubuntu/pool/main/o/openssl1.0/libssl1.0.0_1.0.2n-1ubuntu5.13_amd64.deb /root/
ADD https://master.dl.sourceforge.net/project/gridengine/SGE/releases/8.1.9/sge-common_8.1.9_all.deb?viasf=1 /root/
ADD https://master.dl.sourceforge.net/project/gridengine/SGE/releases/8.1.9/sge-doc_8.1.9_all.deb?viasf=1 /root/
ADD https://master.dl.sourceforge.net/project/gridengine/SGE/releases/8.1.9/sge_8.1.9_amd64.deb?viasf=1 /root/

ADD docker-sge/sge_auto_install.conf /root/sge_auto_install.conf
ADD docker-sge/docker_sge_init.sh /etc/my_init.d/01_docker_sge_init.sh
ADD docker-sge/sge_exec_host.conf /root/sge_hostgrp.conf
ADD docker-sge/sge_exec_host.conf /root/sge_exec_host.conf
ADD docker-sge/sge_queue.conf /root/sge_queue.conf

ADD runit/opal-tomcat /etc/sv/opal-tomcat
ADD runit/sge /etc/sv/sge


RUN ln -fs /bin/bash /bin/sh

RUN mkdir webapps/opal-jobs
RUN export JAVA_OPTS="-Djava.awt.headless=true"

RUN sed -i -e 's/deb.debian.org/archive.debian.org/g' \
           -e 's|security.debian.org|archive.debian.org/|g' \
           -e '/stretch-updates/d' /etc/apt/sources.list
           
RUN apt-get update -y
RUN apt-get install -y gpg
RUN mkdir ~/.gnupg && echo "disable-ipv6" >> ~/.gnupg/dirmngr.conf
RUN gpg --refresh-keys
RUN apt-key adv --homedir ~/.gnupg --keyserver keyserver.ubuntu.com --recv-key 'E19F5F87128899B192B1A2C2AD5F960A256A04AF'


#RUN sed -i s/deb.debian.org/archive.debian.org/g /etc/apt/sources.list
#RUN sed -i 's|security.debian.org|archive.debian.org|g' /etc/apt/sources.list 
#RUN sed -i '/stretch-updates/d' /etc/apt/sources.list           
RUN apt-get update -y

# add Debian jessie repo for libssl1.0.0 (for SGE)
#RUN echo "deb http://security.debian.org/debian-security jessie/updates main" >> /etc/apt/sources.list
RUN echo "deb http://cloud.r-project.org/bin/linux/debian stretch-cran35/" >> /etc/apt/sources.list
RUN apt-get update -y

# install required software
RUN apt-get install --no-install-recommends -y g++
RUN apt-get install --no-install-recommends -y gfortran
RUN apt-get install --no-install-recommends -y libblas-dev
RUN apt-get install --no-install-recommends -y liblapack-dev
RUN apt-get install --no-install-recommends -y ncbi-blast+=2.6.0-1
RUN apt-get install --no-install-recommends -y sudo
RUN apt-get install --no-install-recommends -y bsd-mailx
RUN apt-get install --no-install-recommends -y tcsh
RUN apt-get install --no-install-recommends -y db5.3-util
RUN apt-get install --no-install-recommends -y libhwloc5
RUN apt-get install --no-install-recommends -y libmunge2
RUN apt-get install --no-install-recommends -y libxm4
RUN apt-get install --no-install-recommends -y libjemalloc1
RUN apt-get install --no-install-recommends -y xterm
RUN apt-get install --no-install-recommends -y procps
RUN apt-get install --no-install-recommends -y libdrmaa1.0-java
RUN apt-get install --no-install-recommends -y runit
RUN apt-get install --no-install-recommends -y git
RUN apt-get install --no-install-recommends -y make
RUN apt-get install --no-install-recommends -y tcl
RUN apt-get install --no-install-recommends -y tcl-dev
RUN apt-get install --no-install-recommends -y less
RUN apt-get install --no-install-recommends -y dh-autoreconf
RUN apt-get install --no-install-recommends -y gcc
RUN apt-get install --no-install-recommends -y emboss=6.6.0+dfsg-6
RUN apt-get install --no-install-recommends -y --allow-unauthenticated r-base=3.6.3-1~stretchcran.0
RUN apt-get install --no-install-recommends -y sqlite3=3.16.2-5+deb9u1
RUN apt-get install --no-install-recommends -y wget
RUN apt-get install --no-install-recommends -y unzip
RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

RUN cd /usr/local/bioinfo

RUN mkdir diamond && wget http://github.com/bbuchfink/diamond/releases/download/v2.0.4/diamond-linux64.tar.gz && tar xzf diamond-linux64.tar.gz -C diamond && rm -f diamond-linux64.tar.gz && ln -s /usr/local/bioinfo/diamond/diamond /usr/local/bin/diamond

COPY mafft.zip ./mafft.zip
RUN unzip mafft.zip && rm -f mafft.zip && ln -s /usr/local/bioinfo/mafft/7.313/bin/mafft /usr/local/bin/mafft

COPY Krona.zip ./Krona.zip
RUN unzip Krona.zip && rm -f Krona.zip && ln -s /usr/local/bioinfo/Krona/2.7/scripts/ImportText.pl /usr/local/bin/ImportText.pl

RUN wget https://github.com/matsen/pplacer/releases/download/v1.1.alpha19/pplacer-linux-v1.1.alpha19.zip
RUN unzip pplacer-linux-v1.1.alpha19.zip
RUN mkdir -p pplacer/1.1.alpha19
RUN mv pplacer-Linux-v1.1.alpha19/* pplacer/1.1.alpha19/
RUN rm -rf pplacer-*
RUN ln -s /usr/local/bioinfo/pplacer/1.1.alpha19/pplacer /usr/local/bin/pplacer
RUN ln -s /usr/local/bioinfo/pplacer/1.1.alpha19/guppy /usr/local/bin/guppy
RUN ln -s /usr/local/bioinfo/pplacer/1.1.alpha19/rppr /usr/local/bin/rppr

# turn off password requirement for sudo groups users
RUN cd /usr/local/tomcat && sed -i "s/^\%sudo\tALL=(ALL:ALL)\sALL/%sudo ALL=(ALL) NOPASSWD:ALL/" /etc/sudoers

# Download and install debian packages from Sun Grid Engine
RUN dpkg -i /root/libssl1.0.0_1.0.2n-1ubuntu5.13_amd64.deb
RUN dpkg -i /root/sge-common_8.1.9_all.deb
RUN dpkg -i /root/sge_8.1.9_amd64.deb
RUN dpkg -i /root/sge-doc_8.1.9_all.deb

RUN chmod ug+x /etc/my_init.d/01_docker_sge_init.sh

# setup SGE env
RUN ln -s $SGE_ROOT/$SGE_CELL/common/settings.sh /etc/profile.d/sge_settings.sh

# install SGE
RUN useradd -r -m -U -G sudo -d /home/tomcat -s /bin/bash -c "Docker tomcat user" tomcat

RUN sudo echo $(grep $(hostname) /etc/hosts | sudo cut -f1) metaxplor-hpc >> /etc/hosts
RUN chmod -R 777 /tmp
RUN cd $SGE_ROOT && ./inst_sge -m -x -s -auto $HOME/sge_auto_install.conf \
&& sleep 10 \
&& /etc/init.d/sgemaster.docker-sge restart \
&& /etc/init.d/sgeexecd.docker-sge restart \

&& sed -i "s/HOSTNAME/`hostname`/" $HOME/sge_exec_host.conf \
&& sed -i "s/HOSTNAME/`hostname`/" $HOME/sge_hostgrp.conf \
&& sudo echo export SGE_ROOT=$SGE_ROOT >> /root/.bashrc \
&& sudo echo export PATH=/opt/sge/bin/lx-amd64/:$PATH >> /root/.bashrc \
&& /opt/sge/bin/lx-amd64/qconf -au tomcat arusers \
&& /opt/sge/bin/lx-amd64/qconf -Me $HOME/sge_exec_host.conf \
&& /opt/sge/bin/lx-amd64/qconf -Aq $HOME/sge_queue.conf \
&& ln -s /opt/sge/lib/lx-amd64/libdrmaa.so /usr/local/tomcat/native-jni-lib/libdrmaa.so \
&& ln -s /opt/sge/lib/lx-amd64/libdrmaa.so.1.0 /usr/local/tomcat/native-jni-lib/libdrmaa.so.1.0 \
&& echo "-w n" >> /opt/sge/default/common/sge_request \

# runit
RUN ln -s /etc/sv/opal-tomcat /etc/service/opal-tomcat
RUN ln -s /etc/sv/sge /etc/service/sge
RUN chmod ug+x /etc/sv/opal-tomcat/run
RUN chmod ug+x /etc/sv/sge/run
RUN chmod -R 777 /usr/local/tomcat

RUN mkdir -p /gs7k1/projects/metaXplor
RUN chmod -R 777 /gs7k1

# BoSSA (R-package) install
RUN R -e "install.packages(\"BoSSA\")"
RUN rm -rf R

# cleanup
RUN apt-get purge -y g++ gfortran libblas-dev liblapack-dev make less gcc wget
RUN apt-get autoremove -y
RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
RUN rm /root/*.deb


ENTRYPOINT ["runsvdir",  "-P", "/etc/service"]