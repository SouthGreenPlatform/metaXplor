#!/bin/bash

# updates tomcat.url in opal.propertoes
sed -i --follow-symlinks "s|tomcat.url=.*|tomcat.url=http:\/\/${HPC_IP}:${HPC_PORT}|g" /usr/local/tomcat/webapps/opal2/WEB-INF/classes/opal.properties