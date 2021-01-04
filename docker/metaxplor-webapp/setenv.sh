#!/bin/bash
LC_ALL=
export CATALINA_OPTS="$CATALINA_OPTS -Xms1024m -Xmx6144m"
sed -i "s|cluster.domain.or.ip:80|${HPC_IP}:${HPC_PORT}|g" /usr/local/tomcat/webapp-config/OpalClient.properties
sed -i "s|adminEmail=.*|adminEmail=${ADMINISTRATOR_EMAIL}|g" /usr/local/tomcat/webapp-config/config.properties
sed -i "s|NCBI_api_key=.*|NCBI_api_key=${NCBI_API_KEY}|g" /usr/local/tomcat/webapp-config/config.properties
sed -i "s|onlineOutputTool_1=.*|onlineOutputTool_1=${ONLINE_OUTPUT_TOOL_1//&/\\&}|g" /usr/local/tomcat/webapp-config/config.properties
sed -i "s|onlineOutputTool_2=.*|onlineOutputTool_2=${ONLINE_OUTPUT_TOOL_2//&/\\&}|g" /usr/local/tomcat/webapp-config/config.properties
sed -i "s|onlineOutputTool_3=.*|onlineOutputTool_3=${ONLINE_OUTPUT_TOOL_3//&/\\&}|g" /usr/local/tomcat/webapp-config/config.properties
sed -i "s|onlineOutputTool_4=.*|onlineOutputTool_4=${ONLINE_OUTPUT_TOOL_4//&/\\&}|g" /usr/local/tomcat/webapp-config/config.properties
sed -i "s|onlineOutputTool_5=.*|onlineOutputTool_5=${ONLINE_OUTPUT_TOOL_5//&/\\&}|g" /usr/local/tomcat/webapp-config/config.properties